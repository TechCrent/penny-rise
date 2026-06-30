package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.SusuContributionResponse;
import com.stash.platform.susu.client.SusuContributionTransferClient;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuRoundFullyCollectedEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
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

class SusuContributionServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final SusuRoundRepository            roundRepo        = Mockito.mock(SusuRoundRepository.class);
    private final SusuContributionRepository     contributionRepo = Mockito.mock(SusuContributionRepository.class);
    private final SusuGroupRepository            groupRepo        = Mockito.mock(SusuGroupRepository.class);
    private final SusuMembershipRepository       membershipRepo   = Mockito.mock(SusuMembershipRepository.class);
    private final SusuContributionTransferClient transferClient   = Mockito.mock(SusuContributionTransferClient.class);
    private final ApplicationEventPublisher      eventPublisher   = Mockito.mock(ApplicationEventPublisher.class);
    private final SusuPotIntegrityChecker        integrityChecker = Mockito.mock(SusuPotIntegrityChecker.class);

    private final SusuContributionService service = new SusuContributionService(
            roundRepo, contributionRepo, groupRepo, membershipRepo,
            transferClient, eventPublisher, FIXED_CLOCK, integrityChecker);

    private static final UUID   CALLER_ID  = UUID.randomUUID();
    private static final UUID   GROUP_ID   = UUID.randomUUID();
    private static final UUID   ROUND_ID   = UUID.randomUUID();
    private static final UUID   CONTRIB_ID = UUID.randomUUID();
    private static final UUID   LEDGER_ID  = UUID.randomUUID();  // SUSU_POT
    private static final UUID   WALLET_ID  = UUID.randomUUID();  // USER_WALLET
    private static final String CORR       = "corr-contrib-001";
    private static final String IDEM_KEY   = "idem-contrib-001";
    private static final String TX_REF     = "STSH-202606-CON001";

    @BeforeEach
    void setUp() {
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.of(collectingRound()));
        when(roundRepo.findByIdForUpdate(ROUND_ID)).thenReturn(Optional.of(collectingRound()));
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup()));
        when(membershipRepo.isActiveMember(GROUP_ID, CALLER_ID)).thenReturn(true);
        when(contributionRepo.findByRoundAndMember(ROUND_ID, CALLER_ID))
                .thenReturn(Optional.of(pendingContribution()));
        when(transferClient.resolveUserWallet(CALLER_ID, CORR)).thenReturn(WALLET_ID);
        when(transferClient.transfer(eq(WALLET_ID), eq(LEDGER_ID), eq(20_000L),
                eq(CONTRIB_ID), eq(CORR), eq(IDEM_KEY)))
                .thenReturn(TX_REF);
        when(contributionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roundRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Default: still 1 non-terminal contribution remaining after this one pays
        when(contributionRepo.countNonTerminalContributions(ROUND_ID)).thenReturn(1L);
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("contribution payment: status=PAID, transaction_reference set, paid_at set")
    void happy_contribution_paid() {
        SusuContributionResponse result =
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.transactionReference()).isEqualTo(TX_REF);
        assertThat(result.paidAt()).isEqualTo(Instant.parse("2026-06-24T10:00:00Z"));
        assertThat(result.collectedAmount()).isEqualTo(20_000L);
        assertThat(result.collectedAmountCedis()).isEqualTo("200.00");
    }

    @Test
    @DisplayName("transfer called with correct USER_WALLET → SUSU_POT params")
    void transfer_called_with_correct_accounts() {
        service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        verify(transferClient).transfer(
                eq(WALLET_ID),   // source: USER_WALLET
                eq(LEDGER_ID),   // destination: SUSU_POT
                eq(20_000L),     // amount
                eq(CONTRIB_ID),
                eq(CORR),
                eq(IDEM_KEY)
        );
    }

    @Test
    @DisplayName("contribution row saved with PAID status")
    void contribution_row_saved_as_paid() {
        service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        ArgumentCaptor<SusuContributionEntity> captor =
                ArgumentCaptor.forClass(SusuContributionEntity.class);
        verify(contributionRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PAID");
        assertThat(captor.getValue().getTransactionReference()).isEqualTo(TX_REF);
    }

    // ── Late contribution can still be paid ───────────────────────────────

    @Test
    @DisplayName("LATE contribution can still be paid")
    void late_contribution_can_pay() {
        SusuContributionEntity lateContrib = pendingContribution();
        setField(lateContrib, "status", "LATE");
        when(contributionRepo.findByRoundAndMember(ROUND_ID, CALLER_ID))
                .thenReturn(Optional.of(lateContrib));

        assertThatCode(() ->
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY))
                .doesNotThrowAnyException();
    }

    // ── Already paid ──────────────────────────────────────────────────────

    @Test
    @DisplayName("already PAID contribution returns 409 SUSU_CONTRIBUTION_ALREADY_PAID")
    void already_paid_returns_409() {
        SusuContributionEntity paid = pendingContribution();
        setField(paid, "status", "PAID");
        when(contributionRepo.findByRoundAndMember(ROUND_ID, CALLER_ID))
                .thenReturn(Optional.of(paid));

        assertThatThrownBy(() ->
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_CONTRIBUTION_ALREADY_PAID");
                });
        verifyNoInteractions(transferClient);
    }

    // ── Round not collecting ───────────────────────────────────────────────

    @Test
    @DisplayName("round status=PENDING returns 409 SUSU_ROUND_NOT_COLLECTING")
    void round_not_collecting_returns_409() {
        SusuRoundEntity pending = collectingRound();
        setField(pending, "status", "PENDING");
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() ->
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("SUSU_ROUND_NOT_COLLECTING");
                });
    }

    @Test
    @DisplayName("round status=DISBURSING returns 409")
    void disbursing_round_returns_409() {
        SusuRoundEntity disbursing = collectingRound();
        setField(disbursing, "status", "DISBURSING");
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.of(disbursing));

        assertThatThrownBy(() ->
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    // ── Non-member ────────────────────────────────────────────────────────

    @Test
    @DisplayName("non-member returns 403")
    void non_member_returns_403() {
        when(membershipRepo.isActiveMember(GROUP_ID, CALLER_ID)).thenReturn(false);

        assertThatThrownBy(() ->
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
        verifyNoInteractions(transferClient);
    }

    // ── Insufficient balance ───────────────────────────────────────────────

    @Test
    @DisplayName("Payments 422 PAYMENTS_INSUFFICIENT_BALANCE → 422 SUSU_INSUFFICIENT_BALANCE")
    void insufficient_balance_translated() {
        when(transferClient.transfer(any(), any(), anyLong(), any(), any(), any()))
                .thenThrow(new SusuPaymentsException(
                        "422:{\"code\":\"PAYMENTS_INSUFFICIENT_BALANCE\"}"));

        assertThatThrownBy(() ->
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("SUSU_INSUFFICIENT_BALANCE");
                });
    }

    @Test
    @DisplayName("Payments 5xx returns 502 to caller")
    void payments_5xx_returns_502() {
        when(transferClient.transfer(any(), any(), anyLong(), any(), any(), any()))
                .thenThrow(new SusuPaymentsException("503:gateway timeout"));

        assertThatThrownBy(() ->
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(BAD_GATEWAY));
    }

    // ── Fully collected event — emitted exactly once on final contribution ─

    @Test
    @DisplayName("final contribution: susu.round.fully_collected event published")
    void final_contribution_emits_fully_collected_event() {
        when(contributionRepo.countNonTerminalContributions(ROUND_ID)).thenReturn(0L);
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);

        service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        ArgumentCaptor<SusuRoundFullyCollectedEvent> captor =
                ArgumentCaptor.forClass(SusuRoundFullyCollectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        SusuRoundFullyCollectedEvent event = captor.getValue();
        assertThat(event.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(event.getRoundId()).isEqualTo(ROUND_ID);
        assertThat(event.getActualPotAmount()).isEqualTo(120_000L);
        assertThat(event.getRecipientUserId()).isNotNull();
    }

    @Test
    @DisplayName("final contribution: round status updated to DISBURSING")
    void final_contribution_round_disbursing() {
        when(contributionRepo.countNonTerminalContributions(ROUND_ID)).thenReturn(0L);
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);

        service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        ArgumentCaptor<SusuRoundEntity> roundCaptor =
                ArgumentCaptor.forClass(SusuRoundEntity.class);
        verify(roundRepo).save(roundCaptor.capture());
        assertThat(roundCaptor.getValue().getStatus()).isEqualTo("DISBURSING");
        assertThat(roundCaptor.getValue().getActualPotAmount()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("non-final contribution: no fully_collected event published")
    void non_final_no_event() {
        when(contributionRepo.countNonTerminalContributions(ROUND_ID)).thenReturn(2L);

        service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("response.round_fully_collected=true only when all paid")
    void response_fully_collected_flag() {
        when(contributionRepo.countNonTerminalContributions(ROUND_ID)).thenReturn(0L);
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);

        SusuContributionResponse result =
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        assertThat(result.roundFullyCollected()).isTrue();
    }

    @Test
    @DisplayName("response.round_fully_collected=false when others still PENDING")
    void response_not_fully_collected_flag() {
        when(contributionRepo.countNonTerminalContributions(ROUND_ID)).thenReturn(3L);

        SusuContributionResponse result =
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        assertThat(result.roundFullyCollected()).isFalse();
    }

    // ── Idempotency key forwarded ─────────────────────────────────────────

    @Test
    @DisplayName("idempotency key forwarded to transfer client")
    void idempotency_key_forwarded() {
        service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY);

        verify(transferClient).transfer(any(), any(), anyLong(), any(), any(), eq(IDEM_KEY));
    }

    // ── Round not found ───────────────────────────────────────────────────

    @Test
    @DisplayName("round not found returns 404")
    void round_not_found_returns_404() {
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.payContribution(ROUND_ID, CALLER_ID, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SusuRoundEntity collectingRound() {
        SusuRoundEntity r = newInstance(SusuRoundEntity.class);
        setField(r, "id",                    ROUND_ID);
        setField(r, "susuGroupId",           GROUP_ID);
        setField(r, "roundNumber",           1);
        setField(r, "recipientUserId",       UUID.randomUUID());
        setField(r, "status",                "COLLECTING");
        setField(r, "scheduledCollectionAt", Instant.parse("2026-07-24T00:00:00Z"));
        setField(r, "expectedPotAmount",     120_000L);
        return r;
    }

    private SusuGroupEntity activeGroup() {
        SusuGroupEntity g = SusuGroupEntity.create(
                UUID.randomUUID(), "Akua's Circle", 20_000L,
                "MONTHLY", 6, "STSH1234", Instant.parse("2026-06-20T00:00:00Z"));
        setField(g, "id",              GROUP_ID);
        setField(g, "status",          "ACTIVE");
        setField(g, "ledgerAccountId", LEDGER_ID);
        return g;
    }

    private SusuContributionEntity pendingContribution() {
        SusuContributionEntity c = newInstance(SusuContributionEntity.class);
        setField(c, "id",                     CONTRIB_ID);
        setField(c, "susuRoundId",            ROUND_ID);
        setField(c, "susuGroupId",            GROUP_ID);
        setField(c, "memberUserId",           CALLER_ID);
        setField(c, "expectedAmount",         20_000L);
        setField(c, "status",                 "PENDING");
        setField(c, "collectionAttemptCount", 0);
        setField(c, "penaltyAmount",          0L);
        setField(c, "isLate",                 false);
        setField(c, "createdAt",              Instant.parse("2026-06-24T09:00:00Z"));
        return c;
    }

    private static <T> T newInstance(Class<T> cls) {
        try {
            var ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) { throw new RuntimeException(e); }
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
