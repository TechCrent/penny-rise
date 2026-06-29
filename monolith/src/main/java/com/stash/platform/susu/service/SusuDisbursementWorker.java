package com.stash.platform.susu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.stash.platform.susu.config.SusuRabbitConfig.DISBURSEMENT_QUEUE;

/**
 * Consumes {@code susu.round.fully_collected} events and processes disbursements.
 *
 * <p>Two processing paths:
 * <ol>
 *   <li><b>Event-driven:</b> {@link #onFullyCollected} fires immediately on the
 *       common path, so a disbursement doesn't wait for the next poll.</li>
 *   <li><b>Scheduled fallback:</b> {@link #processDisbursingRounds} polls every
 *       minute for any round stuck in {@code DISBURSING} — covers the event
 *       being lost (consumer offline, broker restart) or a transient Payments
 *       failure on the event-driven attempt. This is the actual retry
 *       mechanism, not RabbitMQ redelivery — the consumer doesn't requeue on
 *       failure (see below), so a failed event-driven attempt just waits for
 *       the next poll rather than hammering Payments in a tight requeue loop.</li>
 * </ol>
 *
 * <p><strong>Why no requeue-on-failure:</strong> {@link SusuDisbursementProcessor#process}
 * is idempotent on the round's DISBURSING status, so a duplicate attempt from
 * either path is always safe — there's no correctness reason to retry
 * immediately within the same delivery. Catching and logging here (rather than
 * rethrowing) avoids an indefinite requeue loop against a single bad message;
 * the round stays in DISBURSING and the scheduled poll picks it up on its own
 * cadence instead.
 */
@Component
public class SusuDisbursementWorker {

    private static final Logger log = LoggerFactory.getLogger(SusuDisbursementWorker.class);
    private static final int    BATCH_SIZE = 10;

    private final SusuDisbursementProcessor processor;
    private final SusuRoundRepository       roundRepo;
    private final ObjectMapper              objectMapper;

    public SusuDisbursementWorker(SusuDisbursementProcessor processor,
                                   SusuRoundRepository roundRepo,
                                   ObjectMapper objectMapper) {
        this.processor    = processor;
        this.roundRepo    = roundRepo;
        this.objectMapper = objectMapper;
    }

    // ── Event-driven path ─────────────────────────────────────────────────

    @RabbitListener(queues = DISBURSEMENT_QUEUE)
    public void onFullyCollected(@Payload String messageBody) {
        UUID roundId = null;
        try {
            Map<?, ?> payload = objectMapper.readValue(messageBody, Map.class);
            roundId = UUID.fromString((String) payload.get("round_id"));
            String correlationId = "disbursement-" + roundId;

            processor.process(roundId, correlationId);

        } catch (SusuPaymentsException e) {
            log.warn("DisbursementWorker: event-driven attempt failed for round={}, will " +
                     "retry on next scheduled poll. error={}", roundId, e.getMessage());
        } catch (Exception e) {
            log.error("DisbursementWorker: could not process fully_collected message: {}",
                    e.getMessage(), e);
        }
    }

    // ── Scheduled fallback poller ──────────────────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    public void processDisbursingRounds() {
        List<SusuRoundEntity> due = roundRepo.findDisbursingRoundsForUpdate(BATCH_SIZE);
        if (due.isEmpty()) return;

        log.info("DisbursementWorker: scheduled poll found {} DISBURSING round(s)", due.size());

        for (SusuRoundEntity round : due) {
            String correlationId = "disbursement-recovery-" + round.getId();
            try {
                processor.process(round.getId(), correlationId);
            } catch (SusuPaymentsException e) {
                log.warn("DisbursementWorker: poll attempt failed for round={}, will retry " +
                         "next cycle. error={}", round.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("DisbursementWorker: unexpected error for round={}: {}",
                        round.getId(), e.getMessage(), e);
            }
        }
    }
}
