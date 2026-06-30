package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.event.SusuGroupCancelledEvent;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class SusuGroupCancellationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-25T09:00:00Z"), ZoneOffset.UTC);

    private final SusuGroupRepository       groupRepo      = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository  membershipRepo = Mockito.mock(SusuMembershipRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

    private final SusuGroupCancellationService service =
            new SusuGroupCancellationService(
                    groupRepo, membershipRepo, eventPublisher, FIXED_CLOCK);

    private static final UUID ORGANISER_ID = UUID.randomUUID();
    private static final UUID MEMBER_1_ID  = UUID.randomUUID();
    private static final UUID MEMBER_2_ID  = UUID.randomUUID();
    private static final UUID GROUP_ID     = UUID.randomUUID();
    private static final String CORR       = "corr-cancel-001";

    @BeforeEach
    void setUp() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pendingGroup()));
        when(membershipRepo.findActiveUserIds(GROUP_ID))
                .thenReturn(List.of(ORGANISER_ID, MEMBER_1_ID, MEMBER_2_ID));
        when(membershipRepo.cancelAllActiveMemberships(eq(GROUP_ID), any())).thenReturn(3);
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy: cancel group ────────────────────────────────────────────

    @Test
    @DisplayName("happy cancel: group transitions to CANCELLED")
    void group_transitions_to_cancelled() {
        service.cancelGroup(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuGroupEntity> captor =
                ArgumentCaptor.forClass(SusuGroupEntity.class);
        verify(groupRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("happy cancel: cancelAllActiveMemberships called with now()")
    void memberships_bulk_transitioned_to_left() {
        service.cancelGroup(GROUP_ID, ORGANISER_ID, CORR);

        verify(membershipRepo).cancelAllActiveMemberships(
                eq(GROUP_ID), eq(Instant.parse("2026-07-25T09:00:00Z")));
    }

    @Test
    @DisplayName("happy cancel: SusuGroupCancelledEvent emitted with all affected member IDs")
    void cancelled_event_emitted_with_members() {
        service.cancelGroup(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuGroupCancelledEvent> captor =
                ArgumentCaptor.forClass(SusuGroupCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        SusuGroupCancelledEvent event = captor.getValue();
        assertThat(event.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(event.getOrganiserUserId()).isEqualTo(ORGANISER_ID);
        assertThat(event.getAffectedMemberIds())
                .containsExactlyInAnyOrder(ORGANISER_ID, MEMBER_1_ID, MEMBER_2_ID);
        assertThat(event.getGroupName()).isEqualTo("Akua's Circle");
    }

    @Test
    @DisplayName("affected member IDs snapshot taken before membership transition")
    void member_ids_snapshotted_before_transition() {
        InOrder order = inOrder(membershipRepo);
        service.cancelGroup(GROUP_ID, ORGANISER_ID, CORR);
        order.verify(membershipRepo).findActiveUserIds(GROUP_ID);
        order.verify(membershipRepo).cancelAllActiveMemberships(any(), any());
    }

    @Test
    @DisplayName("solo organiser group (no other members): event emitted with organiser only")
    void solo_group_event_has_organiser() {
        when(membershipRepo.findActiveUserIds(GROUP_ID))
                .thenReturn(List.of(ORGANISER_ID));
        when(membershipRepo.cancelAllActiveMemberships(eq(GROUP_ID), any())).thenReturn(1);

        service.cancelGroup(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuGroupCancelledEvent> captor =
                ArgumentCaptor.forClass(SusuGroupCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getAffectedMemberIds()).containsExactly(ORGANISER_ID);
    }

    // ── Authorization ─────────────────────────────────────────────────

    @Test
    @DisplayName("non-organiser returns 403")
    void non_organiser_returns_403() {
        assertThatThrownBy(() ->
                service.cancelGroup(GROUP_ID, UUID.randomUUID(), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
        verify(groupRepo, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ── Active group ───────────────────────────────────────────────────

    @Test
    @DisplayName("ACTIVE group returns 409 SUSU_CANNOT_CANCEL_ACTIVE_GROUP")
    void active_group_returns_409() {
        SusuGroupEntity active = pendingGroup();
        setField(active, "status", "ACTIVE");
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() ->
                service.cancelGroup(GROUP_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_CANNOT_CANCEL_ACTIVE_GROUP");
                });
        verify(groupRepo, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ── Already cancelled ──────────────────────────────────────────────

    @Test
    @DisplayName("already CANCELLED group returns 409")
    void already_cancelled_returns_409() {
        SusuGroupEntity cancelled = pendingGroup();
        setField(cancelled, "status", "CANCELLED");
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() ->
                service.cancelGroup(GROUP_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    // ── Completed group ────────────────────────────────────────────────

    @Test
    @DisplayName("COMPLETED group returns 409")
    void completed_group_returns_409() {
        SusuGroupEntity completed = pendingGroup();
        setField(completed, "status", "COMPLETED");
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() ->
                service.cancelGroup(GROUP_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private SusuGroupEntity pendingGroup() {
        SusuGroupEntity g = SusuGroupEntity.create(
                ORGANISER_ID, "Akua's Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", Instant.parse("2026-06-24T00:00:00Z"));
        setField(g, "id", GROUP_ID);
        return g;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
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
