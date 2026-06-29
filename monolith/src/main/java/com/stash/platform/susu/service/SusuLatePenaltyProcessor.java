package com.stash.platform.susu.service;

import com.stash.platform.susu.client.SusuContributionTransferClient;
import com.stash.platform.susu.client.SusuContributionTransferClient.PenaltyChargeResult;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.event.SusuContributionLateEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Processes one overdue contribution: marks it LATE, attempts penalty charge.
 *
 * <p><strong>Penalty split (per Overall doc §6.1):</strong> GHS 5 total.
 * <ul>
 *   <li>250 pesewas → SUSU_POT (compensation to the group)</li>
 *   <li>250 pesewas → PENALTY_REVENUE (Stash's operational fee)</li>
 * </ul>
 *
 * <p><strong>Insufficient balance:</strong> if the member cannot cover the penalty,
 * both legs are waived. The contribution stays LATE; penalty_amount = 0.
 * The event is still emitted so the organiser is notified.
 *
 * <p><strong>Idempotency:</strong> the job queries only PENDING contributions.
 * Once a contribution is LATE, it is never selected again. The penalty transfer
 * idempotency key is {@code "susu-penalty-" + contributionId}, stable across
 * job reruns so Payments deduplicates if the job fires twice in a day.
 *
 * <p><strong>Transaction isolation:</strong> {@link Propagation#REQUIRES_NEW}
 * isolates each contribution. A failure on one member does not roll back the
 * other members' penalty records.
 */
@Service
public class SusuLatePenaltyProcessor {

    private static final Logger log = LoggerFactory.getLogger(SusuLatePenaltyProcessor.class);

    private final SusuContributionRepository     contributionRepo;
    private final SusuGroupRepository            groupRepo;
    private final SusuContributionTransferClient transferClient;
    private final ApplicationEventPublisher      eventPublisher;
    private final Clock                          clock;
    private final long                           totalPenaltyPesewas;
    private final UUID                            penaltyRevenueAccountId;

    public SusuLatePenaltyProcessor(
            SusuContributionRepository contributionRepo,
            SusuGroupRepository groupRepo,
            SusuContributionTransferClient transferClient,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${stash.susu.late-penalty.total-amount-pesewas:500}")
                    long totalPenaltyPesewas,
            @Value("${stash.ledger.penalty-revenue-account-id}")
                    String penaltyRevenueAccountId) {
        this.contributionRepo        = contributionRepo;
        this.groupRepo               = groupRepo;
        this.transferClient          = transferClient;
        this.eventPublisher          = eventPublisher;
        this.clock                   = clock;
        this.totalPenaltyPesewas     = totalPenaltyPesewas;
        this.penaltyRevenueAccountId = UUID.fromString(penaltyRevenueAccountId);
    }

    /**
     * Marks one overdue contribution LATE and attempts to charge the split penalty.
     *
     * @param contribution  the overdue PENDING contribution to process
     * @param correlationId trace ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(SusuContributionEntity contribution, String correlationId) {
        UUID contributionId = contribution.getId();
        UUID memberUserId   = contribution.getMemberUserId();
        UUID groupId        = contribution.getSusuGroupId();

        // ── Idempotency guard (belt-and-suspenders) ───────────────────────
        if (!"PENDING".equals(contribution.getStatus())) {
            log.debug("LatePenaltyProcessor: contribution={} already {}, skipping.",
                    contributionId, contribution.getStatus());
            return;
        }

        Instant now = Instant.now(clock);

        // ── Step 1: Mark contribution LATE ────────────────────────────────
        setField(contribution, "status", "LATE");
        setField(contribution, "isLate", true);
        contributionRepo.save(contribution);

        log.info("SusuLatePenalty: contribution={} member={} group={} marked LATE. correlation={}",
                contributionId, memberUserId, groupId, correlationId);

        // ── Step 2: Resolve SUSU_POT account ID ───────────────────────────
        SusuGroupEntity group = groupRepo.findById(groupId).orElse(null);
        if (group == null || group.getLedgerAccountId() == null) {
            log.error("LatePenaltyProcessor: cannot find SUSU_POT for group={}. " +
                      "Penalty waived. correlation={}", groupId, correlationId);
            emitEvent(contribution, 0L, true, now, correlationId);
            return;
        }
        UUID susuPotId = group.getLedgerAccountId();

        // ── Step 3: Resolve member's USER_WALLET ──────────────────────────
        UUID userWalletId;
        try {
            userWalletId = transferClient.resolveUserWallet(memberUserId, correlationId);
        } catch (SusuPaymentsException e) {
            log.warn("LatePenaltyProcessor: cannot resolve USER_WALLET for member={}. " +
                     "Penalty waived. correlation={}", memberUserId, correlationId);
            emitEvent(contribution, 0L, true, now, correlationId);
            return;
        }

        // ── Step 4: Attempt split penalty charge ──────────────────────────
        long   halfPenalty   = totalPenaltyPesewas / 2;
        String idemKeyBase   = "susu-penalty-" + contributionId;

        PenaltyChargeResult result;
        try {
            result = transferClient.chargePenaltySplit(
                    userWalletId, susuPotId, penaltyRevenueAccountId,
                    halfPenalty, contributionId, correlationId, idemKeyBase);
        } catch (SusuPaymentsException e) {
            log.warn("LatePenaltyProcessor: penalty charge failed for contribution={} " +
                     "error={} correlation={}. Waiving.",
                    contributionId, e.getMessage(), correlationId);
            emitEvent(contribution, 0L, true, now, correlationId);
            return;
        }

        // ── Step 5: Record penalty outcome ────────────────────────────────
        if (result.waived()) {
            log.info("SusuLatePenalty: contribution={} member={} penalty WAIVED " +
                     "(insufficient balance). correlation={}",
                    contributionId, memberUserId, correlationId);
            setField(contribution, "penaltyAmount", 0L);
            contributionRepo.save(contribution);
            emitEvent(contribution, 0L, true, now, correlationId);
        } else {
            log.info("SusuLatePenalty: contribution={} member={} penalty {}p charged " +
                     "({}p to pot, {}p to revenue). correlation={}",
                    contributionId, memberUserId, totalPenaltyPesewas,
                    halfPenalty, halfPenalty, correlationId);
            setField(contribution, "penaltyAmount", totalPenaltyPesewas);
            setField(contribution, "transactionReference", result.potTransactionReference());
            contributionRepo.save(contribution);
            emitEvent(contribution, totalPenaltyPesewas, false, now, correlationId);
        }
    }

    private void emitEvent(SusuContributionEntity c, long penaltyAmount,
                            boolean waived, Instant now, String correlationId) {
        eventPublisher.publishEvent(new SusuContributionLateEvent(
                this, c.getSusuGroupId(), c.getSusuRoundId(),
                c.getId(), c.getMemberUserId(),
                penaltyAmount, waived, correlationId, now));
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
