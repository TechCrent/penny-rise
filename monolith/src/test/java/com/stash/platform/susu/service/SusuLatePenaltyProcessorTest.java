package com.stash.platform.susu.service;

import com.stash.platform.susu.client.SusuContributionTransferClient;
import com.stash.platform.susu.client.SusuContributionTransferClient.PenaltyChargeResult;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.event.SusuContributionLateEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SusuLatePenaltyProcessorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC);

    private static final long   PENALTY_PESEWAS      = 500L;
    private static final long   HALF_PENALTY         = 250L;
    private static final String PENALTY_REVENUE_ID   = "00000000-0000-0000-0000-000000000003";

    private final SusuContributionRepository     contributionRepo = Mockito.mock(SusuContributionRepository.class);
    private final SusuGroupRepository            groupRepo        = Mockito.mock(SusuGroupRepository.class);
    private final SusuContributionTransferClient transferClient   = Mockito.mock(SusuContributionTransferClient.class);
    private final ApplicationEventPublisher      eventPublisher   = Mockito.mock(ApplicationEventPublisher.class);

    private final SusuLatePenaltyProcessor processor = new SusuLatePenaltyProcessor(
            contributionRepo, groupRepo, transferClient, eventPublisher,
            FIXED_CLOCK, PENALTY_PESEWAS, PENALTY_REVENUE_ID);

    private static final UUID   GROUP_ID    = UUID.randomUUID();
    private static final UUID   ROUND_ID    = UUID.randomUUID();
    private static final UUID   CONTRIB_ID  = UUID.randomUUID();
    private static final UUID   MEMBER_ID   = UUID.randomUUID();
    private static final UUID   SUSU_POT_ID = UUID.randomUUID();
    private static final UUID   WALLET_ID   = UUID.randomUUID();
    private static final String CORR        = "corr-penalty-001";

    @BeforeEach
    void setUp() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroup()));
        when(transferClient.resolveUserWallet(MEMBER_ID, CORR)).thenReturn(WALLET_ID);
        when(transferClient.chargePenaltySplit(
                eq(WALLET_ID), eq(SUSU_POT_ID),
                eq(UUID.fromString(PENALTY_REVENUE_ID)),
                eq(HALF_PENALTY), eq(CONTRIB_ID), eq(CORR),
                eq("susu-penalty-" + CONTRIB_ID)))
                .thenReturn(new PenaltyChargeResult(
                        "STSH-202607-PEN001-POT", "STSH-202607-PEN001-REV", false));
        when(contributionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path: penalty charged ───────────────────────────────────────

    @Test
    @DisplayName("overdue contribution marked LATE with is_late=true")
    void contribution_marked_late() {
        processor.process(pendingContribution(), CORR);

        ArgumentCaptor<SusuContributionEntity> captor =
                ArgumentCaptor.forClass(SusuContributionEntity.class);
        verify(contributionRepo, atLeast(1)).save(captor.capture());

        assertThat(captor.getAllValues())
                .anyMatch(c -> "LATE".equals(c.getStatus()) && c.isLate());
    }

    @Test
    @DisplayName("penalty_amount = 500 pesewas stored on contribution when charged")
    void penalty_amount_stored() {
        processor.process(pendingContribution(), CORR);

        ArgumentCaptor<SusuContributionEntity> captor =
                ArgumentCaptor.forClass(SusuContributionEntity.class);
        verify(contributionRepo, atLeast(1)).save(captor.capture());

        assertThat(captor.getAllValues())
                .anyMatch(c -> c.getPenaltyAmount() == PENALTY_PESEWAS);
    }

    @Test
    @DisplayName("split: chargePenaltySplit called with 250p to SUSU_POT and PENALTY_REVENUE")
    void split_charge_called_correctly() {
        processor.process(pendingContribution(), CORR);

        verify(transferClient).chargePenaltySplit(
                eq(WALLET_ID), eq(SUSU_POT_ID),
                eq(UUID.fromString(PENALTY_REVENUE_ID)),
                eq(HALF_PENALTY), eq(CONTRIB_ID), eq(CORR),
                eq("susu-penalty-" + CONTRIB_ID)
        );
    }

    @Test
    @DisplayName("susu.contribution.late event emitted with correct fields")
    void late_event_emitted() {
        processor.process(pendingContribution(), CORR);

        ArgumentCaptor<SusuContributionLateEvent> captor =
                ArgumentCaptor.forClass(SusuContributionLateEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        SusuContributionLateEvent event = captor.getValue();
        assertThat(event.getContributionId()).isEqualTo(CONTRIB_ID);
        assertThat(event.getMemberUserId()).isEqualTo(MEMBER_ID);
        assertThat(event.getPenaltyAmount()).isEqualTo(PENALTY_PESEWAS);
        assertThat(event.isPenaltyWaived()).isFalse();
    }

    @Test
    @DisplayName("penalty idempotency key base is stable per contribution")
    void penalty_idempotency_key_stable() {
        processor.process(pendingContribution(), CORR);

        verify(transferClient).chargePenaltySplit(
                any(), any(), any(), anyLong(), any(), any(),
                eq("susu-penalty-" + CONTRIB_ID)
        );
    }

    // ── Insufficient balance: penalty waived ──────────────────────────────

    @Test
    @DisplayName("insufficient balance: penalty waived, penalty_amount = 0")
    void insufficient_balance_waived() {
        when(transferClient.chargePenaltySplit(any(), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new PenaltyChargeResult(null, null, true));

        processor.process(pendingContribution(), CORR);

        ArgumentCaptor<SusuContributionEntity> captor =
                ArgumentCaptor.forClass(SusuContributionEntity.class);
        verify(contributionRepo, atLeast(1)).save(captor.capture());

        assertThat(captor.getAllValues()).anyMatch(c -> "LATE".equals(c.getStatus()));
        assertThat(captor.getAllValues()).anyMatch(c -> c.getPenaltyAmount() == 0L);
    }

    @Test
    @DisplayName("insufficient balance: event still emitted with penalty_waived=true")
    void insufficient_balance_event_emitted_as_waived() {
        when(transferClient.chargePenaltySplit(any(), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new PenaltyChargeResult(null, null, true));

        processor.process(pendingContribution(), CORR);

        ArgumentCaptor<SusuContributionLateEvent> captor =
                ArgumentCaptor.forClass(SusuContributionLateEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertThat(captor.getValue().isPenaltyWaived()).isTrue();
        assertThat(captor.getValue().getPenaltyAmount()).isEqualTo(0L);
    }

    // ── Idempotency: already-LATE contribution skipped ────────────────────

    @Test
    @DisplayName("already LATE contribution: skipped — no transfer, no event")
    void already_late_contribution_skipped() {
        SusuContributionEntity late = pendingContribution();
        setField(late, "status", "LATE");
        setField(late, "isLate", true);

        processor.process(late, CORR);

        verifyNoInteractions(transferClient);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("already PAID contribution: skipped")
    void already_paid_contribution_skipped() {
        SusuContributionEntity paid = pendingContribution();
        setField(paid, "status", "PAID");

        processor.process(paid, CORR);

        verifyNoInteractions(transferClient);
        verifyNoInteractions(eventPublisher);
    }

    // ── Payments service failure: contribution stays LATE, no crash ───────

    @Test
    @DisplayName("Payments failure during charge: penalty waived, event still emitted")
    void payments_failure_waived() {
        when(transferClient.chargePenaltySplit(any(), any(), any(), anyLong(), any(), any(), any()))
                .thenThrow(new SusuPaymentsException("Service unavailable"));

        assertThatCode(() -> processor.process(pendingContribution(), CORR))
                .doesNotThrowAnyException();

        verify(eventPublisher).publishEvent(any(SusuContributionLateEvent.class));

        ArgumentCaptor<SusuContributionEntity> captor =
                ArgumentCaptor.forClass(SusuContributionEntity.class);
        verify(contributionRepo, atLeast(1)).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(c -> "LATE".equals(c.getStatus()));
    }

    @Test
    @DisplayName("group's SUSU_POT not found: penalty waived, event still emitted")
    void group_not_found_waived() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());

        processor.process(pendingContribution(), CORR);

        verifyNoInteractions(transferClient);
        verify(eventPublisher).publishEvent(any(SusuContributionLateEvent.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SusuContributionEntity pendingContribution() {
        SusuContributionEntity c = new SusuContributionEntity();
        setField(c, "id",                     CONTRIB_ID);
        setField(c, "susuRoundId",            ROUND_ID);
        setField(c, "susuGroupId",            GROUP_ID);
        setField(c, "memberUserId",           MEMBER_ID);
        setField(c, "expectedAmount",         20_000L);
        setField(c, "status",                 "PENDING");
        setField(c, "collectionAttemptCount", 0);
        setField(c, "penaltyAmount",           0L);
        setField(c, "isLate",                 false);
        setField(c, "createdAt",              Instant.parse("2026-07-24T00:00:00Z"));
        return c;
    }

    private SusuGroupEntity activeGroup() {
        SusuGroupEntity g = SusuGroupEntity.create(
                UUID.randomUUID(), "Akua's Circle", 20_000L,
                "MONTHLY", 6, "STSH1234", Instant.parse("2026-06-24T00:00:00Z"));
        setField(g, "id",              GROUP_ID);
        setField(g, "status",          "ACTIVE");
        setField(g, "ledgerAccountId", SUSU_POT_ID);
        return g;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
