package com.stash.platform.user.service;

import com.stash.admin.integration.IntegrationPaymentsClient;
import com.stash.outbox.service.OutboxPublisher;
import com.stash.platform.susu.service.SusuMembershipService;
import com.stash.platform.user.event.UserDeletedEvent;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.service.VaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeletionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DeletionExecutionService.class);

    private final DeletionBlockerEvaluationService blockerService;
    private final UserRepository                   userRepository;
    private final RefreshTokenRepository           refreshTokenRepository;
    private final VaultService                     vaultService;
    private final IntegrationPaymentsClient        paymentsClient;
    private final SusuMembershipService            susuMembershipService;
    private final OutboxPublisher                  outboxPublisher;
    private final Clock                            clock;

    public DeletionExecutionService(DeletionBlockerEvaluationService blockerService,
                                     UserRepository userRepository,
                                     RefreshTokenRepository refreshTokenRepository,
                                     VaultService vaultService,
                                     IntegrationPaymentsClient paymentsClient,
                                     SusuMembershipService susuMembershipService,
                                     OutboxPublisher outboxPublisher,
                                     Clock clock) {
        this.blockerService         = blockerService;
        this.userRepository         = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.vaultService           = vaultService;
        this.paymentsClient         = paymentsClient;
        this.susuMembershipService  = susuMembershipService;
        this.outboxPublisher        = outboxPublisher;
        this.clock                  = clock;
    }

    /**
     * Executes one deletion request as an idempotent saga.
     *
     * <p>Each step is independently safe to re-run on retry:
     * soft-delete is conditional, token revocation is conditional, ledger-account
     * close must be idempotent on the Payments side, susu cancellation uses a
     * status-guarded UPDATE.
     *
     * <p>Returns an Outcome so the caller (DeletionCleanupJob) decides what to write
     * back to the deletion_requests row in its own short transaction — this method
     * must not hold a long transaction across the HTTP calls in steps 3–4.
     */
    public Outcome execute(UUID deletionRequestId, UUID userId) {
        List<String> blockers = blockerService.evaluateBlockers(userId);
        if (!blockers.isEmpty()) {
            // Per Schema doc §1.4: the user stays PENDING until blockers clear.
            // This is not an error; no attempts increment happens.
            log.info("Deletion request {} still blocked for user {}: {}",
                    deletionRequestId, userId, blockers);
            return Outcome.STILL_BLOCKED;
        }

        try {
            // Step 1 — soft-delete (idempotent: WHERE deletedAt IS NULL)
            userRepository.softDeleteIfNotAlready(userId, Instant.now(clock));

            // Step 2 — revoke all active refresh tokens (idempotent: WHERE revokedAt IS NULL)
            refreshTokenRepository.revokeAllActiveForUser(userId, "ACCOUNT_DELETED", Instant.now(clock));

            // Steps 3a — close all vault ledger accounts via Payments Service.
            // Idempotency is the callee's responsibility: an already-CLOSED account
            // must be a no-op, not an error.
            for (UUID vaultAccountId : vaultService.listActiveLedgerAccountIdsForOwner(userId)) {
                paymentsClient.closeLedgerAccount(vaultAccountId);
            }

            // Step 3b — close USER_WALLET ledger account.
            // TODO: wire to wherever USER_WALLET account lookup actually lives —
            // not resolved in this issue. Currently throws UnsupportedOperationException
            // so the saga falls into FAILED_THIS_ATTEMPT until this is wired.
            UUID walletAccountId = resolveUserWalletAccountId(userId);
            paymentsClient.closeLedgerAccount(walletAccountId);

            // Step 4 — cancel PENDING-group memberships (idempotent via status guard)
            susuMembershipService.cancelMembershipsInPendingGroups(userId);

            outboxPublisher.publish(new UserDeletedEvent(userId, Instant.now(clock)));

            return Outcome.SUCCEEDED;

        } catch (Exception e) {
            log.warn("Deletion execution failed for request {} (user {}): {}",
                    deletionRequestId, userId, e.getMessage());
            return Outcome.FAILED_THIS_ATTEMPT;
        }
    }

    private UUID resolveUserWalletAccountId(UUID userId) {
        throw new UnsupportedOperationException(
                "TODO: wire to wherever USER_WALLET account lookup actually lives — not resolved in v0.5-019");
    }

    public enum Outcome {
        SUCCEEDED,
        STILL_BLOCKED,
        FAILED_THIS_ATTEMPT
    }
}
