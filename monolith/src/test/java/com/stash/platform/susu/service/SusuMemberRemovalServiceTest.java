package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuMemberLeftEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class SusuMemberRemovalServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-25T09:00:00Z"), ZoneOffset.UTC);

    private final SusuGroupRepository        groupRepo        = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository   membershipRepo   = Mockito.mock(SusuMembershipRepository.class);
    private final SusuContributionRepository contributionRepo = Mockito.mock(SusuContributionRepository.class);
    private final SusuRoundRepository        roundRepo        = Mockito.mock(SusuRoundRepository.class);
    private final ApplicationEventPublisher  eventPublisher   = Mockito.mock(ApplicationEventPublisher.class);

    private final SusuMemberRemovalService service = new SusuMemberRemovalService(
            groupRepo, membershipRepo, contributionRepo, roundRepo,
            eventPublisher, FIXED_CLOCK);

    private static final UUID ADMIN_ID   = UUID.randomUUID();
    private static final UUID MEMBER_ID  = UUID.randomUUID();
    private static final UUID GROUP_ID   = UUID.randomUUID();
    private static final UUID ROUND_ID_3 = UUID.randomUUID();
    private static final UUID ROUND_ID_5 = UUID.randomUUID();
    private static final String CORR     = "corr-remove-001";

    @BeforeEach
    void setUp() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup()));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(activeMembership()));
        when(contributionRepo.cancelPendingContributions(GROUP_ID, MEMBER_ID)).thenReturn(2);
        when(roundRepo.findPendingRoundsByRecipient(GROUP_ID, MEMBER_ID))
                .thenReturn(List.of(pendingRound(ROUND_ID_3, 3), pendingRound(ROUND_ID_5, 5)));
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roundRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy: admin removes from ACTIVE group ─────────────────────────────

    @Test
    @DisplayName("membership transitions to REMOVED with removed_at set")
    void membership_removed() {
        service.removeMember(GROUP_ID, MEMBER_ID, ADMIN_ID, CORR);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("REMOVED");
        assertThat(captor.getValue().getRemovedAt()).isNotNull();
    }

    @Test
    @DisplayName("future contributions cancelled (set to MISSED)")
    void future_contributions_cancelled() {
        service.removeMember(GROUP_ID, MEMBER_ID, ADMIN_ID, CORR);
        verify(contributionRepo).cancelPendingContributions(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("both pending rounds (3 and 5) marked SKIPPED")
    void pending_rounds_skipped() {
        service.removeMember(GROUP_ID, MEMBER_ID, ADMIN_ID, CORR);

        ArgumentCaptor<SusuRoundEntity> captor = ArgumentCaptor.forClass(SusuRoundEntity.class);
        verify(roundRepo, times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .allMatch(r -> "SKIPPED".equals(r.getStatus()));
    }

    @Test
    @DisplayName("susu.member.left event emitted with reason=REMOVED")
    void event_emitted_as_removed() {
        service.removeMember(GROUP_ID, MEMBER_ID, ADMIN_ID, CORR);

        ArgumentCaptor<SusuMemberLeftEvent> captor =
                ArgumentCaptor.forClass(SusuMemberLeftEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertThat(captor.getValue().getUserId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().getReason()).isEqualTo("REMOVED");
        assertThat(captor.getValue().isGroupCancelled()).isFalse();
    }

    @Test
    @DisplayName("member with no pending rounds: still removed, no round saves")
    void member_with_no_pending_rounds() {
        when(roundRepo.findPendingRoundsByRecipient(GROUP_ID, MEMBER_ID)).thenReturn(List.of());

        service.removeMember(GROUP_ID, MEMBER_ID, ADMIN_ID, CORR);

        verify(membershipRepo).save(any());
        verify(contributionRepo).cancelPendingContributions(GROUP_ID, MEMBER_ID);
        verify(roundRepo, never()).save(any());
    }

    // ── Validation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("removing from non-ACTIVE group returns 409")
    void remove_from_pending_group_rejected() {
        SusuGroupEntity pending = activeGroup();
        setField(pending, "status", "PENDING");
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() ->
                service.removeMember(GROUP_ID, MEMBER_ID, ADMIN_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("already REMOVED member returns 409")
    void already_removed_returns_409() {
        SusuMembershipEntity removed = activeMembership();
        setField(removed, "status", "REMOVED");
        when(membershipRepo.findByGroupAndUser(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(removed));

        assertThatThrownBy(() ->
                service.removeMember(GROUP_ID, MEMBER_ID, ADMIN_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SusuGroupEntity activeGroup() {
        SusuGroupEntity g = SusuGroupEntity.create(
                UUID.randomUUID(), "Akua's Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", Instant.parse("2026-06-24T00:00:00Z"));
        setField(g, "id",     GROUP_ID);
        setField(g, "status", "ACTIVE");
        return g;
    }

    private SusuMembershipEntity activeMembership() {
        SusuMembershipEntity m = SusuMembershipEntity.create(
                GROUP_ID, MEMBER_ID, Instant.parse("2026-06-24T00:00:00Z"));
        setField(m, "id",               UUID.randomUUID());
        setField(m, "rotationPosition", 3);
        return m;
    }

    private SusuRoundEntity pendingRound(UUID id, int number) {
        SusuRoundEntity r = new SusuRoundEntity();
        setField(r, "id",              id);
        setField(r, "susuGroupId",     GROUP_ID);
        setField(r, "roundNumber",     number);
        setField(r, "recipientUserId", MEMBER_ID);
        setField(r, "status",          "PENDING");
        setField(r, "expectedPotAmount", 120_000L);
        return r;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            Field f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        try { return c.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (c.getSuperclass() != null) return findField(c.getSuperclass(), name);
            throw e;
        }
    }
}
