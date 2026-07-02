package com.stash.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.challenge.domain.*;
import com.stash.challenge.event.ChallengeCompletedEvent;
import com.stash.challenge.repository.*;
import com.stash.outbox.service.OutboxPublisher;
import com.stash.platform.notification.event.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChallengeProgressServiceTest {

    private static final Clock  FIXED_CLOCK  =
            Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID   USER_ID      = UUID.randomUUID();
    private static final UUID   CHALLENGE_ID = UUID.randomUUID();
    private static final UUID   BADGE_ID     = UUID.randomUUID();

    private final ProcessedDepositEventRepository processedEventRepository =
            mock(ProcessedDepositEventRepository.class);
    private final UserChallengeRepository    userChallengeRepository  = mock(UserChallengeRepository.class);
    private final SavingsChallengeRepository challengeRepository      = mock(SavingsChallengeRepository.class);
    private final BadgeRepository            badgeRepository          = mock(BadgeRepository.class);
    private final UserBadgeRepository        userBadgeRepository      = mock(UserBadgeRepository.class);
    private final OutboxPublisher            outboxPublisher          = mock(OutboxPublisher.class);

    private final ChallengeProgressService service = new ChallengeProgressService(
            processedEventRepository, userChallengeRepository, challengeRepository,
            badgeRepository, userBadgeRepository, outboxPublisher, FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userChallengeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userBadgeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private EventEnvelope depositEnvelope(String eventId, UUID userId, long amount) {
        var payload = MAPPER.createObjectNode()
                .put("user_id", userId.toString())
                .put("amount", amount);
        return new EventEnvelope(eventId, "DepositCompleted", "1.0", "payments",
                Instant.now(FIXED_CLOCK), "corr-1", payload);
    }

    @Test
    @DisplayName("deposit increments SAVE_AMOUNT progress by the deposited amount")
    void progressIncremented() {
        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(false);
        when(userChallengeRepository.findNewlyCompletable(USER_ID)).thenReturn(List.of());

        service.handleDeposit(depositEnvelope("evt-1", USER_ID, 5000));

        verify(userChallengeRepository).incrementActiveSaveAmountProgress(USER_ID, 5000);
    }

    @Test
    @DisplayName("completion at threshold: status COMPLETED, badge awarded, event emitted")
    void completionAtThreshold() {
        when(processedEventRepository.existsByEventId("evt-2")).thenReturn(false);

        UserChallengeEntity uc =
                UserChallengeEntity.create(USER_ID, CHALLENGE_ID, Instant.now(FIXED_CLOCK));
        when(userChallengeRepository.findNewlyCompletable(USER_ID)).thenReturn(List.of(uc));

        var challenge = mock(SavingsChallengeEntity.class);
        when(challenge.getBadgeId()).thenReturn(BADGE_ID);
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));

        var badge = mock(BadgeEntity.class);
        when(badge.getBadgeCode()).thenReturn("CONSISTENT_SAVER");
        when(badge.getBadgeName()).thenReturn("Consistent Saver");
        when(badgeRepository.findById(BADGE_ID)).thenReturn(Optional.of(badge));

        service.handleDeposit(depositEnvelope("evt-2", USER_ID, 20000));

        assertThat(uc.getStatus()).isEqualTo("COMPLETED");
        verify(userBadgeRepository).save(argThat(b ->
                "CONSISTENT_SAVER".equals(b.getBadgeCode())
                && "CHALLENGE".equals(b.getAwardedForEntityType())));
        verify(outboxPublisher).publish(any(ChallengeCompletedEvent.class));
    }

    @Test
    @DisplayName("completion with no badge_id skips badge award but still emits event")
    void completionWithNoBadge() {
        when(processedEventRepository.existsByEventId("evt-3")).thenReturn(false);

        UserChallengeEntity uc =
                UserChallengeEntity.create(USER_ID, CHALLENGE_ID, Instant.now(FIXED_CLOCK));
        when(userChallengeRepository.findNewlyCompletable(USER_ID)).thenReturn(List.of(uc));

        var challenge = mock(SavingsChallengeEntity.class);
        when(challenge.getBadgeId()).thenReturn(null);
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));

        service.handleDeposit(depositEnvelope("evt-3", USER_ID, 20000));

        verify(userBadgeRepository, never()).save(any());
        verify(outboxPublisher).publish(any(ChallengeCompletedEvent.class));
    }

    @Test
    @DisplayName("duplicate event_id is a complete no-op — no progress, no completion, no save")
    void duplicateEventIsNoOp() {
        when(processedEventRepository.existsByEventId("evt-dup")).thenReturn(true);

        service.handleDeposit(depositEnvelope("evt-dup", USER_ID, 5000));

        verify(userChallengeRepository, never()).incrementActiveSaveAmountProgress(any(), anyLong());
        verify(userChallengeRepository, never()).findNewlyCompletable(any());
        verify(processedEventRepository, never()).save(any());
    }
}
