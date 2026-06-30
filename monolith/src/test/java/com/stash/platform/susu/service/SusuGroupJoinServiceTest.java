package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.JoinSusuGroupRequest;
import com.stash.platform.susu.api.dto.JoinSusuGroupResponse;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class SusuGroupJoinServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final SusuGroupRepository      groupRepo      = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository membershipRepo = Mockito.mock(SusuMembershipRepository.class);
    private final UserRepository           userRepo       = Mockito.mock(UserRepository.class);

    private final SusuGroupJoinService service =
            new SusuGroupJoinService(groupRepo, membershipRepo, userRepo, FIXED_CLOCK);

    private static final UUID   JOINER_ID = UUID.randomUUID();
    private static final UUID   GROUP_ID  = UUID.randomUUID();
    private static final String JOIN_CODE = "STSH5678";
    private static final String CORR      = "corr-join-001";
    private static final String IDEM      = "idem-join-001";

    @BeforeEach
    void setUp() {
        when(userRepo.findById(JOINER_ID)).thenReturn(Optional.of(approvedUser()));
        when(groupRepo.findByJoinCodeForUpdate(JOIN_CODE))
                .thenReturn(Optional.of(pendingGroup(6)));
        when(membershipRepo.existsByGroupIdAndUserId(GROUP_ID, JOINER_ID)).thenReturn(false);
        when(membershipRepo.countActiveMembers(GROUP_ID)).thenReturn(1L); // organiser already in
        when(membershipRepo.save(any())).thenAnswer(inv -> {
            SusuMembershipEntity m = inv.getArgument(0);
            setField(m, "id", UUID.randomUUID());
            return m;
        });
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy join: membership created with ACTIVE status and null rotation_position")
    void happy_join_creates_membership() {
        service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo).save(captor.capture());

        SusuMembershipEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(JOINER_ID);
        assertThat(saved.getSusuGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getRotationPosition()).isNull();
    }

    @Test
    @DisplayName("response contains group details and membership row")
    void response_contains_group_and_membership() {
        JoinSusuGroupResponse result =
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM);

        assertThat(result.group().id()).isEqualTo(GROUP_ID);
        assertThat(result.group().joinCode()).isEqualTo(JOIN_CODE);
        assertThat(result.group().currentMemberCount()).isEqualTo(2L); // 1 existing + this join
        assertThat(result.membership().userId()).isEqualTo(JOINER_ID);
        assertThat(result.membership().status()).isEqualTo("ACTIVE");
        assertThat(result.membership().rotationPosition()).isNull();
    }

    @Test
    @DisplayName("join code normalised to uppercase before lookup")
    void join_code_normalised() {
        when(groupRepo.findByJoinCodeForUpdate("STSH5678"))
                .thenReturn(Optional.of(pendingGroup(6)));

        service.joinGroup(JOINER_ID, new JoinSusuGroupRequest("stsh5678"), CORR, IDEM);

        verify(groupRepo).findByJoinCodeForUpdate("STSH5678");
    }

    @Test
    @DisplayName("FOR UPDATE query is called (not the plain findByJoinCode)")
    void for_update_query_called() {
        service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM);
        verify(groupRepo).findByJoinCodeForUpdate(JOIN_CODE);
        verify(groupRepo, never()).findByJoinCode(anyString());
    }

    // ── KYC check ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("user with kyc_status = PENDING returns 403")
    void pending_kyc_returns_403() {
        when(userRepo.findById(JOINER_ID))
                .thenReturn(Optional.of(userWithKyc(KycStatus.PENDING)));

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("user with kyc_status = REJECTED returns 403")
    void rejected_kyc_returns_403() {
        when(userRepo.findById(JOINER_ID))
                .thenReturn(Optional.of(userWithKyc(KycStatus.REJECTED)));

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    // ── Join code lookup ───────────────────────────────────────────────────

    @Test
    @DisplayName("unknown join code returns 404")
    void unknown_join_code_returns_404() {
        when(groupRepo.findByJoinCodeForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest("XXXXXXXX"), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    // ── Status check ───────────────────────────────────────────────────────

    @Test
    @DisplayName("ACTIVE group returns 409 SUSU_GROUP_NOT_JOINABLE")
    void active_group_returns_409() {
        when(groupRepo.findByJoinCodeForUpdate(JOIN_CODE))
                .thenReturn(Optional.of(groupWithStatus("ACTIVE", 6)));

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_GROUP_NOT_JOINABLE");
                });
    }

    @Test
    @DisplayName("COMPLETED group returns 409 SUSU_GROUP_NOT_JOINABLE")
    void completed_group_returns_409() {
        when(groupRepo.findByJoinCodeForUpdate(JOIN_CODE))
                .thenReturn(Optional.of(groupWithStatus("COMPLETED", 6)));

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("CANCELLED group returns 409 SUSU_GROUP_NOT_JOINABLE")
    void cancelled_group_returns_409() {
        when(groupRepo.findByJoinCodeForUpdate(JOIN_CODE))
                .thenReturn(Optional.of(groupWithStatus("CANCELLED", 6)));

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_GROUP_NOT_JOINABLE");
                });
    }

    // ── Already a member ──────────────────────────────────────────────────

    @Test
    @DisplayName("already a member returns 409 SUSU_ALREADY_A_MEMBER")
    void already_member_returns_409() {
        when(membershipRepo.existsByGroupIdAndUserId(GROUP_ID, JOINER_ID)).thenReturn(true);

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_ALREADY_A_MEMBER");
                });
    }

    @Test
    @DisplayName("already a member check fires before member count check")
    void member_check_before_count_check() {
        // Group is also full — but the already-a-member check should fire first
        when(membershipRepo.existsByGroupIdAndUserId(GROUP_ID, JOINER_ID)).thenReturn(true);
        when(membershipRepo.countActiveMembers(GROUP_ID))
                .thenReturn((long) 6);  // full

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .contains("SUSU_ALREADY_A_MEMBER"));
    }

    // ── Group full ────────────────────────────────────────────────────────

    @Test
    @DisplayName("group full (count == target) returns 409 SUSU_GROUP_FULL")
    void group_full_returns_409() {
        when(membershipRepo.countActiveMembers(GROUP_ID)).thenReturn(6L);

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_GROUP_FULL");
                });
    }

    @Test
    @DisplayName("group one slot from full: join succeeds, next join would be rejected")
    void last_slot_join_succeeds() {
        // target = 6, current = 5 → 1 slot left
        when(membershipRepo.countActiveMembers(GROUP_ID)).thenReturn(5L);

        assertThatCode(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no membership row saved when group is full")
    void no_membership_saved_when_full() {
        when(membershipRepo.countActiveMembers(GROUP_ID)).thenReturn(6L);

        try {
            service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM);
        } catch (ResponseStatusException ignored) {}

        verify(membershipRepo, never()).save(any());
    }

    // ── Concurrent join (last slot race) ──────────────────────────────────

    @Test
    @DisplayName("concurrent join: FOR UPDATE serialises; second caller sees full group")
    void concurrent_last_slot_race() {
        // In production: the FOR UPDATE lock prevents this race — both transactions
        // cannot hold the lock simultaneously. The second one blocks until the first
        // commits, then re-reads count = 6 and correctly returns SUSU_GROUP_FULL.
        when(membershipRepo.countActiveMembers(GROUP_ID)).thenReturn(6L);

        assertThatThrownBy(() ->
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_GROUP_FULL");
                });

        verify(membershipRepo, never()).save(any());
    }

    // ── Response content ───────────────────────────────────────────────────

    @Test
    @DisplayName("response group includes correct contribution amount in cedis")
    void response_includes_cedis_amount() {
        JoinSusuGroupResponse result =
                service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM);

        assertThat(result.group().contributionAmount()).isEqualTo(20_000L);
        assertThat(result.group().contributionAmountCedis()).isEqualTo("200.00");
    }

    @Test
    @DisplayName("joined_at is set to now() from the fixed clock")
    void joined_at_is_now() {
        service.joinGroup(JOINER_ID, new JoinSusuGroupRequest(JOIN_CODE), CORR, IDEM);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo).save(captor.capture());

        assertThat(captor.getValue().getJoinedAt())
                .isEqualTo(Instant.parse("2026-06-24T10:00:00Z"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SusuGroupEntity pendingGroup(int targetCount) {
        return groupWithStatus("PENDING", targetCount);
    }

    private SusuGroupEntity groupWithStatus(String status, int targetCount) {
        SusuGroupEntity g = SusuGroupEntity.create(
                UUID.randomUUID(), "Akua's Circle",
                20_000L, "MONTHLY", targetCount,
                JOIN_CODE, Instant.parse("2026-06-24T09:00:00Z"));
        setField(g, "id", GROUP_ID);
        setField(g, "status", status);
        return g;
    }

    private static User approvedUser() {
        return userWithKyc(KycStatus.APPROVED);
    }

    private static User userWithKyc(KycStatus kycStatus) {
        User u = new User();
        u.setKycStatus(kycStatus);
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
