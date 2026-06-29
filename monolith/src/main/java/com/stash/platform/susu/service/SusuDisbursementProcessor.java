package com.stash.platform.susu.service;

import com.stash.platform.susu.client.SusuContributionTransferClient;
import com.stash.platform.susu.client.SusuContributionTransferClient.DisbursementResult;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuGroupCompletedEvent;
import com.stash.platform.susu.event.SusuRoundCompletedEvent;
import com.stash.platform.susu.event.SusuRoundSkippedEvent;
import com.stash.platform.susu.event.SusuRoundStartedEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Processes one DISBURSING round: transfers the pot, closes the round,
 * opens the next round (or completes the group).
 *
 * <p><strong>Idempotency:</strong> the DISBURSING status guard is the key.
 * If a round is already DISBURSED/COMPLETED when this is called (duplicate
 * event, or the scheduled poller picking up a round the event-driven path
 * already finished), it's a no-op. The disbursement idempotency key
 * ({@code "susu-disbursement-" + roundId}) is stable across retries, so the
 * Payments Service also deduplicates if a retry reaches it after a prior
 * attempt actually succeeded.
 *
 * <p><strong>Transaction isolation:</strong> {@link Propagation#REQUIRES_NEW}
 * so each round is processed in its own transaction — matches
 * VaultAutoUnlockProcessor's reasoning for the same SKIP LOCKED + per-row-tx pattern.
 */
@Service
public class SusuDisbursementProcessor {

    private static final Logger log = LoggerFactory.getLogger(SusuDisbursementProcessor.class);

    private final SusuGroupRepository            groupRepo;
    private final SusuRoundRepository            roundRepo;
    private final SusuContributionRepository     contributionRepo;
    private final SusuMembershipRepository       membershipRepo;
    private final SusuContributionTransferClient transferClient;
    private final ApplicationEventPublisher      eventPublisher;
    private final Clock                          clock;

    public SusuDisbursementProcessor(
            SusuGroupRepository groupRepo,
            SusuRoundRepository roundRepo,
            SusuContributionRepository contributionRepo,
            SusuMembershipRepository membershipRepo,
            SusuContributionTransferClient transferClient,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.groupRepo        = groupRepo;
        this.roundRepo        = roundRepo;
        this.contributionRepo = contributionRepo;
        this.membershipRepo   = membershipRepo;
        this.transferClient   = transferClient;
        this.eventPublisher   = eventPublisher;
        this.clock            = clock;
    }

    /**
     * Processes one disbursement round. Each call is idempotent on the round ID.
     *
     * @param roundId       the round to disburse
     * @param correlationId trace ID
     * @throws SusuPaymentsException if the Payments Service call fails — the
     *                                caller decides how to handle retry
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID roundId, String correlationId) {
        SusuRoundEntity round = roundRepo.findByIdForUpdate(roundId)
                .orElseThrow(() -> new IllegalStateException("Round not found: " + roundId));

        if ("DISBURSED".equals(round.getStatus()) || "COMPLETED".equals(round.getStatus())) {
            log.info("DisbursementProcessor: round={} already {}, skipping. correlation={}",
                    roundId, round.getStatus(), correlationId);
            return;
        }

        if (!"DISBURSING".equals(round.getStatus())) {
            log.warn("DisbursementProcessor: round={} is in unexpected status={}, skipping.",
                    roundId, round.getStatus());
            return;
        }

        SusuGroupEntity group = groupRepo.findById(round.getSusuGroupId())
                .orElseThrow(() -> new IllegalStateException(
                        "Group not found: " + round.getSusuGroupId()));

        List<SusuRoundEntity> allRounds = roundRepo.findAllByGroup(group.getId());

        // ── Skip check: has the recipient left or been removed? ──────────────
        if (handleSkippedRecipient(round, group, allRounds, correlationId)) {
            return;
        }

        Instant now = Instant.now(clock);

        // ── Resolve recipient USER_WALLET ─────────────────────────────────
        UUID recipientUserId = round.getRecipientUserId();
        UUID recipientWalletId;
        try {
            recipientWalletId = transferClient.resolveUserWallet(recipientUserId, correlationId);
        } catch (SusuPaymentsException e) {
            log.error("[DISBURSEMENT_FAILURE] Cannot resolve wallet for recipient={} round={} " +
                      "correlation={}: {}", recipientUserId, roundId, correlationId, e.getMessage());
            throw e;
        }

        // ── Disburse pot to recipient ─────────────────────────────────────
        long potAmount = round.getActualPotAmount() != null
                ? round.getActualPotAmount() : round.getExpectedPotAmount();
        String idemKey   = "susu-disbursement-" + roundId;
        UUID   susuPotId = group.getLedgerAccountId();

        DisbursementResult result;
        try {
            result = transferClient.disburse(
                    susuPotId, recipientWalletId, potAmount, roundId, correlationId, idemKey);
        } catch (SusuPaymentsException e) {
            log.error("[DISBURSEMENT_FAILURE] Transfer failed for round={} recipient={} " +
                      "amount={}p correlation={}: {}",
                    roundId, recipientUserId, potAmount, correlationId, e.getMessage());
            throw e;
        }

        // ── Close the round ───────────────────────────────────────────────
        setField(round, "status",                    "DISBURSED");
        setField(round, "disbursedAt",               now);
        setField(round, "disbursementTransactionId", result.ledgerTransactionId());
        roundRepo.save(round);

        // DISBURSED -> COMPLETED immediately (no intermediate step at v0.4)
        setField(round, "status", "COMPLETED");
        roundRepo.save(round);

        log.info("DisbursementProcessor: round={} COMPLETED. amount={}p recipient={} " +
                 "txnRef={} correlation={}",
                roundId, potAmount, recipientUserId, result.transactionReference(), correlationId);

        // ── Determine what comes next ─────────────────────────────────────
        int nextRoundNumber = round.getRoundNumber() + 1;
        boolean isFinalRound = round.getRoundNumber() >= allRounds.size();

        if (isFinalRound) {
            completeGroup(group, round, allRounds.size(), recipientUserId, potAmount,
                    result.ledgerTransactionId(), correlationId, now);
        } else {
            openNextRound(group, round, nextRoundNumber, allRounds, recipientUserId,
                    potAmount, result.ledgerTransactionId(), correlationId, now);
        }
    }

    private void completeGroup(SusuGroupEntity group, SusuRoundEntity round, int totalRounds,
                                UUID recipientUserId, long potAmount, UUID ledgerTxnId,
                                String correlationId, Instant now) {
        setField(group, "status", "COMPLETED");
        groupRepo.save(group);

        log.info("DisbursementProcessor: group={} COMPLETED after {} rounds. correlation={}",
                group.getId(), totalRounds, correlationId);

        List<SusuMembershipEntity> members = membershipRepo.findActiveMembersByGroup(group.getId());
        for (SusuMembershipEntity m : members) {
            setField(m, "status", "COMPLETED");
            membershipRepo.save(m);
        }

        eventPublisher.publishEvent(new SusuRoundCompletedEvent(
                this, group.getId(), round.getId(), round.getRoundNumber(), totalRounds,
                recipientUserId, potAmount, ledgerTxnId, correlationId, now));

        eventPublisher.publishEvent(new SusuGroupCompletedEvent(
                this, group.getId(), group.getName(), group.getOrganiserUserId(),
                totalRounds, correlationId, now));
    }

    private void openNextRound(SusuGroupEntity group, SusuRoundEntity round, int nextRoundNumber,
                                List<SusuRoundEntity> allRounds, UUID recipientUserId,
                                long potAmount, UUID ledgerTxnId, String correlationId,
                                Instant now) {
        SusuRoundEntity nextRound = allRounds.stream()
                .filter(r -> r.getRoundNumber() == nextRoundNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Next round " + nextRoundNumber + " not found for group " + group.getId()));

        setField(nextRound, "status", "COLLECTING");
        roundRepo.save(nextRound);

        setField(group, "currentRoundNumber", nextRoundNumber);
        groupRepo.save(group);

        List<SusuMembershipEntity> activeMembers =
                membershipRepo.findActiveMembersByGroup(group.getId());
        for (SusuMembershipEntity member : activeMembers) {
            contributionRepo.save(buildContribution(
                    nextRound.getId(), group.getId(), member.getUserId(),
                    group.getContributionAmount(), now));
        }

        log.info("DisbursementProcessor: opened round={} for group={} with {} contributions. " +
                 "correlation={}",
                nextRoundNumber, group.getId(), activeMembers.size(), correlationId);

        eventPublisher.publishEvent(new SusuRoundCompletedEvent(
                this, group.getId(), round.getId(), round.getRoundNumber(), allRounds.size(),
                recipientUserId, potAmount, ledgerTxnId, correlationId, now));

        eventPublisher.publishEvent(new SusuRoundStartedEvent(
                this, group.getId(), nextRound.getId(), nextRoundNumber, allRounds.size(),
                nextRound.getRecipientUserId(), nextRound.getScheduledCollectionAt(),
                correlationId));
    }

    /**
     * Checks if the round's recipient has LEFT or been REMOVED, and if so,
     * marks the round SKIPPED and rolls the pot forward to the next round.
     *
     * <p>Returns true if the round was skipped (caller should return immediately).
     *
     * <p><strong>Pot roll-forward:</strong> the skipped round's actual_pot_amount
     * (contributions collected from remaining members, if any) is added to the
     * next PENDING round's expected_pot_amount.
     */
    private boolean handleSkippedRecipient(SusuRoundEntity round,
                                            SusuGroupEntity group,
                                            List<SusuRoundEntity> allRounds,
                                            String correlationId) {
        UUID recipientUserId = round.getRecipientUserId();
        if (recipientUserId == null) return false;

        boolean isRemoved = membershipRepo.isRemovedOrLeft(group.getId(), recipientUserId);
        if (!isRemoved) return false;

        Instant now = Instant.now(clock);

        log.info("DisbursementProcessor: recipient={} of round={} is REMOVED/LEFT — skipping. " +
                 "correlation={}", recipientUserId, round.getId(), correlationId);

        long skippedPotAmount = round.getActualPotAmount() != null
                ? round.getActualPotAmount() : 0L;
        setField(round, "status",          "SKIPPED");
        setField(round, "actualPotAmount", skippedPotAmount);
        roundRepo.save(round);

        if (skippedPotAmount > 0) {
            allRounds.stream()
                    .filter(r -> "PENDING".equals(r.getStatus()))
                    .filter(r -> r.getRoundNumber() > round.getRoundNumber())
                    .min(java.util.Comparator.comparingInt(SusuRoundEntity::getRoundNumber))
                    .ifPresent(nextRound -> {
                        long current = nextRound.getExpectedPotAmount() != null
                                ? nextRound.getExpectedPotAmount() : 0L;
                        setField(nextRound, "expectedPotAmount", current + skippedPotAmount);
                        roundRepo.save(nextRound);
                        log.info("DisbursementProcessor: rolled {}p from skipped round={} " +
                                 "to next round={}. correlation={}",
                                skippedPotAmount, round.getRoundNumber(),
                                nextRound.getRoundNumber(), correlationId);
                    });
        }

        int nextRoundNumber = round.getRoundNumber() + 1;
        boolean isFinalRound = (round.getRoundNumber() >= allRounds.size());
        if (!isFinalRound) {
            setField(group, "currentRoundNumber", nextRoundNumber);
            groupRepo.save(group);

            allRounds.stream()
                    .filter(r -> r.getRoundNumber() == nextRoundNumber)
                    .findFirst()
                    .ifPresent(nextRound -> {
                        setField(nextRound, "status", "COLLECTING");
                        roundRepo.save(nextRound);

                        List<SusuMembershipEntity> activeMembers =
                                membershipRepo.findActiveMembersByGroup(group.getId());
                        for (SusuMembershipEntity member : activeMembers) {
                            SusuContributionEntity c = buildContribution(
                                    nextRound.getId(), group.getId(),
                                    member.getUserId(), group.getContributionAmount(), now);
                            contributionRepo.save(c);
                        }
                    });
        } else {
            setField(group, "status", "COMPLETED");
            groupRepo.save(group);
        }

        eventPublisher.publishEvent(new SusuRoundSkippedEvent(
                this, group.getId(), round.getId(),
                round.getRoundNumber(), recipientUserId, skippedPotAmount,
                correlationId, now));

        return true;
    }

    private SusuContributionEntity buildContribution(UUID roundId, UUID groupId,
                                                      UUID memberUserId,
                                                      long expectedAmount, Instant now) {
        SusuContributionEntity c = new SusuContributionEntity();
        setField(c, "susuRoundId",            roundId);
        setField(c, "susuGroupId",            groupId);
        setField(c, "memberUserId",           memberUserId);
        setField(c, "expectedAmount",         expectedAmount);
        setField(c, "status",                 "PENDING");
        setField(c, "collectionAttemptCount", 0);
        setField(c, "penaltyAmount",          0L);
        setField(c, "isLate",                 false);
        setField(c, "createdAt",              now);
        return c;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot set field " + name, e);
        }
    }
}
