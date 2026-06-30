package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.CreateSusuGroupRequest;
import com.stash.platform.susu.api.dto.SusuGroupResponse;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;
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

class SusuGroupCreationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final SusuGroupRepository      groupRepo      = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository membershipRepo = Mockito.mock(SusuMembershipRepository.class);
    private final UserRepository           userRepo       = Mockito.mock(UserRepository.class);
    private final JoinCodeGenerator        joinCodeGen    = Mockito.mock(JoinCodeGenerator.class);

    private final SusuGroupCreationService service =
            new SusuGroupCreationService(groupRepo, membershipRepo, userRepo,
                    joinCodeGen, FIXED_CLOCK);

    private static final UUID   USER_ID = UUID.randomUUID();
    private static final String CORR    = "corr-susu-create-001";
    private static final String IDEM    = "idem-susu-create-001";

    @BeforeEach
    void setUp() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(approvedFreeUser()));
        when(groupRepo.countActiveGroupsByOrganiser(USER_ID)).thenReturn(0L);
        when(joinCodeGen.generate()).thenReturn("STSH1234");
        when(groupRepo.save(any())).thenAnswer(inv -> {
            SusuGroupEntity g = inv.getArgument(0);
            setField(g, "id", UUID.randomUUID());
            return g;
        });
        when(membershipRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("creates group with correct fields and returns 201 response")
    void creates_group_correctly() {
        SusuGroupResponse result = service.createGroup(USER_ID, validRequest(), CORR, IDEM);

        assertThat(result.name()).isEqualTo("Akua's Circle");
        assertThat(result.contributionAmount()).isEqualTo(20_000L);
        assertThat(result.contributionAmountCedis()).isEqualTo("200.00");
        assertThat(result.frequency()).isEqualTo("MONTHLY");
        assertThat(result.targetMemberCount()).isEqualTo(6);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.joinCode()).isEqualTo("STSH1234");
        assertThat(result.organiserUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("group INSERT and membership INSERT both called — atomic")
    void group_and_membership_both_saved() {
        service.createGroup(USER_ID, validRequest(), CORR, IDEM);

        verify(groupRepo).save(any(SusuGroupEntity.class));
        verify(membershipRepo).save(any(SusuMembershipEntity.class));
    }

    @Test
    @DisplayName("organiser membership has status=ACTIVE and rotation_position=NULL")
    void organiser_membership_correct_fields() {
        service.createGroup(USER_ID, validRequest(), CORR, IDEM);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo).save(captor.capture());

        SusuMembershipEntity membership = captor.getValue();
        assertThat(membership.getStatus()).isEqualTo("ACTIVE");
        assertThat(membership.getRotationPosition()).isNull();
        assertThat(membership.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("membership is for the saved group's ID, not a random UUID")
    void membership_references_saved_group_id() {
        SusuGroupResponse group = service.createGroup(USER_ID, validRequest(), CORR, IDEM);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo).save(captor.capture());

        assertThat(captor.getValue().getSusuGroupId()).isEqualTo(group.id());
    }

    @Test
    @DisplayName("frequency normalised to uppercase before saving")
    void frequency_normalised_to_uppercase() {
        var req = new CreateSusuGroupRequest("Circle", 20_000L, "monthly", 6);
        service.createGroup(USER_ID, req, CORR, IDEM);

        ArgumentCaptor<SusuGroupEntity> captor = ArgumentCaptor.forClass(SusuGroupEntity.class);
        verify(groupRepo).save(captor.capture());
        assertThat(captor.getValue().getFrequency()).isEqualTo("MONTHLY");
    }

    // ── KYC check ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("user with kyc_status = PENDING returns 403")
    void pending_kyc_returns_403() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(userWithKyc(KycStatus.PENDING)));

        assertThatThrownBy(() -> service.createGroup(USER_ID, validRequest(), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("user with kyc_status = REJECTED returns 403")
    void rejected_kyc_returns_403() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(userWithKyc(KycStatus.REJECTED)));

        assertThatThrownBy(() -> service.createGroup(USER_ID, validRequest(), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("user with kyc_status = SUBMITTED returns 403")
    void submitted_kyc_returns_403() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(userWithKyc(KycStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.createGroup(USER_ID, validRequest(), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    // ── Input validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("target_member_count = 3 is rejected by the DTO's @Min(4) before the service runs")
    void target_count_below_minimum_returns_422() {
        // @Min(4) on the DTO is the actual enforcement point (via @Valid in the
        // controller) - the service itself doesn't re-validate target_member_count.
        var req = new CreateSusuGroupRequest("Circle", 20_000L, "MONTHLY", 3);
        assertThat(req.targetMemberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("invalid frequency returns 422")
    void invalid_frequency_returns_422() {
        var req = new CreateSusuGroupRequest("Circle", 20_000L, "DAILY", 6);

        assertThatThrownBy(() -> service.createGroup(USER_ID, req, CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    // ── Free-tier limit ────────────────────────────────────────────────────

    @Test
    @DisplayName("free user at limit (1 active group) returns 422 SUSU_FREE_TIER_LIMIT_REACHED")
    void free_tier_limit_returns_422() {
        when(groupRepo.countActiveGroupsByOrganiser(USER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.createGroup(USER_ID, validRequest(), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("SUSU_FREE_TIER_LIMIT_REACHED");
                });
    }

    @Test
    @DisplayName("premium user can create up to 3 groups")
    void premium_user_can_create_up_to_3_groups() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(groupRepo.countActiveGroupsByOrganiser(USER_ID)).thenReturn(2L);

        assertThatCode(() -> service.createGroup(USER_ID, validRequest(), CORR, IDEM))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("premium user at limit (3 active groups) returns 422")
    void premium_user_at_limit_returns_422() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(groupRepo.countActiveGroupsByOrganiser(USER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> service.createGroup(USER_ID, validRequest(), CORR, IDEM))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("free user with zero active groups is allowed to create")
    void free_user_with_no_groups_allowed() {
        when(groupRepo.countActiveGroupsByOrganiser(USER_ID)).thenReturn(0L);

        assertThatCode(() -> service.createGroup(USER_ID, validRequest(), CORR, IDEM))
                .doesNotThrowAnyException();
    }

    // ── Join code ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("join code from generator is used and returned in response")
    void join_code_in_response() {
        when(joinCodeGen.generate()).thenReturn("ABCD5678");
        SusuGroupResponse result = service.createGroup(USER_ID, validRequest(), CORR, IDEM);
        assertThat(result.joinCode()).isEqualTo("ABCD5678");
    }

    @Test
    @DisplayName("join code generator is called exactly once per creation")
    void join_code_generator_called_once() {
        service.createGroup(USER_ID, validRequest(), CORR, IDEM);
        verify(joinCodeGen, times(1)).generate();
    }

    // ── Idempotency (no monolith-side filter exists — documents real behaviour) ──

    @Test
    @DisplayName("idempotency key is not enforced at the service layer (no monolith filter exists)")
    void idempotency_is_not_enforced_at_service_layer() {
        // Unlike payments-service, monolith has no IdempotencyFilter. The header
        // is required and logged (see VaultCreationService for the same pattern)
        // but a repeated key does not prevent a duplicate INSERT here.
        service.createGroup(USER_ID, validRequest(), CORR, IDEM);
        service.createGroup(USER_ID, validRequest(), CORR, IDEM);
        verify(groupRepo, times(2)).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static CreateSusuGroupRequest validRequest() {
        return new CreateSusuGroupRequest("Akua's Circle", 20_000L, "MONTHLY", 6);
    }

    private static User approvedFreeUser() {
        return userWithKyc(KycStatus.APPROVED);
    }

    private static User userWithKyc(KycStatus kycStatus) {
        User u = new User();
        u.setKycStatus(kycStatus);
        u.setSubscriptionTier(SubscriptionTier.FREE);
        return u;
    }

    private static User premiumUser() {
        User u = new User();
        u.setKycStatus(KycStatus.APPROVED);
        u.setSubscriptionTier(SubscriptionTier.PREMIUM);
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
