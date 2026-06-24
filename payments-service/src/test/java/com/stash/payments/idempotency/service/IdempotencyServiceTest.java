package com.stash.payments.idempotency.service;

import com.stash.payments.idempotency.domain.IdempotencyKey;
import com.stash.payments.idempotency.domain.IdempotencyState;
import com.stash.payments.idempotency.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final IdempotencyKeyRepository repo =
            Mockito.mock(IdempotencyKeyRepository.class);
    private final IdempotencyService service =
            new IdempotencyService(repo, FIXED_CLOCK);

    private static final String KEY   = "test-key-001";
    private static final String HASH  = "abc123hash";
    private static final String PATH  = "/api/v1/transactions/deposits";

    @BeforeEach
    void setUp() {
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Path 1: first request ─────────────────────────────────────────────

    @Test
    @DisplayName("Path 1: first request — inserts PROCESSING row and returns Proceed")
    void first_request_inserts_and_returns_proceed() {
        when(repo.findByKeyValue(KEY)).thenReturn(Optional.empty());

        var result = service.claim(KEY, HASH, PATH);

        assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.Proceed.class);
        verify(repo).saveAndFlush(any(IdempotencyKey.class));
    }

    // ── Path 2: retry with same hash ──────────────────────────────────────

    @Test
    @DisplayName("Path 2: retry with same hash — returns cached response")
    void retry_same_hash_returns_cached_response() {
        IdempotencyKey existing = completedRow(KEY, HASH, 200, "{\"ok\":true}");
        when(repo.findByKeyValue(KEY)).thenReturn(Optional.of(existing));

        var result = service.claim(KEY, HASH, PATH);

        assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.Cached.class);
        var cached = (IdempotencyService.ClaimResult.Cached) result;
        assertThat(cached.statusCode()).isEqualTo(200);
        assertThat(cached.body()).isEqualTo("{\"ok\":true}");
    }

    // ── Path 3: same key, different hash ──────────────────────────────────

    @Test
    @DisplayName("Path 3: same key, different body — returns KeyReused")
    void same_key_different_hash_returns_key_reused() {
        IdempotencyKey existing = completedRow(KEY, "original-hash", 200, "{}");
        when(repo.findByKeyValue(KEY)).thenReturn(Optional.of(existing));

        var result = service.claim(KEY, "completely-different-hash", PATH);

        assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.KeyReused.class);
    }

    // ── Path 4: concurrent retry while PROCESSING ─────────────────────────

    @Test
    @DisplayName("Path 4: key exists and is PROCESSING — returns InProgress")
    void processing_key_returns_in_progress() {
        IdempotencyKey processing = processingRow(KEY, HASH);
        when(repo.findByKeyValue(KEY)).thenReturn(Optional.of(processing));

        var result = service.claim(KEY, HASH, PATH);

        assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.InProgress.class);
    }

    @Test
    @DisplayName("Path 4 (race): INSERT conflict — re-reads PROCESSING and returns InProgress")
    void insert_race_re_reads_and_returns_in_progress() {
        // First findByKeyValue sees nothing — races with another thread
        when(repo.findByKeyValue(KEY))
                .thenReturn(Optional.empty())           // initial check
                .thenReturn(Optional.of(processingRow(KEY, HASH)));  // re-read after race

        when(repo.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var result = service.claim(KEY, HASH, PATH);

        assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.InProgress.class);
    }

    // ── Path 5: expired key ────────────────────────────────────────────────

    @Test
    @DisplayName("Path 5: expired key — purges and treats as first request")
    void expired_key_purged_and_restarted() {
        IdempotencyKey expired = expiredRow(KEY, HASH);
        when(repo.findByKeyValue(KEY)).thenReturn(Optional.of(expired));

        var result = service.claim(KEY, HASH, PATH);

        verify(repo).deleteByKeyValue(KEY);
        verify(repo).saveAndFlush(any(IdempotencyKey.class));
        assertThat(result).isInstanceOf(IdempotencyService.ClaimResult.Proceed.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static IdempotencyKey completedRow(String key, String hash,
                                               int status, String body) {
        Instant now = Instant.parse("2026-06-24T09:00:00Z");   // within TTL
        var row = new IdempotencyKey(key, hash, PATH, now, now.plusSeconds(3600));
        row.markCompleted(status, body);
        return row;
    }

    private static IdempotencyKey processingRow(String key, String hash) {
        Instant now = Instant.parse("2026-06-24T09:59:00Z");
        return new IdempotencyKey(key, hash, PATH, now, now.plusSeconds(3600));
    }

    private static IdempotencyKey expiredRow(String key, String hash) {
        // expires_at is in the past relative to FIXED_CLOCK (2026-06-24T10:00:00Z)
        Instant past = Instant.parse("2026-06-24T08:00:00Z");
        return new IdempotencyKey(key, hash, PATH,
                past.minusSeconds(86400), past);  // created yesterday, expired 2h ago
    }
}
