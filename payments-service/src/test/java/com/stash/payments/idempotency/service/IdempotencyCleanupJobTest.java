package com.stash.payments.idempotency.service;

import com.stash.payments.idempotency.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyCleanupJobTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T02:00:00Z"), ZoneOffset.UTC);

    private final IdempotencyKeyRepository repo = Mockito.mock(IdempotencyKeyRepository.class);
    private final IdempotencyCleanupJob job = new IdempotencyCleanupJob(repo, FIXED_CLOCK);

    @Test
    @DisplayName("purges all expired rows across multiple batches")
    void purges_in_batches_until_under_limit() {
        // First batch: full 500; second batch: 200 (signals end)
        when(repo.deleteExpiredBatch(any(Instant.class), eq(500)))
                .thenReturn(500)
                .thenReturn(200);

        job.purgeExpiredKeys();

        verify(repo, times(2)).deleteExpiredBatch(any(Instant.class), eq(500));
    }

    @Test
    @DisplayName("stops after first batch if fewer than batch-size rows deleted")
    void stops_after_single_partial_batch() {
        when(repo.deleteExpiredBatch(any(Instant.class), eq(500)))
                .thenReturn(47);

        job.purgeExpiredKeys();

        verify(repo, times(1)).deleteExpiredBatch(any(Instant.class), eq(500));
    }

    @Test
    @DisplayName("no-ops cleanly when no expired rows exist")
    void noop_when_nothing_to_purge() {
        when(repo.deleteExpiredBatch(any(Instant.class), eq(500)))
                .thenReturn(0);

        job.purgeExpiredKeys();

        verify(repo, times(1)).deleteExpiredBatch(any(Instant.class), eq(500));
    }
}
