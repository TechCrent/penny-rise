package com.stash.payments.idempotency.service;

import com.stash.payments.idempotency.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Daily job that purges expired idempotency keys.
 *
 * <p>Runs in batches of 500 to avoid a long-held table lock on the
 * idempotency_keys table. A single DELETE of potentially thousands of
 * rows would spike latency on the key-lookup path for the duration.
 *
 * <p>Schedule: 02:00 UTC daily (low-traffic window for Ghana West Africa Time).
 */
@Component
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);
    private static final int BATCH_SIZE = 500;

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    public IdempotencyCleanupJob(IdempotencyKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock      = clock;
    }

    @Scheduled(cron = "0 0 2 * * *")   // 02:00 UTC daily
    @Transactional
    public void purgeExpiredKeys() {
        Instant now   = Instant.now(clock);
        int total     = 0;
        int deleted;

        log.info("IdempotencyCleanupJob: starting purge of expired keys at {}", now);

        do {
            deleted = repository.deleteExpiredBatch(now, BATCH_SIZE);
            total  += deleted;
            if (deleted > 0) {
                log.debug("IdempotencyCleanupJob: purged batch of {} rows", deleted);
            }
        } while (deleted == BATCH_SIZE);   // stop when a batch came back under the limit

        log.info("IdempotencyCleanupJob: purge complete — {} total rows deleted", total);
    }
}
