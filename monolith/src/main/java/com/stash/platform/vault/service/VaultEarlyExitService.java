package com.stash.platform.vault.service;

import com.stash.platform.vault.api.dto.EarlyExitRequest;
import com.stash.platform.vault.api.dto.EarlyExitResponse;
import com.stash.platform.vault.client.PaymentsBalanceClient;
import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.EarlyExitRequestRepository;
import com.stash.platform.vault.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Handles locked vault early-exit requests.
 *
 * <p><strong>Flow:</strong>
 * <ol>
 *   <li>Validate vault: exists, owned, LOCKED type, ACTIVE status.</li>
 *   <li>Check for an existing PENDING early-exit request (application-level).</li>
 *   <li>Fetch current vault balance from the Payments Service.</li>
 *   <li>Calculate penalty (5% FLOOR) and release amount.</li>
 *   <li>Insert early-exit request row.</li>
 *   <li>Update vault status to EARLY_EXIT_PENDING.</li>
 *   <li>Commit — steps 5 and 6 are inside the same {@code @Transactional}.</li>
 * </ol>
 *
 * <p><strong>Balance snapshot immutability:</strong> the {@code balance_at_request},
 * {@code penalty_amount}, and {@code release_amount} are stored on the request row
 * at creation time. Any deposits made to the vault during the 72-hour cool-off
 * window do NOT affect these values — the release worker pays out exactly
 * {@code release_amount} from the snapshot, regardless of subsequent balance changes.
 *
 * <p><strong>Concurrency:</strong> the DB partial unique index
 * {@code locked_vault_early_exit_requests_one_pending_per_vault} enforces
 * one PENDING request per vault at the DB level. A concurrent second request
 * that passes the application-level check will fail the INSERT with a unique
 * constraint violation, caught and translated to 409.
 */
@Service
public class VaultEarlyExitService {

    private static final Logger log = LoggerFactory.getLogger(VaultEarlyExitService.class);

    private static final Duration COOL_OFF_WINDOW = Duration.ofHours(72);
    private static final Set<String> VALID_REASONS = Set.of(
            "SCHOOL_FEES_EMERGENCY", "MEDICAL", "FAMILY", "OTHER");

    private final VaultRepository             vaultRepo;
    private final EarlyExitRequestRepository  requestRepo;
    private final PaymentsBalanceClient       balanceClient;
    private final EarlyExitPenaltyCalculator  penaltyCalculator;
    private final Clock                       clock;

    public VaultEarlyExitService(VaultRepository vaultRepo,
                                  EarlyExitRequestRepository requestRepo,
                                  PaymentsBalanceClient balanceClient,
                                  EarlyExitPenaltyCalculator penaltyCalculator,
                                  Clock clock) {
        this.vaultRepo         = vaultRepo;
        this.requestRepo       = requestRepo;
        this.balanceClient     = balanceClient;
        this.penaltyCalculator = penaltyCalculator;
        this.clock             = clock;
    }

