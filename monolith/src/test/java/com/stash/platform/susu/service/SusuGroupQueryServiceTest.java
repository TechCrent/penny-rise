package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.SusuGroupDetailResponse;
import com.stash.platform.susu.api.dto.SusuGroupListItemResponse;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class SusuGroupQueryServiceTest {

    private final SusuGroupRepository        groupRepo        = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository   membershipRepo   = Mockito.mock(SusuMembershipRepository.class);
    private final SusuRoundRepository        roundRepo        = Mockito.mock(SusuRoundRepository.class);
    private final SusuContributionRepository contributionRepo = Mockito.mock(SusuContributionRepository.class);
    private final UserRepository             userRepo         = Mockito.mock(UserRepository.class);

    private final SusuGroupQueryService service = new SusuGroupQueryService(
            groupRepo, membershipRepo, roundRepo, contributionRepo, userRepo);

    private static final UUID   CALLER_ID    = UUID.randomUUID();
    private static final UUID   GROUP_ID     = UUID.randomUUID();
    private static final UUID   ORGANISER_ID = UUID.randomUUID();
    private static final UUID   ROUND_ID     = UUID.randomUUID();
    private static final String CORR         = "corr-susu-query-001";

    @BeforeEach
    void setUp() {
        when(membershipRepo.countActiveMembers(any())).thenReturn(3L);
        when(roundRepo.findAllByGroup(any())).thenReturn(List.of());
        when(roundRepo.findByGroupAndRoundNumber(any(), anyInt())).thenReturn(Optional.empty());
        when(contributionRepo.findByRound(any())).thenReturn(List.of());
        when(userRepo.findAllById(any())).thenReturn(List.of());
    }

    // ── List: empty ────────────────────────────────────────────────────────

    @Test
    @DisplayName("user with no memberships returns empty list")
    void empty_list() {
        when(membershipRepo.findActiveMembershipsByUser(CALLER_ID)).thenReturn(List.of());

        List<SusuGroupListItemResponse> result = service.listGroups(CALLER_ID, false, CORR);

        assertThat(result).isEmpty();
        verifyNoInteractions(groupRepo);
    }

    // ── List: multiple groups ──────────────────────────────────────────────

    @Test
    @DisplayName("list returns all active groups, ordered by created_at descending")
    void list_multiple_groups_ordered() {
        UUID groupId1 = UUID.randomUUID();
        UUID groupId2 = UUID.randomUUID();

        SusuGroupEntity group1 = pendingGroup(groupId1, Instant.parse("2026-06-01T00:00:00Z"));
        SusuGroupEntity group2 = pendingGroup(groupId2, Instant.parse("2026-06-10T00:00:00Z"));

        when(membershipRepo.findActiveMembershipsByUser(CALLER_ID)).thenReturn(List.of(
                activeMembership(groupId1), activeMembership(groupId2)));
        when(groupRepo.findAllById(any())).thenReturn(List.of(group1, group2));

        List<SusuGroupListItemResponse> result = service.listGroups(CALLER_ID, false, CORR);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).groupId()).isEqualTo(groupId2);   // newer
        assertThat(result.get(1).groupId()).isEqualTo(groupId1);   // older
    }

    // ── List: inactive exclusion ───────────────────────────────────────────

    @Test
    @DisplayName("CANCELLED and COMPLETED groups excluded by default")
    void inactive_groups_excluded_by_default() {
        UUID cancelledId = UUID.randomUUID();
        UUID completedId = UUID.randomUUID();
        UUID pendingId   = UUID.randomUUID();

        SusuGroupEntity cancelled = groupWithStatus(cancelledId, "CANCELLED");
        SusuGroupEntity completed = groupWithStatus(completedId, "COMPLETED");
        SusuGroupEntity pending   = groupWithStatus(pendingId,   "PENDING");

        when(membershipRepo.findActiveMembershipsByUser(CALLER_ID)).thenReturn(List.of(
                activeMembership(cancelledId), activeMembership(completedId),
                activeMembership(pendingId)));
        when(groupRepo.findAllById(any())).thenReturn(List.of(cancelled, completed, pending));

        List<SusuGroupListItemResponse> result = service.listGroups(CALLER_ID, false, CORR);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).groupId()).isEqualTo(pendingId);
    }

    @Test
    @DisplayName("include_inactive=true returns CANCELLED and COMPLETED groups too")
    void inactive_groups_included_with_flag() {
        UUID cancelledId = UUID.randomUUID();
        UUID pendingId   = UUID.randomUUID();

        when(membershipRepo.findActiveMembershipsByUser(CALLER_ID)).thenReturn(List.of(
                activeMembership(cancelledId), activeMembership(pendingId)));
        when(groupRepo.findAllById(any())).thenReturn(List.of(
                groupWithStatus(cancelledId, "CANCELLED"),
                groupWithStatus(pendingId, "PENDING")));

        List<SusuGroupListItemResponse> result = service.listGroups(CALLER_ID, true, CORR);

        assertThat(result).hasSize(2);
    }

    // ── List: caller flags ─────────────────────────────────────────────────

    @Test
    @DisplayName("is_organiser=true when caller is the organiser")
    void is_organiser_flag_set() {
        SusuGroupEntity group = pendingGroupWithOrganiser(GROUP_ID, CALLER_ID);
        when(membershipRepo.findActiveMembershipsByUser(CALLER_ID))
                .thenReturn(List.of(activeMembership(GROUP_ID)));
        when(groupRepo.findAllById(any())).thenReturn(List.of(group));

        List<SusuGroupListItemResponse> result = service.listGroups(CALLER_ID, false, CORR);

        assertThat(result.get(0).isOrganiser()).isTrue();
    }

    @Test
    @DisplayName("is_organiser=false when caller is not the organiser")
    void is_organiser_false_for_member() {
        SusuGroupEntity group = pendingGroupWithOrganiser(GROUP_ID, UUID.randomUUID());
        when(membershipRepo.findActiveMembershipsByUser(CALLER_ID))
                .thenReturn(List.of(activeMembership(GROUP_ID)));
        when(groupRepo.findAllById(any())).thenReturn(List.of(group));

        List<SusuGroupListItemResponse> result = service.listGroups(CALLER_ID, false, CORR);

        assertThat(result.get(0).isOrganiser()).isFalse();
    }

    // ── Detail: happy path ─────────────────────────────────────────────────

    @Test
    @DisplayName("detail returns full group state for active member")
    void detail_happy_path() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup()));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, CALLER_ID))
                .thenReturn(Optional.of(activeMembershipWithPosition(1)));
        when(membershipRepo.findActiveMembersByGroup(GROUP_ID))
                .thenReturn(List.of(activeMembershipWithPosition(1)));
        when(roundRepo.findByGroupAndRoundNumber(GROUP_ID, 1))
                .thenReturn(Optional.of(collectingRound()));
        when(roundRepo.findAllByGroup(GROUP_ID)).thenReturn(List.of(collectingRound()));
        when(contributionRepo.findByRound(ROUND_ID)).thenReturn(List.of(paidContribution()));
        when(userRepo.findAllById(any())).thenReturn(List.of(user(CALLER_ID, "Akua Mensah")));

        SusuGroupDetailResponse result = service.getGroupDetail(GROUP_ID, CALLER_ID, CORR);

        assertThat(result.id()).isEqualTo(GROUP_ID);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.currentRound()).isNotNull();
        assertThat(result.currentRound().roundNumber()).isEqualTo(1);
        assertThat(result.currentRound().totalRounds()).isEqualTo(1);
        assertThat(result.members()).hasSize(1);
        assertThat(result.callerMembership().rotationPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("detail: contribution statuses included in current round")
    void detail_contributions_in_current_round() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup()));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, CALLER_ID))
                .thenReturn(Optional.of(activeMembershipWithPosition(1)));
        when(membershipRepo.findActiveMembersByGroup(GROUP_ID))
                .thenReturn(List.of(activeMembershipWithPosition(1)));
        when(roundRepo.findByGroupAndRoundNumber(GROUP_ID, 1))
                .thenReturn(Optional.of(collectingRound()));
        when(roundRepo.findAllByGroup(GROUP_ID)).thenReturn(List.of(collectingRound()));
        when(contributionRepo.findByRound(ROUND_ID))
                .thenReturn(List.of(paidContribution(), pendingContribution()));
        when(userRepo.findAllById(any())).thenReturn(List.of());

        SusuGroupDetailResponse result = service.getGroupDetail(GROUP_ID, CALLER_ID, CORR);

        assertThat(result.currentRound().contributions()).hasSize(2);
        assertThat(result.currentRound().contributions())
                .anyMatch(c -> "PAID".equals(c.status()));
        assertThat(result.currentRound().contributions())
                .anyMatch(c -> "PENDING".equals(c.status()));
    }

    @Test
    @DisplayName("detail: PENDING group has null current_round")
    void detail_pending_group_null_round() {
        SusuGroupEntity pending = pendingGroup(GROUP_ID, Instant.parse("2026-06-01T00:00:00Z"));
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pending));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, CALLER_ID))
                .thenReturn(Optional.of(activeMembershipWithPosition(null)));
        when(membershipRepo.findActiveMembersByGroup(GROUP_ID))
                .thenReturn(List.of(activeMembershipWithPosition(null)));
        when(roundRepo.findAllByGroup(GROUP_ID)).thenReturn(List.of());
        when(userRepo.findAllById(any())).thenReturn(List.of());

        SusuGroupDetailResponse result = service.getGroupDetail(GROUP_ID, CALLER_ID, CORR);

        assertThat(result.currentRound()).isNull();
        assertThat(result.callerMembership().rotationPosition()).isNull();
    }

    // ── Detail: access control ─────────────────────────────────────────────

    @Test
    @DisplayName("non-member accessing detail returns 403")
    void non_member_returns_403() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup()));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, CALLER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroupDetail(GROUP_ID, CALLER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("REMOVED member accessing detail returns 403")
    void removed_member_returns_403() {
        SusuMembershipEntity removed = activeMembership(GROUP_ID);
        setField(removed, "status", "REMOVED");
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup()));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, CALLER_ID))
                .thenReturn(Optional.of(removed));

        assertThatThrownBy(() -> service.getGroupDetail(GROUP_ID, CALLER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("group not found returns 404")
    void group_not_found_returns_404() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroupDetail(GROUP_ID, CALLER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    // ── Detail: display names ──────────────────────────────────────────────

    @Test
    @DisplayName("member display_name comes from the user's display_name")
    void display_name_resolved_from_user() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup()));
        when(membershipRepo.findByGroupAndUser(GROUP_ID, CALLER_ID))
                .thenReturn(Optional.of(activeMembershipWithPosition(1)));
        when(membershipRepo.findActiveMembersByGroup(GROUP_ID))
                .thenReturn(List.of(activeMembershipWithPosition(1)));
        when(roundRepo.findAllByGroup(GROUP_ID)).thenReturn(List.of());
        when(userRepo.findAllById(any()))
                .thenReturn(List.of(user(CALLER_ID, "Akua Mensah")));

        SusuGroupDetailResponse result = service.getGroupDetail(GROUP_ID, CALLER_ID, CORR);

        assertThat(result.members().get(0).displayName()).isEqualTo("Akua Mensah");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SusuGroupEntity pendingGroup(UUID id, Instant createdAt) {
        SusuGroupEntity g = SusuGroupEntity.create(
                ORGANISER_ID, "Akua's Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", createdAt);
        setField(g, "id", id);
        return g;
    }

    private SusuGroupEntity pendingGroupWithOrganiser(UUID id, UUID organiser) {
        SusuGroupEntity g = SusuGroupEntity.create(
                organiser, "Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", Instant.parse("2026-06-01T00:00:00Z"));
        setField(g, "id", id);
        return g;
    }

    private SusuGroupEntity activeGroup() {
        SusuGroupEntity g = SusuGroupEntity.create(
                ORGANISER_ID, "Akua's Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", Instant.parse("2026-06-01T00:00:00Z"));
        setField(g, "id", GROUP_ID);
        setField(g, "status", "ACTIVE");
        setField(g, "currentRoundNumber", 1);
        return g;
    }

    private SusuGroupEntity groupWithStatus(UUID id, String status) {
        SusuGroupEntity g = SusuGroupEntity.create(
                ORGANISER_ID, "Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", Instant.parse("2026-06-01T00:00:00Z"));
        setField(g, "id", id);
        setField(g, "status", status);
        return g;
    }

    private SusuMembershipEntity activeMembership(UUID groupId) {
        SusuMembershipEntity m = SusuMembershipEntity.create(
                groupId, CALLER_ID, Instant.parse("2026-06-01T00:00:00Z"));
        setField(m, "id", UUID.randomUUID());
        return m;
    }

    private SusuMembershipEntity activeMembershipWithPosition(Integer position) {
        SusuMembershipEntity m = SusuMembershipEntity.create(
                GROUP_ID, CALLER_ID, Instant.parse("2026-06-01T00:00:00Z"));
        setField(m, "id", UUID.randomUUID());
        setField(m, "rotationPosition", position);
        return m;
    }

    private SusuRoundEntity collectingRound() {
        SusuRoundEntity r = new SusuRoundEntity();
        setField(r, "id", ROUND_ID);
        setField(r, "susuGroupId", GROUP_ID);
        setField(r, "roundNumber", 1);
        setField(r, "recipientUserId", CALLER_ID);
        setField(r, "status", "COLLECTING");
        setField(r, "scheduledCollectionAt", Instant.parse("2026-07-01T00:00:00Z"));
        setField(r, "expectedPotAmount", 120_000L);
        return r;
    }

    private SusuContributionEntity paidContribution() {
        SusuContributionEntity c = new SusuContributionEntity();
        setField(c, "id", UUID.randomUUID());
        setField(c, "susuRoundId", ROUND_ID);
        setField(c, "susuGroupId", GROUP_ID);
        setField(c, "memberUserId", CALLER_ID);
        setField(c, "expectedAmount", 20_000L);
        setField(c, "collectedAmount", 20_000L);
        setField(c, "status", "PAID");
        setField(c, "isLate", false);
        setField(c, "penaltyAmount", 0L);
        setField(c, "paidAt", Instant.parse("2026-06-24T10:00:00Z"));
        setField(c, "createdAt", Instant.parse("2026-06-24T09:00:00Z"));
        return c;
    }

    private SusuContributionEntity pendingContribution() {
        SusuContributionEntity c = new SusuContributionEntity();
        setField(c, "id", UUID.randomUUID());
        setField(c, "susuRoundId", ROUND_ID);
        setField(c, "susuGroupId", GROUP_ID);
        setField(c, "memberUserId", UUID.randomUUID());
        setField(c, "expectedAmount", 20_000L);
        setField(c, "status", "PENDING");
        setField(c, "isLate", false);
        setField(c, "penaltyAmount", 0L);
        setField(c, "createdAt", Instant.parse("2026-06-24T09:00:00Z"));
        return c;
    }

    private User user(UUID id, String displayName) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(displayName);
        u.setEmail("akua@stash.test");
        return u;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
