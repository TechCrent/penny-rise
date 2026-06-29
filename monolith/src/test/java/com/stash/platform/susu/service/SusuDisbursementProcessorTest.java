package com.stash.platform.susu.service;

import com.stash.platform.susu.client.SusuContributionTransferClient;
import com.stash.platform.susu.client.SusuContributionTransferClient.DisbursementResult;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuGroupCompletedEvent;
import com.stash.platform.susu.event.SusuRoundCompletedEvent;
import com.stash.platform.susu.event.SusuRoundStartedEvent;
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

class SusuDisbursementProcessorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);

    private final SusuGroupRepository             groupRepo        = Mockito.mock(SusuGroupRepository.class);
    private final SusuRoundRepository             roundRepo        = Mockito.mock(SusuRoundRepository.class);
    private final SusuContributionRepository      contributionRepo = Mockito.mock(SusuContributionRepository.class);
    private final SusuMembershipRepository        membershipRepo   = Mockito.mock(SusuMembershipRepository.class);
    private final SusuContributionTransferClient  transferClient   = Mockito.mock(SusuContributionTransferClient.class);
    private final ApplicationEventPublisher       eventPublisher   = Mockito.mock(ApplicationEventPublisher.class);

    private final SusuDisbursementProcessor processor = new SusuDisbursementProcessor(
            groupRepo, roundRepo, contributionRepo, membershipRepo,
            transferClient, eventPublisher, FIXED_CLOCK);

    private static final UUID   GROUP_ID     = UUID.randomUUID();
    private static final UUID   ROUND_1_ID   = UUID.randomUUID();
    private static final UUID   ROUND_2_ID   = UUID.randomUUID();
    private static final UUID   SUSU_POT_ID  = UUID.randomUUID();
    private static final UUID   RECIPIENT_ID = UUID.randomUUID();
    private static final UUID   WALLET_ID    = UUID.randomUUID();
    private static final UUID   LEDGER_TXN_ID = UUID.randomUUID();
    private static final String CORR         = "corr-disb-001";

    @BeforeEach
    void setUp() {
        when(roundRepo.findByIdForUpdate(ROUND_1_ID))
                .thenReturn(Optional.of(disbursingRound1()));
        when(groupRepo.findById(GROUP_ID))
                .thenReturn(Optional.of(activeGroup()));
        when(transferClient.resolveUserWallet(RECIPIENT_ID, CORR))
                .thenReturn(WALLET_ID);
        when(transferClient.disburse(eq(SUSU_POT_ID), eq(WALLET_ID),
                eq(120_000L), eq(ROUND_1_ID), eq(CORR),
                eq("susu-disbursement-" + ROUND_1_ID)))
                .thenReturn(new DisbursementResult("STSH-202607-DISB001", LEDGER_TXN_ID));
        when(roundRepo.findAllByGroup(GROUP_ID))
                .thenReturn(List.of(round1(), round2()));
        when(roundRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contributionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepo.findActiveMembersByGroup(GROUP_ID))
                .thenReturn(List.of(
                        membership(RECIPIENT_ID),
                        membership(UUID.randomUUID())));
    }

    // ── Happy disbursement ────────────────────────────────────────────────

    @Test
    @DisplayName("happy disbursement: transfer called with SUSU_POT -> recipient wallet")
    void happy_disbursement_transfer_called() {
        processor.process(ROUND_1_ID, CORR);

        verify(transferClient).disburse(
                eq(SUSU_POT_ID), eq(WALLET_ID), eq(120_000L),
                eq(ROUND_1_ID), eq(CORR),
                eq("susu-disbursement-" + ROUND_1_ID)
        );
    }

    @Test
    @DisplayName("round saved at least twice (DISBURSED then COMPLETED), final state is COMPLETED with the real ledger_transaction_id")
    void round_completed() {
        processor.process(ROUND_1_ID, CORR);

        // round is the same mutable object across both saves, so by inspection
        // time every captured reference reflects its FINAL state — the
        // intermediate DISBURSED snapshot isn't observable this way. Asserting
        // call count proves both transitions happened; asserting the final
        // state proves it landed correctly.
        ArgumentCaptor<SusuRoundEntity> captor = ArgumentCaptor.forClass(SusuRoundEntity.class);
        verify(roundRepo, atLeast(2)).save(captor.capture());

        List<SusuRoundEntity> saves = captor.getAllValues();
        assertThat(saves).anyMatch(r ->
                ROUND_1_ID.equals(r.getId())
                        && "COMPLETED".equals(r.getStatus())
                        && LEDGER_TXN_ID.equals(r.getDisbursementTransactionId()));
    }

    @Test
    @DisplayName("SusuRoundCompleted event published")
    void round_completed_event_published() {
        processor.process(ROUND_1_ID, CORR);
        verify(eventPublisher).publishEvent(isA(SusuRoundCompletedEvent.class));
    }

    // ── Next round opened ─────────────────────────────────────────────────

    @Test
    @DisplayName("next round (round 2) opened to COLLECTING")
    void next_round_opened_to_collecting() {
        processor.process(ROUND_1_ID, CORR);

        ArgumentCaptor<SusuRoundEntity> captor = ArgumentCaptor.forClass(SusuRoundEntity.class);
        verify(roundRepo, atLeast(3)).save(captor.capture());

        assertThat(captor.getAllValues()).anyMatch(r -> "COLLECTING".equals(r.getStatus()));
    }

    @Test
    @DisplayName("contribution rows generated for all members in next round")
    void contributions_generated_for_next_round() {
        processor.process(ROUND_1_ID, CORR);

        // 2 active members x 1 round = 2 contribution INSERTs
        verify(contributionRepo, times(2)).save(any(SusuContributionEntity.class));
    }

    @Test
    @DisplayName("SusuRoundStarted event emitted for next round")
    void round_started_event_for_next_round() {
        processor.process(ROUND_1_ID, CORR);
        verify(eventPublisher).publishEvent(isA(SusuRoundStartedEvent.class));
    }

    @Test
    @DisplayName("group current_round_number advanced to 2")
    void group_current_round_advanced() {
        processor.process(ROUND_1_ID, CORR);

        ArgumentCaptor<SusuGroupEntity> captor = ArgumentCaptor.forClass(SusuGroupEntity.class);
        verify(groupRepo, atLeast(1)).save(captor.capture());

        assertThat(captor.getAllValues())
                .anyMatch(g -> g.getCurrentRoundNumber() != null && g.getCurrentRoundNumber() == 2);
    }

    // ── Final round completes group ───────────────────────────────────────

    @Test
    @DisplayName("final round: group transitions to COMPLETED")
    void final_round_group_completed() {
        when(roundRepo.findAllByGroup(GROUP_ID)).thenReturn(List.of(round1()));

        processor.process(ROUND_1_ID, CORR);

        ArgumentCaptor<SusuGroupEntity> captor = ArgumentCaptor.forClass(SusuGroupEntity.class);
        verify(groupRepo, atLeast(1)).save(captor.capture());

        assertThat(captor.getAllValues()).anyMatch(g -> "COMPLETED".equals(g.getStatus()));
    }

    @Test
    @DisplayName("final round: SusuGroupCompleted event published")
    void final_round_group_completed_event() {
        when(roundRepo.findAllByGroup(GROUP_ID)).thenReturn(List.of(round1()));

        processor.process(ROUND_1_ID, CORR);

        verify(eventPublisher).publishEvent(isA(SusuGroupCompletedEvent.class));
    }

    @Test
    @DisplayName("final round: member memberships transitioned to COMPLETED")
    void final_round_memberships_completed() {
        when(roundRepo.findAllByGroup(GROUP_ID)).thenReturn(List.of(round1()));

        processor.process(ROUND_1_ID, CORR);

        ArgumentCaptor<SusuMembershipEntity> captor =
                ArgumentCaptor.forClass(SusuMembershipEntity.class);
        verify(membershipRepo, atLeast(1)).save(captor.capture());

        assertThat(captor.getAllValues()).anyMatch(m -> "COMPLETED".equals(m.getStatus()));
    }

    @Test
    @DisplayName("final round: no contribution rows generated (group is done)")
    void final_round_no_new_contributions() {
        when(roundRepo.findAllByGroup(GROUP_ID)).thenReturn(List.of(round1()));

        processor.process(ROUND_1_ID, CORR);

        verifyNoInteractions(contributionRepo);
    }

    // ── Idempotency: DISBURSING guard ─────────────────────────────────────

    @Test
    @DisplayName("round already DISBURSED: processor skips without transfer")
    void already_disbursed_skips() {
        SusuRoundEntity disbursed = disbursingRound1();
        setField(disbursed, "status", "DISBURSED");
        when(roundRepo.findByIdForUpdate(ROUND_1_ID)).thenReturn(Optional.of(disbursed));

        processor.process(ROUND_1_ID, CORR);

        verifyNoInteractions(transferClient);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("round already COMPLETED: processor skips without transfer")
    void already_completed_skips() {
        SusuRoundEntity completed = disbursingRound1();
        setField(completed, "status", "COMPLETED");
        when(roundRepo.findByIdForUpdate(ROUND_1_ID)).thenReturn(Optional.of(completed));

        processor.process(ROUND_1_ID, CORR);

        verifyNoInteractions(transferClient);
    }

    @Test
    @DisplayName("duplicate call: second call sees COMPLETED, no second transfer")
    void duplicate_call_no_double_transfer() {
        processor.process(ROUND_1_ID, CORR);
        verify(transferClient, times(1)).disburse(any(), any(), anyLong(), any(), any(), any());

        SusuRoundEntity completed = disbursingRound1();
        setField(completed, "status", "COMPLETED");
        when(roundRepo.findByIdForUpdate(ROUND_1_ID)).thenReturn(Optional.of(completed));

        processor.process(ROUND_1_ID, CORR);

        verify(transferClient, times(1)).disburse(any(), any(), anyLong(), any(), any(), any());
    }

    // ── Payments failure ───────────────────────────────────────────────────

    @Test
    @DisplayName("Payments failure during disburse: SusuPaymentsException propagates")
    void payments_failure_propagates() {
        when(transferClient.disburse(any(), any(), anyLong(), any(), any(), any()))
                .thenThrow(new SusuPaymentsException("Payments down"));

        assertThatThrownBy(() -> processor.process(ROUND_1_ID, CORR))
                .isInstanceOf(SusuPaymentsException.class);

        verify(roundRepo, never()).save(argThat(r -> "COMPLETED".equals(r.getStatus())));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Payments failure: no contribution rows generated for next round")
    void payments_failure_no_contributions() {
        when(transferClient.disburse(any(), any(), anyLong(), any(), any(), any()))
                .thenThrow(new SusuPaymentsException("down"));

        try { processor.process(ROUND_1_ID, CORR); } catch (Exception ignored) {}

        verifyNoInteractions(contributionRepo);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SusuRoundEntity disbursingRound1() {
        SusuRoundEntity r = new SusuRoundEntity();
        setField(r, "id",                    ROUND_1_ID);
        setField(r, "susuGroupId",           GROUP_ID);
        setField(r, "roundNumber",           1);
        setField(r, "recipientUserId",       RECIPIENT_ID);
        setField(r, "status",                "DISBURSING");
        setField(r, "expectedPotAmount",     120_000L);
        setField(r, "actualPotAmount",       120_000L);
        setField(r, "scheduledCollectionAt", Instant.parse("2026-07-24T00:00:00Z"));
        return r;
    }

    private SusuRoundEntity round1() {
        return disbursingRound1();
    }

    private SusuRoundEntity round2() {
        SusuRoundEntity r = new SusuRoundEntity();
        setField(r, "id",                    ROUND_2_ID);
        setField(r, "susuGroupId",           GROUP_ID);
        setField(r, "roundNumber",           2);
        setField(r, "recipientUserId",       UUID.randomUUID());
        setField(r, "status",                "PENDING");
        setField(r, "expectedPotAmount",     120_000L);
        setField(r, "scheduledCollectionAt", Instant.parse("2026-08-24T00:00:00Z"));
        return r;
    }

    private SusuGroupEntity activeGroup() {
        SusuGroupEntity g = SusuGroupEntity.create(
                UUID.randomUUID(), "Akua's Circle", 20_000L,
                "MONTHLY", 6, "STSH1234", Instant.parse("2026-06-24T00:00:00Z"));
        setField(g, "id",                 GROUP_ID);
        setField(g, "status",             "ACTIVE");
        setField(g, "ledgerAccountId",    SUSU_POT_ID);
        setField(g, "currentRoundNumber", 1);
        return g;
    }

    private SusuMembershipEntity membership(UUID userId) {
        SusuMembershipEntity m = SusuMembershipEntity.create(
                GROUP_ID, userId, Instant.parse("2026-06-24T00:00:00Z"));
        setField(m, "id", UUID.randomUUID());
        return m;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
