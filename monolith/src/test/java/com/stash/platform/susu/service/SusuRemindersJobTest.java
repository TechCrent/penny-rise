package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.repository.SusuContributionRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SusuRemindersJobTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-23T08:00:00Z"), ZoneOffset.UTC);

    private final SusuContributionRepository contributionRepo =
            Mockito.mock(SusuContributionRepository.class);
    private final SusuContributionReminderProcessor processor =
            Mockito.mock(SusuContributionReminderProcessor.class);

    private final SusuRemindersJob job =
            new SusuRemindersJob(contributionRepo, processor, FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        when(contributionRepo.findPendingContributionsInWindow(any(), any(), anyInt()))
                .thenReturn(List.of());
    }

    // ── Window calculation ─────────────────────────────────────────────

    @Test
    @DisplayName("48H query window: starts now+24h, ends now+48h")
    void window_48h_correct() {
        Instant now           = Instant.parse("2026-07-23T08:00:00Z");
        Instant window48Start = now.plus(24, ChronoUnit.HOURS);
        Instant window48End   = now.plus(48, ChronoUnit.HOURS);

        job.emitReminders();

        verify(contributionRepo).findPendingContributionsInWindow(
                eq(window48Start), eq(window48End), anyInt());
    }

    @Test
    @DisplayName("24H query window: starts now, ends now+24h")
    void window_24h_correct() {
        Instant now           = Instant.parse("2026-07-23T08:00:00Z");
        Instant window24End   = now.plus(24, ChronoUnit.HOURS);

        job.emitReminders();

        verify(contributionRepo).findPendingContributionsInWindow(
                eq(now), eq(window24End), anyInt());
    }

    // ── Processor delegation ───────────────────────────────────────────

    @Test
    @DisplayName("48H reminder: processor called with TYPE_48H for each contribution")
    void delegates_48h_to_processor() {
        SusuContributionEntity c1 = stubContribution();
        SusuContributionEntity c2 = stubContribution();

        when(contributionRepo.findPendingContributionsInWindow(any(), any(), anyInt()))
                .thenReturn(List.of(c1, c2))
                .thenReturn(List.of());

        job.emitReminders();

        verify(processor).processOne(eq(c1), eq("48H"), anyString());
        verify(processor).processOne(eq(c2), eq("48H"), anyString());
    }

    @Test
    @DisplayName("24H reminder: processor called with TYPE_24H for each contribution")
    void delegates_24h_to_processor() {
        SusuContributionEntity c = stubContribution();

        when(contributionRepo.findPendingContributionsInWindow(any(), any(), anyInt()))
                .thenReturn(List.of())
                .thenReturn(List.of(c));

        job.emitReminders();

        verify(processor).processOne(eq(c), eq("24H"), anyString());
    }

    @Test
    @DisplayName("both 48H and 24H issued for different windows on the same run")
    void both_windows_processed_per_run() {
        SusuContributionEntity c48 = stubContribution();
        SusuContributionEntity c24 = stubContribution();

        when(contributionRepo.findPendingContributionsInWindow(any(), any(), anyInt()))
                .thenReturn(List.of(c48))
                .thenReturn(List.of(c24));

        job.emitReminders();

        verify(processor).processOne(eq(c48), eq("48H"), anyString());
        verify(processor).processOne(eq(c24), eq("24H"), anyString());
    }

    @Test
    @DisplayName("no contributions in window: processor not called")
    void empty_window_no_calls() {
        job.emitReminders();
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("processor exception for one contribution does not stop others")
    void processor_exception_is_isolated() {
        SusuContributionEntity c1 = stubContribution();
        SusuContributionEntity c2 = stubContribution();

        when(contributionRepo.findPendingContributionsInWindow(any(), any(), anyInt()))
                .thenReturn(List.of(c1, c2))
                .thenReturn(List.of());

        doThrow(new RuntimeException("transient failure"))
                .when(processor).processOne(eq(c1), anyString(), anyString());

        job.emitReminders();

        verify(processor).processOne(eq(c1), eq("48H"), anyString());
        verify(processor).processOne(eq(c2), eq("48H"), anyString());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private SusuContributionEntity stubContribution() {
        SusuContributionEntity c = new SusuContributionEntity();
        setField(c, "id",                     UUID.randomUUID());
        setField(c, "susuRoundId",            UUID.randomUUID());
        setField(c, "susuGroupId",            UUID.randomUUID());
        setField(c, "memberUserId",           UUID.randomUUID());
        setField(c, "expectedAmount",         20_000L);
        setField(c, "status",                 "PENDING");
        setField(c, "penaltyAmount",          0L);
        setField(c, "isLate",                 false);
        setField(c, "collectionAttemptCount", 0);
        setField(c, "createdAt",              Instant.now());
        return c;
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
