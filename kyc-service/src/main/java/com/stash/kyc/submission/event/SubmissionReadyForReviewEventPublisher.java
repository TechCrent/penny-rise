package com.stash.kyc.submission.event;

import com.stash.kyc.config.KycMessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link SubmissionReadyForReviewEvent} to RabbitMQ after the
 * document-confirmation transaction commits.
 *
 * <p>Uses {@code @TransactionalEventListener(AFTER_COMMIT)} so the publish
 * never happens if that transaction rolls back, and — the bug this fixes,
 * see docs/hands-on-testing-findings.md Finding 6 — so a consumer can
 * never observe the submission's REVIEWING status before it's actually
 * durable.
 *
 * <p>Not {@code @Async}: kyc-service has no {@code @EnableAsync} configured
 * elsewhere, and the correctness fix here is about ordering (publish after
 * commit), not about offloading the publish to another thread.
 */
@Component
public class SubmissionReadyForReviewEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SubmissionReadyForReviewEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public SubmissionReadyForReviewEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionReadyForReview(SubmissionReadyForReviewApplicationEvent event) {
        var readyEvent = new SubmissionReadyForReviewEvent(
                UUID.randomUUID().toString(),
                SubmissionReadyForReviewEvent.EVENT_TYPE,
                SubmissionReadyForReviewEvent.SCHEMA_VERSION,
                SubmissionReadyForReviewEvent.SOURCE_SERVICE,
                Instant.now(),
                event.getCorrelationId(),
                new SubmissionReadyForReviewEvent.Payload(event.getSubmissionId(), event.getUserId())
        );

        rabbitTemplate.convertAndSend(
                KycMessagingConfig.KYC_EXCHANGE,
                "kyc.submission.ready_for_review",
                readyEvent
        );

        log.info("Published SubmissionReadyForReview submissionId={} correlationId={}",
                event.getSubmissionId(), event.getCorrelationId());
    }
}
