package com.stash.platform.vault.service;

import com.stash.platform.vault.client.PaymentsBalanceClient;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.platform.vault.client.PaymentsTransferClient;
import com.stash.platform.vault.client.PaymentsWithdrawalClient;
import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.EarlyExitRequestRepository;
import com.stash.platform.vault.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Processes one early-exit release request.
 *
 * <p><strong>Steps per request:</strong>
 * <ol>
 *   <li>Fetch the current vault balance from the Payments Service.</li>
 *   <li>Calculate the actual release amount:
 *       {@code current_balance - snapshotted_penalty_amount}.</li>
 *   <li>If penalty > 0: transfer penalty to FEE_REVENUE via internal transfer.</li>
 *   <li>Initiate MoMo withdrawal of release amount from vault ledger account.</li>
 *   <li>Mark early-exit request COMPLETED; transition vault to CLOSED.</li>
 * </ol>
 *
 * <p><strong>Why recalculate release amount?</strong>
 * The penalty is fixed at request time (5% of balance at that moment) and stored
 * on the request row. The release amount at processing time is
 * {@code current_balance - snapshotted_penalty}, not the snapshotted release amount.
 * If the user deposited GHS 50 during the cool-off window:
 * <ul>
 *   <li>Snapshot: balance = GHS 100, penalty = GHS 5, release = GHS 95</li>
 *   <li>At processing: current balance = GHS 150, penalty = GHS 5 (fixed), release = GHS 145</li>
 * </ul>
 * The extra GHS 50 is returned to the user without additional penalty.
 *
 * <p><strong>Zero release amount:</strong> if the vault balance equals the penalty
 * (or the vault is empty), no withdrawal is initiated. The vault is still closed.
 *
 * <p><strong>Failure handling:</strong> any Payments Service failure increments
 * {@code attempts}. After {@link #MAX_ATTEMPTS} failures, a P0 alert is emitted
 * and the request is left in PENDING state for ops review — it is NOT moved to FAILED
 * (which would close the DB partial unique index gap and allow duplicate requests).
 */
@Service
public class EarlyExitReleaseProcessor {

    private static final Logger log = LoggerFactory.getLogger(EarlyExitReleaseProcessor.class);

    static final int MAX_ATTEMPTS = 5;

    private final VaultRepository            vaultRepo;
    private final EarlyExitRequestRepository requestRepo;
    private final PaymentsBalanceClient      balanceClient;
    private final PaymentsTransferClient     transferClient;
    private final PaymentsWithdrawalClient   withdrawalClient;
    private final EarlyExitReleaseMetrics    metrics;
    private final Clock                      clock;
    private final UUID                       feeRevenueAccountId;

    public EarlyExitReleaseProcessor(
            VaultRepository vaultRepo,
            EarlyExitRequestRepository requestRepo,
            PaymentsBalanceClient balanceClient,
            PaymentsTransferClient transferClient,
            PaymentsWithdrawalClient withdrawalClient,
            EarlyExitReleaseMetrics metrics,
            Clock clock,
            @Value("${stash.ledger.fee-revenue-account-id}") String feeRevenueAccountId) {
        this.vaultRepo          = vaultRepo;
        this.requestRepo        = requestRepo;
        this.balanceClient      = balanceClient;
        this.transferClient     = transferClient;
        this.withdrawalClient   = withdrawalClient;
        this.metrics            = metrics;
        this.clock              = clock;
        this.feeRevenueAccountId = UUID.fromString(feeRevenueAccountId);
    }

    /**
     * Processes one due early-exit request.
     *
     * @param request        the due PENDING early-exit request
     * @param userMomoNumber the user's registered MoMo number
     * @param userMomoProvider the MoMo network provider (mtn, vodafone, airteltigo)
     * @param userFullName   user's full name (for Paystack recipient)
     * @param userEmail      user's email (for Paystack recipient)
     * @param correlationId  trace ID
     */
    @Transactional
    public void process(EarlyExitRequestEntity request,
                        String userMomoNumber,
                        String userMomoProvider,
                        String userFullName,
                        String userEmail,
                        String correlationId) {

        UUID requestId = request.getId();
        UUID vaultId   = request.getVaultId();
        Instant now    = Instant.now(clock);

        log.info("EarlyExitReleaseProcessor: processing request={} vault={} attempt={} correlation={}",
                requestId, vaultId, request.getAttempts() + 1, correlationId);

        // ── Load vault ────────────────────────────────────────────────────
        VaultEntity vault = vaultRepo.findById(vaultId)
                .orElseThrow(() -> new IllegalStateException(
                        "Vault not found for early-exit request: " + vaultId));

        // ── Fetch current vault balance ───────────────────────────────────
        long currentBalance;
        try {
            currentBalance = balanceClient
                    .fetchBalance(vault.getLedgerAccountId(), correlationId)
                    .orElseThrow(() -> new PaymentsServiceException(
                            "Balance unavailable for vault=" + vaultId, 503, null));
        } catch (Exception e) {
            handleFailure(request, now, "Balance fetch failed: " + e.getMessage());
            return;
        }

        // ── Calculate actual release amount ───────────────────────────────
        long penaltyAmount  = request.getPenaltyAmount();   // fixed at request time
        long releaseAmount  = Math.max(0, currentBalance - penaltyAmount);

        log.info("EarlyExitReleaseProcessor: vault={} currentBalance={}p penalty={}p " +
                 "releaseAmount={}p (snapshot was {}p) correlation={}",
                vaultId, currentBalance, penaltyAmount, releaseAmount,
                request.getReleaseAmount(), correlationId);

        // ── Step 1: Transfer penalty to FEE_REVENUE (if penalty > 0) ─────
        if (penaltyAmount > 0) {
            String penaltyIdempotencyKey = "early-exit-penalty-" + requestId;
            try {
                transferClient.transfer(
                        vault.getLedgerAccountId(),
                        feeRevenueAccountId,
                        penaltyAmount,
                        "VAULT_EARLY_EXIT_PENALTY",
                        "Early-exit penalty for vault " + vaultId,
                        requestId,
                        correlationId,
                        penaltyIdempotencyKey
                );
                log.debug("Penalty transferred: vault={} penalty={}p correlation={}",
                        vaultId, penaltyAmount, correlationId);
            } catch (Exception e) {
                handleFailure(request, now, "Penalty transfer failed: " + e.getMessage());
                return;
            }
        }

        // ── Step 2: Initiate withdrawal of release amount ─────────────────
        if (releaseAmount > 0) {
            String withdrawalIdempotencyKey = "early-exit-withdrawal-" + requestId;
            try {
                withdrawalClient.initiateWithdrawal(
                        request.getRequestedByUserId(),
                        userEmail,
                        userFullName,
                        vault.getLedgerAccountId(),
                        releaseAmount,
                        userMomoNumber,
                        userMomoProvider,
                        vaultId,
                        correlationId,
                        withdrawalIdempotencyKey
                );
                log.debug("Release withdrawal initiated: vault={} release={}p correlation={}",
                        vaultId, releaseAmount, correlationId);
            } catch (Exception e) {
                // Penalty transfer already succeeded — the withdrawal failed.
                // The penalty is already moved to FEE_REVENUE. On retry, the penalty
                // transfer is idempotent (same idempotency key), so it won't double-charge.
                handleFailure(request, now, "Withdrawal initiation failed: " + e.getMessage());
                return;
            }
        } else {
            log.info("EarlyExitReleaseProcessor: zero release amount — skipping withdrawal. " +
                     "vault={} correlation={}", vaultId, correlationId);
        }

        // ── Step 3: Close vault and complete request ───────────────────────
        closeVault(vault);
        vaultRepo.save(vault);

        request.recordAttempt(now);
        request.markCompleted(now);
        requestRepo.save(request);

        metrics.recordReleased();
        log.info("EarlyExitReleaseProcessor: completed — vault={} closed, request={} COMPLETED. " +
                 "penalty={}p released={}p correlation={}",
                vaultId, requestId, penaltyAmount, releaseAmount, correlationId);
    }

    // ── Failure handling ──────────────────────────────────────────────────

    private void handleFailure(EarlyExitRequestEntity request, Instant now, String reason) {
        request.recordAttempt(now);
        requestRepo.save(request);
        metrics.recordFailed();

        int attempts = request.getAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            log.error("[P0_ALERT] EarlyExitReleaseProcessor: request={} vault={} " +
                      "exhausted {} attempts. MANUAL INTERVENTION REQUIRED. " +
                      "Last error: {} Scheduled was: {}",
                    request.getId(), request.getVaultId(),
                    MAX_ATTEMPTS, reason, request.getScheduledReleaseAt());
            metrics.recordP0Alert();
            // DO NOT mark COMPLETED or CANCELLED — leave in PENDING for ops.
            // The partial unique index prevents duplicate requests.
            // Ops must investigate and either manually complete or cancel.
        } else {
            log.warn("EarlyExitReleaseProcessor: attempt {}/{} failed for request={} vault={}: {}",
                    attempts, MAX_ATTEMPTS, request.getId(), request.getVaultId(), reason);
        }
    }

    private void closeVault(VaultEntity vault) {
        try {
            var statusField = VaultEntity.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(vault, "CLOSED");
            var exitField = VaultEntity.class.getDeclaredField("earlyExitInProgress");
            exitField.setAccessible(true);
            exitField.set(vault, false);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to close vault", e);
        }
    }
}
