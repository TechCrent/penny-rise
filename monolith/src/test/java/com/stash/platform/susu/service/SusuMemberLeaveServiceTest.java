package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.event.SusuMemberLeftEvent;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class SusuMemberLeaveServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-25T09:00:00Z"), ZoneOffset.UTC);

    private final SusuGroupRepository       groupRepo      = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository  membershipRepo = Mockito.mock(SusuMembershipRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

    private final SusuMemberLeaveService service =
            new SusuMemberLeaveService(groupRepo, membershipRepo, eventPublisher, FIXED_CLOCK);

    private static final UUID ORGANISER_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID    = UUID.randomUUID();
    private static final UUID GROUP_ID     = UUID.randomUUID();
    private static final String CORR       = "corr-leave-001";

    @BeforeEach
    void setUp() {
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy: leave PENDING group ────────────────────────────────────────

    @Test
    @DisplayName("member leaves PENDING group: membership transitions to LEFT")
    void member_leaves_pending_group() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pendingGroup(ORGANISER_ID)));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(activeMembership(GROUP_ID, MEMBER_ID)));

        service.leaveGroup(GROUP_ID, MEMBER_ID, CORR);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("LEFT");
        assertThat(captor.getValue().getRemovedAt())
                .isEqualTo(Instant.parse("2026-07-25T09:00:00Z"));
    }

    @Test
    @DisplayName("member leaves PENDING: susu.member.left event emitted with reason=LEFT")
    void event_emitted_on_leave() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pendingGroup(ORGANISER_ID)));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(activeMembership(GROUP_ID, MEMBER_ID)));

        service.leaveGroup(GROUP_ID, MEMBER_ID, CORR);

        ArgumentCaptor<SusuMemberLeftEvent> captor =
                ArgumentCaptor.forClass(SusuMemberLeftEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().getReason()).isEqualTo("LEFT");
        assertThat(captor.getValue().isGroupCancelled()).isFalse();
    }

    // ── Organiser leaves PENDING group: group cancelled ───────────────────

    @Test
    @DisplayName("organiser leaves PENDING group: group auto-cancelled")
    void organiser_leaves_cancels_group() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pendingGroup(ORGANISER_ID)));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, ORGANISER_ID))
                .thenReturn(Optional.of(activeMembership(GROUP_ID, ORGANISER_ID)));

        service.leaveGroup(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuGroupEntity> captor = ArgumentCaptor.forClass(SusuGroupEntity.class);
        verify(groupRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("organiser leaves: event has groupCancelled=true")
    void organiser_leaves_event_cancelled() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pendingGroup(ORGANISER_ID)));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, ORGANISER_ID))
                .thenReturn(Optional.of(activeMembership(GROUP_ID, ORGANISER_ID)));

        service.leaveGroup(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuMemberLeftEvent> captor =
                ArgumentCaptor.forClass(SusuMemberLeftEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isGroupCancelled()).isTrue();
        assertThat(captor.getValue().getGroupStatus()).isEqualTo("CANCELLED");
    }

    // ── Cannot leave ACTIVE group ─────────────────────────────────────────

    @Test
    @DisplayName("leave ACTIVE group returns 409 SUSU_CANNOT_LEAVE_ACTIVE_GROUP")
    void cannot_leave_active_group() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup(ORGANISER_ID)));

        assertThatThrownBy(() -> service.leaveGroup(GROUP_ID, MEMBER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_CANNOT_LEAVE_ACTIVE_GROUP");
                });

        verifyNoInteractions(membershipRepo);
        verifyNoInteractions(eventPublisher);
    }

    // ── Not a member ───────────────────────────────────────────────────────

    @Test
    @DisplayName("non-member trying to leave returns 404")
    void non_member_returns_404() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pendingGroup(ORGANISER_ID)));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveGroup(GROUP_ID, MEMBER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SusuGroupEntity pendingGroup(UUID organiserId) {
        SusuGroupEntity g = SusuGroupEntity.create(
                organiserId, "Akua's Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", Instant.parse("2026-06-24T00:00:00Z"));
        setField(g, "id", GROUP_ID);
        return g;
    }

    private SusuGroupEntity activeGroup(UUID organiserId) {
        SusuGroupEntity g = pendingGroup(organiserId);
        setField(g, "status", "ACTIVE");
        return g;
    }

    private SusuMembershipEntity activeMembership(UUID groupId, UUID userId) {
        SusuMembershipEntity m = SusuMembershipEntity.create(
                groupId, userId, Instant.parse("2026-06-24T00:00:00Z"));
        setField(m, "id", UUID.randomUUID());
        return m;
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
