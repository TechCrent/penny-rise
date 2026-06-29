package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.SusuContributionResponse;
import com.stash.platform.susu.client.SusuContributionTransferClient;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuRoundFullyCollectedEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Processes a member's susu contribution payment.
 *
 * <p><strong>Flow:</strong>
 * <ol>
 *   <li>Validate round exists and is COLLECTING.</li>
 *   <li>Validate caller is an active member of the group.</li>
 *   <li>Validate caller's contribution row is PENDING or LATE.</li>
 *   <li>Resolve caller's USER_WALLET ledger account ID via Payments Service.</li>
 *   <li>Transfer contribution_amount from USER_WALLET → SUSU_POT.</li>
 *   <li>Update contribution row: status=PAID, collected_amount, transaction_reference, paid_at.</li>
 *   <li>Check if all contributions for the round are now in a terminal state.</li>
 *   <li>If fully collected: update round actual_pot_amount, publish
 *       {@link SusuRoundFullyCollectedEvent} AFTER_COMMIT.</li>
 * </ol>
 *
 * <p><strong>No direct disbursement:</strong> this service never triggers payout.
 * It publishes {@code susu.round.fully_collected} and returns. The disbursement
 * worker (v0.4-010) owns the payout flow.
 *
 * <p><strong>Idempotency:</strong> the {@code IdempotencyFilter} (v0.3-009) caches
 * the 200 response for the Idempotency-Key. The Payments transfer call also receives
 * the idempotency key — both layers dedup independently.
 *
 * <p><strong>Concurrency:</strong> the contribution row's UNIQUE(round_id, member_user_id)
 * constraint prevents two concurrent payments from the same member. The "fully collected"
 * check uses a PESSIMISTIC_WRITE lock on the round row to prevent two final-contributor
 * transactions from both seeing zero non-terminal contributions and both publishing the event.
 */
@Service
public class SusuContributionService {

    private static final Logger log = LoggerFactory.getLogger(SusuContributionService.class);

    private final SusuRoundRepository            roundRepo;
    private final SusuContributionRepository     contributionRepo;
    private final SusuGroupRepository            groupRepo;
    private final SusuMembershipRepository       membershipRepo;
    private final SusuContributionTransferClient transferClient;
    private final ApplicationEventPublisher      eventPublisher;
    private final Clock                          clock;

    public SusuContributionService(
            SusuRoundRepository roundRepo,
            SusuContributionRepository contributionRepo,
            SusuGroupRepository groupRepo,
            SusuMembershipRepository membershipRepo,
            SusuContributionTransferClient transferClient,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.roundRepo        = roundRepo;
        this.contributionRepo = contributionRepo;
        this.groupRepo        = groupRepo;
        this.membershipRepo   = membershipRepo;
        this.transferClient   = transferClient;
        this.eventPublisher   = eventPublisher;
        this.clock            = clock;
    }

    @Transactional
    public SusuContributionResponse payContribution(UUID roundId, UUID callerId,
                                                     String correlationId,
                                                     String idempotencyKey) {
        // ── Load round ────────────────────────────────────────────────────
        SusuRoundEntity round = roundRepo.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Round not found: " + roundId));

        if (!"COLLECTING".equals(round.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_ROUND_NOT_COLLECTING: This round is " +
                    round.getStatus().toLowerCase() + " and is not accepting contributions.");
        }

        // ── Validate group membership ─────────────────────────────────────
        SusuGroupEntity group = groupRepo.findById(round.getSusuGroupId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Group not found for round."));

        if (!membershipRepo.isActiveMember(group.getId(), callerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not an active member of this susu group.");
        }

        // ── Load caller's contribution row ────────────────────────────────
        SusuContributionEntity contribution =
                contributionRepo.findByRoundAndMember(roundId, callerId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "No contribution row found for this round and member. " +
                                "Contact support if this is unexpected."));

        if ("PAID".equals(contribution.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_CONTRIBUTION_ALREADY_PAID: You have already paid your " +
                    "contribution for this round.");
        }

        if (!"PENDING".equals(contribution.getStatus()) &&
                !"LATE".equals(contribution.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_CONTRIBUTION_NOT_PAYABLE: Contribution status is " +
                    contribution.getStatus() + " and cannot be paid.");
        }

        // ── Resolve USER_WALLET account ID ────────────────────────────────
        UUID userWalletId;
        try {
            userWalletId = transferClient.resolveUserWallet(callerId, correlationId);
        } catch (SusuPaymentsException e) {
            log.error("Could not resolve USER_WALLET for user={} correlation={}",
                    callerId, correlationId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment service unavailable. Please try again.");
        }

        // ── Execute transfer: USER_WALLET → SUSU_POT ──────────────────────
        String transactionReference;
        try {
            transactionReference = transferClient.transfer(
                    userWalletId,
                    group.getLedgerAccountId(),
                    contribution.getExpectedAmount(),
                    contribution.getId(),
                    correlationId,
                    idempotencyKey
            );
        } catch (SusuPaymentsException e) {
            String body = e.getMessage();
            if (body != null && body.contains("422") &&
                    body.contains("PAYMENTS_INSUFFICIENT_BALANCE")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "SUSU_INSUFFICIENT_BALANCE: Your wallet balance is too low " +
                        "to pay this contribution. Please top up and try again.");
            }
            log.error("Contribution transfer failed: round={} member={} error={} correlation={}",
                    roundId, callerId, e.getMessage(), correlationId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment failed. Your wallet was not charged. Please try again.");
        }

        // ── Update contribution row ────────────────────────────────────────
        Instant now = Instant.now(clock);
        setField(contribution, "status",               "PAID");
        setField(contribution, "collectedAmount",      contribution.getExpectedAmount());
        setField(contribution, "transactionReference", transactionReference);
        setField(contribution, "paidAt",               now);
        contributionRepo.save(contribution);

        log.info("SusuContribution PAID: round={} member={} amount={}p txnRef={} correlation={}",
                roundId, callerId, contribution.getExpectedAmount(),
                transactionReference, correlationId);

        // ── Check if round is fully collected ─────────────────────────────
        // Acquire write lock on round row to prevent concurrent final-contributor
        // race where two transactions both see zero non-terminal contributions.
        SusuRoundEntity roundForUpdate = roundRepo.findByIdForUpdate(roundId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Round vanished."));

        long nonTerminal     = contributionRepo.countNonTerminalContributions(roundId);
        boolean fullyCollected = (nonTerminal == 0);

        if (fullyCollected) {
            long actualPot = contributionRepo.sumCollectedAmountForRound(roundId);
            setField(roundForUpdate, "actualPotAmount", actualPot);
            setField(roundForUpdate, "status",          "DISBURSING");
            roundRepo.save(roundForUpdate);

            log.info("Round fully collected: round={} group={} actualPot={}p correlation={}",
                    roundId, group.getId(), actualPot, correlationId);

            eventPublisher.publishEvent(new SusuRoundFullyCollectedEvent(
                    this, group.getId(), roundId,
                    round.getRoundNumber(), actualPot,
                    round.getRecipientUserId(), correlationId, now));
        }

        return SusuContributionResponse.from(contribution, fullyCollected);
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot set field " + name, e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name)
            throws NoSuchFieldException {
        try { return c.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (c.getSuperclass() != null) return findField(c.getSuperclass(), name);
            throw e;
        }
    }
}
