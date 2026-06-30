package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.event.SusuMemberLeftEvent;
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

class SusuOrganiserMemberRemovalServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-25T09:00:00Z"), ZoneOffset.UTC);

    private final SusuGroupRepository       groupRepo      = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository  membershipRepo = Mockito.mock(SusuMembershipRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

    private final SusuOrganiserMemberRemovalService service =
            new SusuOrganiserMemberRemovalService(
                    groupRepo, membershipRepo, eventPublisher, FIXED_CLOCK);

    private static final UUID ORGANISER_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID    = UUID.randomUUID();
    private static final UUID GROUP_ID     = UUID.randomUUID();
    private static final String CORR       = "corr-org-remove-001";

    @BeforeEach
    void setUp() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pendingGroup()));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(activeMembership(MEMBER_ID)));
        when(membershipRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy: remove member ──────────────────────────────────────────

    @Test
    @DisplayName("happy removal: membership transitions to REMOVED with removed_at set")
    void happy_removal() {
        service.removeMember(GROUP_ID, MEMBER_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo("REMOVED");
        assertThat(captor.getValue().getRemovedAt())
                .isEqualTo(Instant.parse("2026-07-25T09:00:00Z"));
    }

    @Test
    @DisplayName("happy removal: SusuMemberLeft event emitted with reason=REMOVED")
    void event_emitted() {
        service.removeMember(GROUP_ID, MEMBER_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuMemberLeftEvent> captor =
                ArgumentCaptor.forClass(SusuMemberLeftEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertThat(captor.getValue().getUserId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().getReason()).isEqualTo("REMOVED");
        assertThat(captor.getValue().getGroupStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("after removal, slot reopens — REMOVED member no longer counted as ACTIVE")
    void slot_reopens_after_removal() {
        service.removeMember(GROUP_ID, MEMBER_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("REMOVED");
    }

    // ── Authorization ─────────────────────────────────────────────────

    @Test
    @DisplayName("non-organiser caller returns 403")
    void non_organiser_returns_403() {
        assertThatThrownBy(() ->
                service.removeMember(GROUP_ID, MEMBER_ID, UUID.randomUUID(), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
        verifyNoInteractions(membershipRepo);
    }

    // ── Self-removal ───────────────────────────────────────────────────

    @Test
    @DisplayName("organiser removing themselves returns 422 SUSU_ORGANISER_CANNOT_REMOVE_SELF")
    void organiser_self_removal_returns_422() {
        assertThatThrownBy(() ->
                service.removeMember(GROUP_ID, ORGANISER_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("SUSU_ORGANISER_CANNOT_REMOVE_SELF");
                });
        verify(membershipRepo, never()).save(any());
    }

    // ── Active group ───────────────────────────────────────────────────

    @Test
    @DisplayName("ACTIVE group returns 409 SUSU_CANNOT_REMOVE_FROM_ACTIVE_GROUP")
    void active_group_returns_409() {
        SusuGroupEntity active = pendingGroup();
        setField(active, "status", "ACTIVE");
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() ->
                service.removeMember(GROUP_ID, MEMBER_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_CANNOT_REMOVE_FROM_ACTIVE_GROUP");
                });
    }

    // ── Member not found ───────────────────────────────────────────────

    @Test
    @DisplayName("member not in group returns 404")
    void member_not_found_returns_404() {
        when(membershipRepo.findByGroupAndUser(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.removeMember(GROUP_ID, MEMBER_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    // ── Already removed ────────────────────────────────────────────────

    @Test
    @DisplayName("already REMOVED member returns 409")
    void already_removed_returns_409() {
        SusuMembershipEntity alreadyRemoved = activeMembership(MEMBER_ID);
        setField(alreadyRemoved, "status", "REMOVED");
        when(membershipRepo.findByGroupAndUser(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(alreadyRemoved));

        assertThatThrownBy(() ->
                service.removeMember(GROUP_ID, MEMBER_ID, ORGANISER_ID, CORR))
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

    private SusuMembershipEntity activeMembership(UUID userId) {
        SusuMembershipEntity m = SusuMembershipEntity.create(
                GROUP_ID, userId, Instant.parse("2026-06-24T00:00:00Z"));
        setField(m, "id", UUID.randomUUID());
        return m;
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
