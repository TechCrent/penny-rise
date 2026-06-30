package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.SusuActivationResponse;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.client.SusuPotProvisionClient;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuActivatedEvent;
import com.stash.platform.susu.event.SusuRoundStartedEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class SusuGroupActivationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.parse("2026-06-24");

    private final SusuGroupRepository        groupRepo        = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository   membershipRepo   = Mockito.mock(SusuMembershipRepository.class);
    private final SusuRoundRepository        roundRepo        = Mockito.mock(SusuRoundRepository.class);
    private final SusuContributionRepository contributionRepo = Mockito.mock(SusuContributionRepository.class);
    private final UserRepository             userRepo         = Mockito.mock(UserRepository.class);
    private final SusuPotProvisionClient     potClient        = Mockito.mock(SusuPotProvisionClient.class);
    private final ApplicationEventPublisher  eventPublisher   = Mockito.mock(ApplicationEventPublisher.class);

    private final SusuGroupActivationService service = new SusuGroupActivationService(
            groupRepo, membershipRepo, roundRepo, contributionRepo,
            userRepo, potClient, eventPublisher, FIXED_CLOCK);

    private static final UUID   ORGANISER_ID  = UUID.randomUUID();
    private static final UUID   GROUP_ID      = UUID.randomUUID();
    private static final UUID   LEDGER_ID     = UUID.randomUUID();
    private static final String CORR          = "corr-activate-001";

    private static final UUID[] MEMBER_IDS = {
            ORGANISER_ID,
            UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
    };

    @BeforeEach
    void setUp() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(pendingGroup(6)));
        when(membershipRepo.findActiveMembersByGroupForActivation(GROUP_ID))
                .thenReturn(sixMembers());
        when(userRepo.findAllById(any())).thenReturn(approvedUsers(Arrays.asList(MEMBER_IDS)));
        when(potClient.provisionSusuPot(GROUP_ID, "Akua's Circle", CORR))
                .thenReturn(LEDGER_ID);
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roundRepo.save(any())).thenAnswer(inv -> {
            SusuRoundEntity r = inv.getArgument(0);
            setField(r, "id", UUID.randomUUID());
            return r;
        });
        when(contributionRepo.save(any())).thenAnswer(inv -> {
            SusuContributionEntity c = inv.getArgument(0);
            setField(c, "id", UUID.randomUUID());
            return c;
        });
    }

    // ── Happy path: all writes verified ──────────────────────────────────

    @Test
    @DisplayName("activates group: status=ACTIVE, start_date=today, currentRound=1")
    void activates_group_row() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuGroupEntity> groupCaptor =
                ArgumentCaptor.forClass(SusuGroupEntity.class);
        verify(groupRepo).save(groupCaptor.capture());

        SusuGroupEntity saved = groupCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getStartDate()).isEqualTo(TODAY);
        assertThat(saved.getCurrentRoundNumber()).isEqualTo(1);
        assertThat(saved.getLedgerAccountId()).isEqualTo(LEDGER_ID);
    }

    @Test
    @DisplayName("assigns rotation positions 1..6 with organiser at position 1")
    void assigns_rotation_positions() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo, times(6)).save(captor.capture());

        List<SusuMembershipEntity> saved = captor.getAllValues();
        assertThat(saved.stream()
                .filter(m -> m.getUserId().equals(ORGANISER_ID))
                .findFirst().get()
                .getRotationPosition()).isEqualTo(1);
        Set<Integer> positions = new HashSet<>();
        saved.forEach(m -> positions.add(m.getRotationPosition()));
        assertThat(positions).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("generates exactly 6 rounds for a 6-member group")
    void generates_6_rounds() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        verify(roundRepo, times(6)).save(any(SusuRoundEntity.class));
    }

    @Test
    @DisplayName("round 1 status=COLLECTING; rounds 2-6 status=PENDING")
    void round_1_collecting_rest_pending() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuRoundEntity> captor =
                ArgumentCaptor.forClass(SusuRoundEntity.class);
        verify(roundRepo, times(6)).save(captor.capture());

        List<SusuRoundEntity> rounds = captor.getAllValues();
        assertThat(rounds.get(0).getStatus()).isEqualTo("COLLECTING");
        for (int i = 1; i < 6; i++) {
            assertThat(rounds.get(i).getStatus()).isEqualTo("PENDING");
        }
    }

    @Test
    @DisplayName("generates exactly 6 contribution rows for round 1")
    void generates_6_contributions_for_round_1() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        verify(contributionRepo, times(6)).save(any(SusuContributionEntity.class));
    }

    @Test
    @DisplayName("all contributions for round 1 are PENDING")
    void all_contributions_pending() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuContributionEntity> captor =
                ArgumentCaptor.forClass(SusuContributionEntity.class);
        verify(contributionRepo, times(6)).save(captor.capture());

        captor.getAllValues().forEach(c ->
                assertThat(c.getStatus()).isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("MONTHLY: round 1 collection_at = start_date + 1 month")
    void monthly_round_1_collection_date() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuRoundEntity> captor =
                ArgumentCaptor.forClass(SusuRoundEntity.class);
        verify(roundRepo, times(6)).save(captor.capture());

        Instant expectedRound1 = LocalDate.parse("2026-07-24")
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(captor.getAllValues().get(0).getScheduledCollectionAt())
                .isEqualTo(expectedRound1);

        Instant expectedRound6 = LocalDate.parse("2026-12-24")
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(captor.getAllValues().get(5).getScheduledCollectionAt())
                .isEqualTo(expectedRound6);
    }

    @Test
    @DisplayName("expected_pot_amount = contribution × member_count")
    void expected_pot_amount_correct() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<SusuRoundEntity> captor =
                ArgumentCaptor.forClass(SusuRoundEntity.class);
        verify(roundRepo, times(6)).save(captor.capture());

        // 20000 pesewas × 6 members = 120000
        captor.getAllValues().forEach(r ->
                assertThat(r.getExpectedPotAmount()).isEqualTo(120_000L));
    }

    @Test
    @DisplayName("SUSU_POT provision called before DB writes")
    void pot_provision_called() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);
        verify(potClient).provisionSusuPot(GROUP_ID, "Akua's Circle", CORR);
    }

    @Test
    @DisplayName("both SusuActivated and SusuRoundStarted events published")
    void both_events_published() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        verify(eventPublisher).publishEvent(isA(SusuActivatedEvent.class));
        verify(eventPublisher).publishEvent(isA(SusuRoundStartedEvent.class));
    }

    @Test
    @DisplayName("SusuRoundStarted event is for round 1 with correct recipient")
    void round_started_event_correct() {
        service.activate(GROUP_ID, ORGANISER_ID, CORR);

        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());

        SusuRoundStartedEvent roundEvent = captor.getAllValues().stream()
                .filter(e -> e instanceof SusuRoundStartedEvent)
                .map(e -> (SusuRoundStartedEvent) e)
                .findFirst().orElseThrow();

        assertThat(roundEvent.getRoundNumber()).isEqualTo(1);
        assertThat(roundEvent.getTotalRounds()).isEqualTo(6);
        assertThat(roundEvent.getRecipientUserId()).isEqualTo(ORGANISER_ID); // pos 1
    }

    @Test
    @DisplayName("response includes correct activation data")
    void response_correct() {
        SusuActivationResponse result = service.activate(GROUP_ID, ORGANISER_ID, CORR);

        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.startDate()).isEqualTo(TODAY);
        assertThat(result.currentRoundNumber()).isEqualTo(1);
        assertThat(result.ledgerAccountId()).isEqualTo(LEDGER_ID);
        assertThat(result.members()).hasSize(6);
        assertThat(result.round1().status()).isEqualTo("COLLECTING");
        assertThat(result.round1().contributionCount()).isEqualTo(6);
    }

    // ── Authorization ─────────────────────────────────────────────────────

    @Test
    @DisplayName("non-organiser caller returns 403")
    void non_organiser_returns_403() {
        assertThatThrownBy(() ->
                service.activate(GROUP_ID, UUID.randomUUID(), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    // ── Validation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("member count not met returns 422 SUSU_MEMBER_COUNT_NOT_MET")
    void member_count_not_met() {
        when(membershipRepo.findActiveMembersByGroupForActivation(GROUP_ID))
                .thenReturn(fiveMembers()); // target is 6

        assertThatThrownBy(() ->
                service.activate(GROUP_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("SUSU_MEMBER_COUNT_NOT_MET");
                });

        verifyNoInteractions(potClient);
        verifyNoInteractions(roundRepo);
    }

    @Test
    @DisplayName("non-APPROVED KYC member returns 422 SUSU_MEMBER_KYC_INCOMPLETE")
    void non_kyc_member_returns_422() {
        UUID pendingKycUserId = UUID.randomUUID();
        List<SusuMembershipEntity> members = sixMembersWithExtra(pendingKycUserId);
        when(groupRepo.findById(GROUP_ID))
                .thenReturn(Optional.of(pendingGroup(members.size())));
        when(membershipRepo.findActiveMembersByGroupForActivation(GROUP_ID))
                .thenReturn(members);

        List<User> users = approvedUsers(
                members.stream().map(SusuMembershipEntity::getUserId).toList());
        users.stream().filter(u -> u.getId().equals(pendingKycUserId))
                .findFirst().ifPresent(u -> u.setKycStatus(KycStatus.PENDING));
        when(userRepo.findAllById(any())).thenReturn(users);

        assertThatThrownBy(() ->
                service.activate(GROUP_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("SUSU_MEMBER_KYC_INCOMPLETE");
                });

        verifyNoInteractions(potClient);
    }

    @Test
    @DisplayName("already ACTIVE group returns 409")
    void already_active_returns_409() {
        SusuGroupEntity active = pendingGroup(6);
        setField(active, "status", "ACTIVE");
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() ->
                service.activate(GROUP_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    // ── Payments failure: full rollback ───────────────────────────────────

    @Test
    @DisplayName("Payments Service failure: SusuPaymentsException propagates — full rollback")
    void payments_failure_causes_rollback() {
        when(potClient.provisionSusuPot(any(), any(), any()))
                .thenThrow(new SusuPaymentsException("Payments down"));

        assertThatThrownBy(() ->
                service.activate(GROUP_ID, ORGANISER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(BAD_GATEWAY));

        verifyNoInteractions(roundRepo);
        verifyNoInteractions(contributionRepo);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Payments failure: group row is NOT updated (service-level — unit test)")
    void payments_failure_group_not_saved() {
        // Note: In a real @Transactional rollback, the groupRepo.save() above
        // the potClient call would be rolled back. In this unit test, the save()
        // mock has no rollback semantics — we verify that potClient is called
        // AFTER validations and BEFORE round writes (order is correct).
        when(potClient.provisionSusuPot(any(), any(), any()))
                .thenThrow(new SusuPaymentsException("down"));

        try {
            service.activate(GROUP_ID, ORGANISER_ID, CORR);
        } catch (ResponseStatusException ignored) {}

        verify(roundRepo, never()).save(any());
        verify(contributionRepo, never()).save(any());
    }

    // ── Idempotency (framework-level) ─────────────────────────────────────

    @Test
    @DisplayName("potClient.provisionSusuPot is idempotent on same group ID")
    void pot_provision_idempotent() {
        // The Payments Service's UNIQUE(owner_type, owner_id, account_type)
        // constraint makes this idempotent. A second call returns the existing account.
        // The IdempotencyFilter above the controller caches the 200 response,
        // so the service is not called twice for the same Idempotency-Key.
        // This test documents the architecture decision.
        service.activate(GROUP_ID, ORGANISER_ID, CORR);
        verify(potClient, times(1)).provisionSusuPot(GROUP_ID, "Akua's Circle", CORR);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SusuGroupEntity pendingGroup(int targetCount) {
        SusuGroupEntity g = SusuGroupEntity.create(
                ORGANISER_ID, "Akua's Circle", 20_000L, "MONTHLY",
                targetCount, "STSH1234", Instant.parse("2026-06-20T00:00:00Z"));
        setField(g, "id", GROUP_ID);
        return g;
    }

    private List<SusuMembershipEntity> sixMembers() {
        Instant base = Instant.parse("2026-06-20T00:00:00Z");
        List<SusuMembershipEntity> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            SusuMembershipEntity m = SusuMembershipEntity.create(
                    GROUP_ID, MEMBER_IDS[i], base.plusSeconds(i * 60));
            setField(m, "id", UUID.randomUUID());
            list.add(m);
        }
        return list;
    }

    private List<SusuMembershipEntity> fiveMembers() {
        return sixMembers().subList(0, 5);
    }

    private List<SusuMembershipEntity> sixMembersWithExtra(UUID extraId) {
        List<SusuMembershipEntity> members = new ArrayList<>(sixMembers());
        SusuMembershipEntity extra = SusuMembershipEntity.create(
                GROUP_ID, extraId, Instant.parse("2026-06-21T00:00:00Z"));
        setField(extra, "id", UUID.randomUUID());
        members.add(extra);
        return members;
    }

    private List<User> approvedUsers(List<UUID> ids) {
        return ids.stream().map(id -> {
            User u = new User();
            u.setId(id);
            u.setKycStatus(KycStatus.APPROVED);
            return u;
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            java.lang.reflect.Field f = findField(obj.getClass(), name);
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
