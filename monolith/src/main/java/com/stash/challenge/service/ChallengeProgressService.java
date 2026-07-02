package com.stash.challenge.service;

import com.stash.challenge.domain.ProcessedDepositEventEntity;
import com.stash.challenge.domain.UserBadgeEntity;
import com.stash.challenge.domain.UserChallengeEntity;
import com.stash.challenge.event.ChallengeCompletedEvent;
import com.stash.challenge.repository.*;
import com.stash.outbox.service.OutboxPublisher;
import com.stash.platform.notification.event.EventEnvelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ChallengeProgressService {

    private final ProcessedDepositEventRepository processedEventRepository;
    private final UserChallengeRepository         userChallengeRepository;
    private final SavingsChallengeRepository      challengeRepository;
    private final BadgeRepository                 badgeRepository;
    private final UserBadgeRepository             userBadgeRepository;
    private final OutboxPublisher                 outboxPublisher;
    private final Clock                           clock;

    public ChallengeProgressService(ProcessedDepositEventRepository processedEventRepository,
                                     UserChallengeRepository userChallengeRepository,
                                     SavingsChallengeRepository challengeRepository,
                                     BadgeRepository badgeRepository,
                                     UserBadgeRepository userBadgeRepository,
                                     OutboxPublisher outboxPublisher,
                                     Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.userChallengeRepository  = userChallengeRepository;
        this.challengeRepository      = challengeRepository;
        this.badgeRepository          = badgeRepository;
        this.userBadgeRepository      = userBadgeRepository;
        this.outboxPublisher          = outboxPublisher;
        this.clock                    = clock;
    }

    @Transactional
    public void handleDeposit(EventEnvelope envelope) {
        if (processedEventRepository.existsByEventId(envelope.eventId())) {
            return;
        }

        UUID userId = UUID.fromString(envelope.payload().get("user_id").asText());
        long amount = envelope.payload().get("amount").asLong();

        userChallengeRepository.incrementActiveSaveAmountProgress(userId, amount);

        for (UserChallengeEntity uc : userChallengeRepository.findNewlyCompletable(userId)) {
            completeChallenge(uc);
        }

        processedEventRepository.save(
                ProcessedDepositEventEntity.create(envelope.eventId(), Instant.now(clock)));
    }

    private void completeChallenge(UserChallengeEntity uc) {
        Instant now = Instant.now(clock);
        uc.complete(now);
        userChallengeRepository.save(uc);

        var challenge = challengeRepository.findById(uc.getChallengeId())
                .orElseThrow(() -> new IllegalStateException(
                        "user_challenges references nonexistent challenge_id=" + uc.getChallengeId()
                        + " — should be impossible given the FK constraint (V32)"));

        if (challenge.getBadgeId() != null) {
            badgeRepository.findById(challenge.getBadgeId()).ifPresent(badge ->
                    userBadgeRepository.save(UserBadgeEntity.create(
                            uc.getUserId(),
                            badge.getBadgeCode(),
                            badge.getBadgeName(),
                            "CHALLENGE",
                            uc.getId(),
                            now)));
        }

        outboxPublisher.publish(
                new ChallengeCompletedEvent(uc.getId(), uc.getUserId(), uc.getChallengeId(), now));
    }
}