    /**
     * Creates an early-exit request and transitions the vault to EARLY_EXIT_PENDING.
     *
     * @throws ResponseStatusException 404 vault not found;
     *                                 403 not owned by user;
     *                                 409 not LOCKED type, not ACTIVE status,
     *                                     or duplicate PENDING request;
     *                                 422 invalid reason;
     *                                 502 Payments Service unavailable
     */
    @Transactional
    public EarlyExitResponse requestEarlyExit(UUID vaultId, UUID userId,
                                               EarlyExitRequest request,
                                               String correlationId) {
        // ── Validate reason ───────────────────────────────────────────────
        String reason = request.reason().toUpperCase();
        if (!VALID_REASONS.contains(reason)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invalid reason. Must be one of: " + VALID_REASONS);
        }

        // ── Load and validate vault ───────────────────────────────────────
        VaultEntity vault = vaultRepo.findById(vaultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vault not found: " + vaultId));

        if (!userId.equals(vault.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vault does not belong to the authenticated user.");
        }

        if (!"LOCKED".equals(vault.getVaultType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VAULT_EARLY_EXIT_NOT_APPLICABLE: Early exit is only available " +
                    "for LOCKED vaults. This vault is type " + vault.getVaultType() + ".");
        }

        if (!"ACTIVE".equals(vault.getStatus())) {
            // Covers EARLY_EXIT_PENDING (duplicate), CLOSED, and any other status
            if ("EARLY_EXIT_PENDING".equals(vault.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "VAULT_EARLY_EXIT_ALREADY_PENDING: An early-exit request is " +
                        "already pending for this vault. Cancel it before submitting a new one.");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot request early exit for a vault with status: " + vault.getStatus());
        }

        if (vault.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot request early exit for a deleted vault.");
        }

        // ── Application-level duplicate check (belt-and-suspenders) ──────
        // The DB partial unique index is the real enforcement; this gives a
        // cleaner error message before hitting the constraint.
        if (requestRepo.findPendingByVaultId(vaultId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VAULT_EARLY_EXIT_ALREADY_PENDING: An early-exit request is " +
                    "already pending for this vault.");
        }

        // ── Fetch current balance from Payments Service ───────────────────
        long balancePesewas = balanceClient.fetchBalance(vault.getLedgerAccountId(), correlationId)
                .orElseThrow(() -> {
                    log.error("Cannot fetch balance for vault={} ledgerAccount={} " +
                              "during early-exit request. correlation={}",
                            vaultId, vault.getLedgerAccountId(), correlationId);
                    return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Unable to fetch vault balance from Payments Service. " +
                            "Please retry.");
                });

        // ── Calculate penalty and release amount ──────────────────────────
        EarlyExitPenaltyCalculator.PenaltyResult penalty =
                penaltyCalculator.calculate(balancePesewas);

        if (!penalty.isValid()) {
            // Should never happen — indicates a calculation bug
            log.error("Penalty calculation invalid for vault={} balance={}: penalty={} release={}",
                    vaultId, balancePesewas, penalty.penaltyAmount(), penalty.releaseAmount());
            throw new IllegalStateException(
                    "Penalty calculation produced inconsistent result. This is a bug.");
        }

        // ── Create early-exit request row ─────────────────────────────────
        Instant now                = Instant.now(clock);
        Instant scheduledReleaseAt = now.plus(COOL_OFF_WINDOW);

        EarlyExitRequestEntity earlyExitRequest = EarlyExitRequestEntity.create(
                vaultId, userId, reason,
                penalty.balanceAtRequest(),
                penalty.penaltyAmount(),
                penalty.releaseAmount(),
                scheduledReleaseAt,
                now
        );

        EarlyExitRequestEntity savedRequest;
        try {
            savedRequest = requestRepo.save(earlyExitRequest);
        } catch (DataIntegrityViolationException e) {
            // Race condition: two concurrent requests both passed the application-level
            // check; the second one fails the partial unique index.
            log.warn("Race condition on early-exit request for vault={}: unique index fired. " +
                     "Returning 409. correlation={}", vaultId, correlationId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VAULT_EARLY_EXIT_ALREADY_PENDING: An early-exit request was just " +
                    "created concurrently. Please check the vault status and retry if needed.");
        }

        // ── Transition vault to EARLY_EXIT_PENDING ────────────────────────
        // Both the request INSERT and the vault status UPDATE commit together.
        setVaultStatus(vault, "EARLY_EXIT_PENDING", true);
        vaultRepo.save(vault);

        log.info("Early-exit request created: vault={} requestId={} balance={}p " +
                 "penalty={}p release={}p scheduledRelease={} reason={} correlation={}",
                vaultId, savedRequest.getId(), balancePesewas,
                penalty.penaltyAmount(), penalty.releaseAmount(),
                scheduledReleaseAt, reason, correlationId);

        return EarlyExitResponse.from(savedRequest);
    }

    private void setVaultStatus(VaultEntity vault, String status, boolean earlyExitInProgress) {
        try {
            var statusField = VaultEntity.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(vault, status);
            var exitField = VaultEntity.class.getDeclaredField("earlyExitInProgress");
            exitField.setAccessible(true);
            exitField.set(vault, earlyExitInProgress);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update vault status fields", e);
        }
    }
}
