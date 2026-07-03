package com.stash.platform.vault.service;

import com.stash.platform.subscription.service.SubscriptionLimitChecker;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.api.dto.VaultWithdrawalRequest;
import com.stash.platform.vault.api.dto.VaultWithdrawalResponse;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.platform.vault.client.PaymentsWithdrawalClient;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Orchestrates STANDARD vault withdrawals.
 *
 * <p><strong>Hard business rules enforced here:</strong>
 * <ul>
 *   <li>LOCKED vaults may not withdraw via this endpoint unless they have
 *       naturally unlocked (the auto-unlock worker set {@code unlockedAt}
 *       once the date/amount condition was met) — otherwise they must use
 *       the early-exit flow (v0.3-031). This check happens before any call
 *       to the Payments Service and cannot be bypassed.</li>
 *   <li>Vault must be ACTIVE (not EARLY_EXIT_PENDING, not CLOSED).</li>
 *   <li>Vault must belong to the authenticated user.</li>
 * </ul>
 *
 * <p><strong>Error code translation:</strong>
 * The Payments Service returns a generic body with {@code code = PAYMENTS_INSUFFICIENT_BALANCE}.
 * This service translates that to {@code VAULT_INSUFFICIENT_BALANCE} for the mobile
 * client so it can surface the correct message ("Your vault balance is too low" rather
 * than the Payments Service's generic phrasing).
 */
@Service
public class VaultWithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(VaultWithdrawalService.class);

    private final VaultRepository          vaultRepo;
    private final UserRepository           userRepo;
    private final PaymentsWithdrawalClient paymentsClient;
    private final SubscriptionLimitChecker subscriptionLimitChecker;

    public VaultWithdrawalService(VaultRepository vaultRepo,
                                   UserRepository userRepo,
                                   PaymentsWithdrawalClient paymentsClient,
                                   SubscriptionLimitChecker subscriptionLimitChecker) {
        this.vaultRepo      = vaultRepo;
        this.userRepo       = userRepo;
        this.paymentsClient = paymentsClient;
        this.subscriptionLimitChecker = subscriptionLimitChecker;
    }

    /**
     * Initiates a withdrawal from a STANDARD vault.
     *
     * @throws ResponseStatusException 404 if vault not found;
     *                                 403 if vault doesn't belong to user;
     *                                 409 if LOCKED (code = VAULT_WITHDRAWAL_NOT_PERMITTED),
     *                                     or if CLOSED/deleted (code = VAULT_CLOSED);
     *                                 422 if insufficient balance (code = VAULT_INSUFFICIENT_BALANCE);
     *                                 502 if Payments Service is unavailable
     */
    @Transactional(readOnly = true)
    public VaultWithdrawalResponse initiateWithdrawal(UUID vaultId, UUID userId,
                                                       VaultWithdrawalRequest request,
                                                       String correlationId,
                                                       String idempotencyKey) {
        // ── Load and validate vault ───────────────────────────────────────
        VaultEntity vault = vaultRepo.findById(vaultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vault not found: " + vaultId));

        // Ownership check (403 — vault exists, user just doesn't own it)
        if (!userId.equals(vault.getOwnerUserId())) {
            log.warn("Withdrawal ownership mismatch: vault={} requesting_user={} vault_owner={}",
                    vaultId, userId, vault.getOwnerUserId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vault does not belong to the authenticated user.");
        }

        // LOCKED vault check — hard business rule, checked before anything else.
        // A LOCKED vault that has naturally unlocked (unlockedAt set by the
        // auto-unlock worker) is exempt — its lock conditions were met, so
        // ordinary withdrawal is permitted without the early-exit penalty.
        if ("LOCKED".equals(vault.getVaultType()) && vault.getUnlockedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VAULT_WITHDRAWAL_NOT_PERMITTED: LOCKED vaults cannot be withdrawn from. " +
                    "Use the early-exit flow to break the lock.");
        }

        // Status checks
        if (vault.getDeletedAt() != null || "CLOSED".equals(vault.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VAULT_CLOSED: Cannot withdraw from a closed vault.");
        }

        // v0.5-030: checked before the generic status check below so a FROZEN
        // vault gets the specific 422 VAULT_FROZEN code, not a generic 409.
        subscriptionLimitChecker.assertVaultNotFrozen(vault.getStatus());

        if (!"ACTIVE".equals(vault.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot withdraw from a vault with status: " + vault.getStatus() +
                    ". Vault must be ACTIVE.");
        }

        // ── Load user (for recipient name + email) ────────────────────────
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));

        // ── Validate MoMo provider ────────────────────────────────────────
        validateMomoProvider(request.momoProvider());

        // ── Delegate to Payments Service ──────────────────────────────────
        try {
            PaymentsWithdrawalClient.WithdrawalResult result =
                    paymentsClient.initiateWithdrawal(
                            userId,
                            user.getEmail(),
                            user.getDisplayName(),
                            vault.getLedgerAccountId(),
                            request.amount(),
                            request.destinationMomoNumber(),
                            request.momoProvider(),
                            vaultId,
                            correlationId,
                            idempotencyKey
                    );

            log.info("Vault withdrawal initiated: vault={} txnRef={} amount={}p user={} correlation={}",
                    vaultId, result.transactionReference(), request.amount(), userId, correlationId);

            return new VaultWithdrawalResponse(
                    result.transactionReference(),
                    result.paystackTransferCode(),
                    result.status()
            );

        } catch (PaymentsServiceException e) {
            translateAndThrow(e, vaultId, correlationId);
            throw e;   // unreachable — translateAndThrow always throws
        }
    }

    // ── Error translation ─────────────────────────────────────────────────

    /**
     * Translates Payments Service errors to vault-specific error codes.
     *
     * <p>The Payments Service uses generic codes (PAYMENTS_INSUFFICIENT_BALANCE).
     * The mobile app needs vault-specific codes (VAULT_INSUFFICIENT_BALANCE)
     * so it can display the correct contextual message.
     */
    private void translateAndThrow(PaymentsServiceException e, UUID vaultId, String correlationId) {
        int status = e.getHttpStatus();
        String body = e.getMessage();

        if (status == 422 && body != null && body.contains("PAYMENTS_INSUFFICIENT_BALANCE")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VAULT_INSUFFICIENT_BALANCE: The vault balance is insufficient " +
                    "for this withdrawal.");
        }

        if (status >= 400 && status < 500) {
            // Other 4xx: propagate with original status
            throw new ResponseStatusException(HttpStatus.valueOf(status), body);
        }

        // 5xx / timeout: surface as 502
        log.error("Payments Service error during vault withdrawal: vault={} error={} correlation={}",
                vaultId, body, correlationId);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Payment processing temporarily unavailable. Please retry.");
    }

    private void validateMomoProvider(String provider) {
        if (!"mtn".equalsIgnoreCase(provider)
                && !"vodafone".equalsIgnoreCase(provider)
                && !"airteltigo".equalsIgnoreCase(provider)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "momo_provider must be one of: mtn, vodafone, airteltigo.");
        }
    }
}
