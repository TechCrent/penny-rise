package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SusuPotIntegrityJobTest {

    private final SusuGroupRepository     groupRepo = Mockito.mock(SusuGroupRepository.class);
    private final SusuRoundRepository     roundRepo = Mockito.mock(SusuRoundRepository.class);
    private final SusuPotIntegrityChecker checker   = Mockito.mock(SusuPotIntegrityChecker.class);

    private final SusuPotIntegrityJob job =
            new SusuPotIntegrityJob(groupRepo, roundRepo, checker);

    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID ROUND_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(groupRepo.findByStatus(eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(List.of());
    }

    // ── No active groups ───────────────────────────────────────────────

    @Test
    @DisplayName("no active groups: checker not called")
    void no_active_groups_no_check() {
        job.sweep();
        verifyNoInteractions(checker);
    }

    // ── Balanced group ─────────────────────────────────────────────────

    @Test
    @DisplayName("one balanced ACTIVE group: checkQuietly called, returns true")
    void balanced_group_checked() {
        when(groupRepo.findByStatus(eq("ACTIVE"), any())).thenReturn(List.of(activeGroup(1)));
        when(roundRepo.findByGroupAndRoundNumber(GROUP_ID, 1))
                .thenReturn(Optional.of(collectingRound()));
        when(checker.checkQuietly(GROUP_ID, ROUND_ID)).thenReturn(true);

        job.sweep();

        verify(checker).checkQuietly(GROUP_ID, ROUND_ID);
    }

    // ── Drifted group ─────────────────────────────────────────────────

    @Test
    @DisplayName("drifted group: checkQuietly returns false, sweep completes without throwing")
    void drifted_group_job_continues() {
        when(groupRepo.findByStatus(eq("ACTIVE"), any())).thenReturn(List.of(activeGroup(1)));
        when(roundRepo.findByGroupAndRoundNumber(GROUP_ID, 1))
                .thenReturn(Optional.of(collectingRound()));
        when(checker.checkQuietly(GROUP_ID, ROUND_ID)).thenReturn(false);

        assertThatCode(() -> job.sweep()).doesNotThrowAnyException();
    }

    // ── PENDING round skipped ──────────────────────────────────────────

    @Test
    @DisplayName("PENDING round: skipped (not yet collecting)")
    void pending_round_skipped() {
        when(groupRepo.findByStatus(eq("ACTIVE"), any())).thenReturn(List.of(activeGroup(1)));
        SusuRoundEntity pending = collectingRound();
        setField(pending, "status", "PENDING");
        when(roundRepo.findByGroupAndRoundNumber(GROUP_ID, 1))
                .thenReturn(Optional.of(pending));

        job.sweep();

        verifyNoInteractions(checker);
    }

    // ── Multiple groups ────────────────────────────────────────────────

    @Test
    @DisplayName("multiple groups: all checked independently")
    void multiple_groups_all_checked() {
        UUID gid2 = UUID.randomUUID();
        UUID rid2 = UUID.randomUUID();

        SusuGroupEntity g1 = activeGroup(1);
        SusuGroupEntity g2 = activeGroupWith(gid2, 2);

        when(groupRepo.findByStatus(eq("ACTIVE"), any())).thenReturn(List.of(g1, g2));
        when(roundRepo.findByGroupAndRoundNumber(GROUP_ID, 1))
                .thenReturn(Optional.of(collectingRound()));
        when(roundRepo.findByGroupAndRoundNumber(gid2, 2))
                .thenReturn(Optional.of(roundWith(rid2, gid2, 2)));
        when(checker.checkQuietly(any(), any())).thenReturn(true);

        job.sweep();

        verify(checker).checkQuietly(GROUP_ID, ROUND_ID);
        verify(checker).checkQuietly(gid2, rid2);
    }

    @Test
    @DisplayName("one group fails check, second group still checked")
    void one_failure_does_not_stop_sweep() {
        UUID gid2 = UUID.randomUUID();
        UUID rid2 = UUID.randomUUID();

        when(groupRepo.findByStatus(eq("ACTIVE"), any()))
                .thenReturn(List.of(activeGroup(1), activeGroupWith(gid2, 1)));
        when(roundRepo.findByGroupAndRoundNumber(GROUP_ID, 1))
                .thenReturn(Optional.of(collectingRound()));
        when(roundRepo.findByGroupAndRoundNumber(gid2, 1))
                .thenReturn(Optional.of(roundWith(rid2, gid2, 1)));

        when(checker.checkQuietly(GROUP_ID, ROUND_ID)).thenReturn(false);
        when(checker.checkQuietly(gid2, rid2)).thenReturn(true);

        job.sweep();

        verify(checker).checkQuietly(GROUP_ID, ROUND_ID);
        verify(checker).checkQuietly(gid2, rid2);
    }

    // ── No current round number ────────────────────────────────────────

    @Test
    @DisplayName("group with null currentRoundNumber: skipped")
    void null_round_number_skipped() {
        SusuGroupEntity g = activeGroup(null);
        when(groupRepo.findByStatus(eq("ACTIVE"), any())).thenReturn(List.of(g));

        job.sweep();

        verifyNoInteractions(checker);
        verifyNoInteractions(roundRepo);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private SusuGroupEntity activeGroup(Integer roundNumber) {
        return activeGroupWith(GROUP_ID, roundNumber);
    }

    private SusuGroupEntity activeGroupWith(UUID id, Integer roundNumber) {
        SusuGroupEntity g = SusuGroupEntity.create(
                UUID.randomUUID(), "Circle", 20_000L, "MONTHLY", 6,
                "CODE1234", Instant.parse("2026-06-24T00:00:00Z"));
        setField(g, "id",                 id);
        setField(g, "status",             "ACTIVE");
        setField(g, "currentRoundNumber", roundNumber);
        setField(g, "ledgerAccountId",    UUID.randomUUID());
        return g;
    }

    private SusuRoundEntity collectingRound() {
        return roundWith(ROUND_ID, GROUP_ID, 1);
    }

    private SusuRoundEntity roundWith(UUID id, UUID groupId, int roundNum) {
        SusuRoundEntity r = new SusuRoundEntity();
        setField(r, "id",                    id);
        setField(r, "susuGroupId",           groupId);
        setField(r, "roundNumber",           roundNum);
        setField(r, "recipientUserId",       UUID.randomUUID());
        setField(r, "status",                "COLLECTING");
        setField(r, "expectedPotAmount",     120_000L);
        setField(r, "scheduledCollectionAt", Instant.parse("2026-07-24T00:00:00Z"));
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
