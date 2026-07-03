package com.stash.platform.susu.event;

import com.stash.platform.susu.repository.SusuGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SusuGroupFlaggingListenerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID GROUP_ID = UUID.randomUUID();

    private final SusuGroupRepository groupRepo = mock(SusuGroupRepository.class);
    private final SusuGroupFlaggingListener listener = new SusuGroupFlaggingListener(groupRepo, FIXED_CLOCK);

    private SusuContributionLateEvent event(boolean waived) {
        return new SusuContributionLateEvent(
                this, GROUP_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                waived ? 0L : 500L, waived, "corr-1", Instant.now(FIXED_CLOCK));
    }

    @Test
    @DisplayName("penalty waived: flags the group for review")
    void waivedPenaltyFlagsGroup() {
        listener.onContributionLate(event(true));

        verify(groupRepo).flagForReview(GROUP_ID, Instant.now(FIXED_CLOCK));
    }

    @Test
    @DisplayName("penalty successfully charged (not waived): does NOT flag the group — a routine late payment isn't a shortfall")
    void chargedPenaltyDoesNotFlagGroup() {
        listener.onContributionLate(event(false));

        verify(groupRepo, never()).flagForReview(any(), any());
    }
}
