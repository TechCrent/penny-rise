package com.stash.payments.idempotency.service;

import com.stash.payments.idempotency.domain.IdempotencyKey;
import com.stash.payments.idempotency.domain.IdempotencyState;
import com.stash.payments.idempotency.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Core logic for the idempotency framework. Called by {@link IdempotencyFilter}.
 *
 * <p>All five paths handled here:
 * <ol>
 *   <li>First request — insert PROCESSING row.</li>
 *   <li>Retry with same hash — return cached response.</li>
 *   <li>Same key, different hash — reject 422.</li>
 *   <li>Concurrent retry while PROCESSING — return 409.</li>
 *   <li>Expired key — delete and start fresh.</li>
 * </ol>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock      = clock;
    }

    /**
     * Attempts to claim an idempotency key for a new request.
     *
     * <p>Intentionally NOT annotated {@code @Transactional}: each repo call runs
     * in its own implicit mini-transaction. If {@code saveAndFlush} throws a
     * {@link DataIntegrityViolationException} (concurrent INSERT race), the failed
     * mini-transaction is already rolled back before the catch block runs, so the
     * re-read in the catch block gets a clean connection with no aborted-transaction
     * state to contend with.
     *
     * @return the outcome — callers branch on the type
     */
    public ClaimResult claim(String keyValue, String requestHash, String requestPath) {
        Instant now = Instant.now(clock);
        Optional<IdempotencyKey> existing = repository.findByKeyValue(keyValue);

        if (existing.isPresent()) {
            IdempotencyKey row = existing.get();

            // Path 5: expired — purge and fall through to Path 1
            if (row.isExpired(now)) {
                log.debug("Idempotency key '{}' expired; purging and treating as first request", keyValue);
                repository.deleteByKeyValue(keyValue);
                // fall through to insert below
            }
            // Path 3: same key, different body
            else if (!row.getRequestHash().equals(requestHash)) {
                return ClaimResult.keyReused();
            }
            // Path 4: concurrent retry while PROCESSING
            else if (row.getState() == IdempotencyState.PROCESSING) {
                return ClaimResult.inProgress();
            }
            // Path 2: retry with cached response (COMPLETED or FAILED)
            else {
                return ClaimResult.cached(row.getResponseStatusCode(), row.getResponseBody());
            }
        }

        // Path 1 (and Path 5 fall-through): insert PROCESSING row
        try {
            Instant expiresAt = now.plus(TTL);
            IdempotencyKey newRow = new IdempotencyKey(keyValue, requestHash, requestPath, now, expiresAt);
            repository.saveAndFlush(newRow);
            return ClaimResult.proceed(newRow.getId().toString());
        } catch (DataIntegrityViolationException e) {
            // Two concurrent threads raced on Path 1 — the other thread won the INSERT.
            // Re-read to determine which concurrent case we're in.
            log.debug("Idempotency key '{}' insert race; re-reading", keyValue);
            IdempotencyKey raced = repository.findByKeyValue(keyValue)
                    .orElseThrow(() -> new IllegalStateException(
                            "Key disappeared immediately after insert race: " + keyValue));

            if (raced.getState() == IdempotencyState.PROCESSING) {
                return ClaimResult.inProgress();
            }
            return ClaimResult.cached(raced.getResponseStatusCode(), raced.getResponseBody());
        }
    }

    /**
     * Called after the downstream handler completes successfully.
     * Runs in a NEW transaction so it commits even if the caller's
     * transaction is rolling back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String keyValue, int statusCode, String responseBody) {
        repository.findByKeyValue(keyValue).ifPresent(row -> {
            row.markCompleted(statusCode, responseBody);
            repository.save(row);
        });
    }

    /**
     * Called after the downstream handler throws or returns a 5xx.
     * Marks the key FAILED so retries get the cached error response.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String keyValue, int statusCode, String responseBody) {
        repository.findByKeyValue(keyValue).ifPresent(row -> {
            row.markFailed(statusCode, responseBody);
            repository.save(row);
        });
    }

    // ── Result type ───────────────────────────────────────────────────────

    public sealed interface ClaimResult
            permits ClaimResult.Proceed, ClaimResult.Cached,
                    ClaimResult.KeyReused, ClaimResult.InProgress {

        record Proceed(String rowId) implements ClaimResult {}
        record Cached(int statusCode, String body) implements ClaimResult {}
        record KeyReused() implements ClaimResult {}
        record InProgress() implements ClaimResult {}

        static ClaimResult proceed(String rowId)            { return new Proceed(rowId); }
        static ClaimResult cached(int sc, String body)      { return new Cached(sc, body); }
        static ClaimResult keyReused()                      { return new KeyReused(); }
        static ClaimResult inProgress()                     { return new InProgress(); }
    }
}
