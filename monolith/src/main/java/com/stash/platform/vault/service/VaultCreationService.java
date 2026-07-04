package com.stash.platform.vault.service;

import com.stash.platform.subscription.service.SubscriptionLimitChecker;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.api.dto.CreateVaultRequest;
import com.stash.platform.vault.api.dto.VaultResponse;
import com.stash.platform.vault.client.PaymentsServiceClient;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates vaults, enforcing tier limits and provisioning ledger accounts.
 *
 * <p><strong>Ordering (critical):</strong>
 * <ol>
 *   <li>Acquire SELECT FOR UPDATE lock on the user row.</li>
 *   <li>Validate KYC status.</li>
 *   <li>Count existing vaults — reject if free-tier limit reached.</li>
 *   <li>Call Payments Service to provision the VAULT ledger account.</li>
 *   <li>Insert the vault row.</li>
 * </ol>
 *
 * <p>If the Payments call succeeds but the vault INSERT fails, a dangling
 * empty ledger account exists in the Payments Service — acceptable (it has
 * no balance and is cleaned up by a periodic reconciliation job if needed).
 *
 * <p><strong>Concurrency:</strong> the SELECT FOR UPDATE on the user row
 * in step 1 serialises concurrent vault creation from the same user.
 * Two simultaneous requests both attempt the lock; the second blocks until
 * the first's count-check + Payments call + INSERT completes.
 */
@Service
public class VaultCreationService {

    private static final Logger log = LoggerFactory.getLogger(
        VaultCreationService.class
    );

    private final VaultRepository            vaultRepo;
    private final UserRepository             userRepo;
    private final PaymentsServiceClient      paymentsClient;
    private final SubscriptionLimitChecker   subscriptionLimitChecker;
    private final Clock                      clock;

    public VaultCreationService(VaultRepository vaultRepo,
                                 UserRepository userRepo,
                                 PaymentsServiceClient paymentsClient,
                                 SubscriptionLimitChecker subscriptionLimitChecker,
                                 Clock clock) {
        this.vaultRepo      = vaultRepo;
        this.userRepo       = userRepo;
        this.paymentsClient = paymentsClient;
        this.subscriptionLimitChecker = subscriptionLimitChecker;
        this.clock          = clock;
    }

    /**
     * Creates a vault for the authenticated user.
     *
     * @param userId         authenticated user's UUID (from JWT)
     * @param request        validated create request
     * @param correlationId  trace ID
     * @param idempotencyKey from the Idempotency-Key header
     */
    @Transactional
    public VaultResponse createVault(
        UUID userId,
        CreateVaultRequest request,
        String correlationId,
        String idempotencyKey
    ) {
        // ── Step 1: Lock user row ─────────────────────────────────────────
        userRepo.lockUserRow(userId);

        // ── Step 2: Load user and validate KYC ───────────────────────────
        User user = userRepo
            .findByIdForVaultCreation(userId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found: " + userId
                )
            );

        if (user.getKycStatus() != KycStatus.APPROVED) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "KYC must be APPROVED to create a vault. " +
                    "Current status: " +
                    user.getKycStatus()
            );
        }

        // ── Step 3: Validate request ──────────────────────────────────────
        validateRequest(request);

        // ── Step 4: Tier limit check (v0.5-030: centralized in SubscriptionLimitChecker) ──
        enforceTierLimit(userId, user.getSubscriptionTier(), request.vaultType());

        // ── Step 5: Provision ledger account (external HTTP call) ─────────
        // Derive a stable vault ID from the authenticated user and idempotency key
        // so retries reuse the same downstream Payments request body.
        UUID vaultId = deterministicVaultId(userId, idempotencyKey);
        UUID ledgerAccountId;
        try {
            ledgerAccountId = paymentsClient.provisionVaultLedgerAccount(
                userId,
                vaultId,
                correlationId,
                idempotencyKey
            );
        } catch (PaymentsServiceException e) {
            log.error(
                "Vault creation failed: Payments Service unavailable for user={} error={}",
                userId,
                e.getMessage()
            );
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to provision vault ledger account. Please retry. " +
                    "Your account has not been charged."
            );
        }

        // ── Step 6: Insert vault row ──────────────────────────────────────
        Instant now = Instant.now(clock);
        VaultEntity vault;

        if ("STANDARD".equals(request.vaultType())) {
            vault = VaultEntity.createStandard(
                vaultId,
                userId,
                request.name(),
                ledgerAccountId,
                now
            );
        } else {
            String logic = deriveUnlockLogic(request);
            vault = VaultEntity.createLocked(
                vaultId,
                userId,
                request.name(),
                ledgerAccountId,
                request.unlockAt(),
                request.unlockAmount(),
                logic,
                now
            );
        }

        VaultEntity saved = vaultRepo.save(vault);

        log.info(
            "Vault created: id={} type={} user={} ledgerAccount={} correlation={}",
            saved.getId(),
            saved.getVaultType(),
            userId,
            ledgerAccountId,
            correlationId
        );

        return VaultResponse.from(saved);
    }

    // ── Validation ────────────────────────────────────────────────────────

    private void validateRequest(CreateVaultRequest request) {
        if (
            !"STANDARD".equals(request.vaultType()) &&
            !"LOCKED".equals(request.vaultType())
        ) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "vault_type must be STANDARD or LOCKED."
            );
        }

        if ("LOCKED".equals(request.vaultType())) {
            if (request.unlockAt() == null && request.unlockAmount() == null) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "LOCKED vaults require at least one unlock condition " +
                        "(unlock_at or unlock_amount)."
                );
            }
            if (
                request.unlockAt() != null &&
                request.unlockAt().isBefore(Instant.now(clock))
            ) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "unlock_at must be in the future."
                );
            }
        }

        if ("STANDARD".equals(request.vaultType())) {
            if (request.unlockAt() != null || request.unlockAmount() != null) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "STANDARD vaults must not have unlock conditions."
                );
            }
        }

        if (
            request.unlockConditionLogic() != null &&
            !"AND".equals(request.unlockConditionLogic()) &&
            !"OR".equals(request.unlockConditionLogic())
        ) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "unlock_condition_logic must be AND or OR."
            );
        }

        if (
            request.unlockConditionLogic() != null &&
            (request.unlockAt() == null || request.unlockAmount() == null)
        ) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "unlock_condition_logic is only valid when both unlock_at " +
                    "and unlock_amount are provided."
            );
        }
    }

    private void enforceTierLimit(UUID userId, SubscriptionTier tier, String vaultType) {
        if ("STANDARD".equals(vaultType)) {
            long count = vaultRepo.countByOwnerUserIdAndVaultType(userId, "STANDARD");
            subscriptionLimitChecker.assertStandardVaultWithinLimit(tier, count);
        } else {
            long count = vaultRepo.countByOwnerUserIdAndVaultType(userId, "LOCKED");
            subscriptionLimitChecker.assertLockedVaultWithinLimit(tier, count);
        }
    }

    private String deriveUnlockLogic(CreateVaultRequest request) {
        if (request.unlockAt() != null && request.unlockAmount() != null) {
            // Both conditions set: use provided logic or default to AND
            return request.unlockConditionLogic() != null
                ? request.unlockConditionLogic()
                : "AND";
        }
        return null; // Only one condition: logic is trivial, leave null
    }

    private UUID deterministicVaultId(UUID userId, String idempotencyKey) {
        String seed = userId + ":" + idempotencyKey;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
