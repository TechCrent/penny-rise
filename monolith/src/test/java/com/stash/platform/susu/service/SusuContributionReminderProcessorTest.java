package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuContributionReminderEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuContributionReminderEvent;
import com.stash.platform.susu.repository.SusuContributionReminderRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SusuContributionReminderProcessorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-23T08:00:00Z"), ZoneOffset.UTC);

    private final SusuContributionReminderRepository reminderRepo =
            Mockito.mock(SusuContributionReminderRepository.class);
    private final SusuRoundRepository roundRepo =
            Mockito.mock(SusuRoundRepository.class);
    private final ApplicationEventPublisher eventPublisher =
            Mockito.mock(ApplicationEventPublisher.class);

    private final SusuContributionReminderProcessor processor =
            new SusuContributionReminderProcessor(
                    reminderRepo, roundRepo, eventPublisher, FIXED_CLOCK);

    private static final UUID CONTRIB_ID = UUID.randomUUID();
    private static final UUID ROUND_ID   = UUID.randomUUID();
    private static final UUID GROUP_ID   = UUID.randomUUID();
    private static final UUID MEMBER_ID  = UUID.randomUUID();
    private static final String CORR     = "corr-reminder-001";

    @BeforeEach
    void setUp() {
        when(reminderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.of(collectingRound()));
    }

    // ── Happy: 48H reminder ────────────────────────────────────────────

    @Test
    @DisplayName("48H reminder: inserts reminder record and publishes event")
    void emits_48h_reminder() {
        processor.processOne(pendingContribution(), "48H", CORR);

        verify(reminderRepo).save(argThat(r ->
                r.getReminderType().equals("48H") &&
                r.getContributionId().equals(CONTRIB_ID)));
        verify(eventPublisher).publishEvent(isA(SusuContributionReminderEvent.class));
    }

    @Test
    @DisplayName("48H reminder event has correct reminder_type and member_user_id")
    void reminder_event_correct_fields() {
        processor.processOne(pendingContribution(), "48H", CORR);

        ArgumentCaptor<SusuContributionReminderEvent> captor =
                ArgumentCaptor.forClass(SusuContributionReminderEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        SusuContributionReminderEvent event = captor.getValue();
        assertThat(event.getReminderType()).isEqualTo("48H");
        assertThat(event.getMemberUserId()).isEqualTo(MEMBER_ID);
        assertThat(event.getAmountPesewas()).isEqualTo(20_000L);
        assertThat(event.getRoundId()).isEqualTo(ROUND_ID);
        assertThat(event.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(event.getDueDate()).isNotNull();
    }

    // ── Happy: 24H reminder ────────────────────────────────────────────

    @Test
    @DisplayName("24H reminder: inserts reminder record and publishes event")
    void emits_24h_reminder() {
        processor.processOne(pendingContribution(), "24H", CORR);

        verify(reminderRepo).save(argThat(r -> r.getReminderType().equals("24H")));
        verify(eventPublisher).publishEvent(isA(SusuContributionReminderEvent.class));
    }

    // ── Both reminders for same contribution ─────────────────────────

    @Test
    @DisplayName("48H and 24H reminders for same contribution: both emitted separately")
    void both_reminders_emitted_separately() {
        processor.processOne(pendingContribution(), "48H", CORR);
        processor.processOne(pendingContribution(), "24H", CORR);

        verify(reminderRepo, times(2)).save(any());
        verify(eventPublisher, times(2)).publishEvent(any());
    }

    // ── Idempotency: already sent ─────────────────────────────────────

    @Test
    @DisplayName("duplicate 48H reminder: INSERT conflict is caught, no second event")
    void duplicate_48h_skipped() {
        when(reminderRepo.save(any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        processor.processOne(pendingContribution(), "48H", CORR);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("idempotent re-run: second call with same contribution+type skips silently")
    void idempotent_rerun_no_double_event() {
        processor.processOne(pendingContribution(), "24H", CORR);
        verify(eventPublisher, times(1)).publishEvent(any());

        when(reminderRepo.save(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        processor.processOne(pendingContribution(), "24H", CORR);

        verifyNoMoreInteractions(eventPublisher);
    }

    // ── Processor is status-agnostic; query is the gate ───────────────

    @Test
    @DisplayName("PAID contribution: processor still runs — query is the authoritative gate")
    void paid_contribution_excluded_by_query() {
        SusuContributionEntity paid = pendingContribution();
        setField(paid, "status", "PAID");

        processor.processOne(paid, "24H", CORR);
        verify(eventPublisher).publishEvent(any());
    }

    // ── Round not found ────────────────────────────────────────────────

    @Test
    @DisplayName("round not found: reminder record inserted but no event published")
    void round_not_found_no_event() {
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.empty());

        processor.processOne(pendingContribution(), "48H", CORR);

        verify(reminderRepo).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private SusuContributionEntity pendingContribution() {
        SusuContributionEntity c = new SusuContributionEntity();
        setField(c, "id",                     CONTRIB_ID);
        setField(c, "susuRoundId",            ROUND_ID);
        setField(c, "susuGroupId",            GROUP_ID);
        setField(c, "memberUserId",           MEMBER_ID);
        setField(c, "expectedAmount",         20_000L);
        setField(c, "status",                 "PENDING");
        setField(c, "penaltyAmount",          0L);
        setField(c, "isLate",                 false);
        setField(c, "collectionAttemptCount", 0);
        setField(c, "createdAt",              Instant.parse("2026-07-20T00:00:00Z"));
        return c;
    }

    private SusuRoundEntity collectingRound() {
        SusuRoundEntity r = new SusuRoundEntity();
        setField(r, "id",                    ROUND_ID);
        setField(r, "susuGroupId",           GROUP_ID);
        setField(r, "roundNumber",           1);
        setField(r, "recipientUserId",       UUID.randomUUID());
        setField(r, "status",                "COLLECTING");
        setField(r, "scheduledCollectionAt", Instant.parse("2026-07-25T00:00:00Z"));
        setField(r, "expectedPotAmount",     120_000L);
        return r;
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
