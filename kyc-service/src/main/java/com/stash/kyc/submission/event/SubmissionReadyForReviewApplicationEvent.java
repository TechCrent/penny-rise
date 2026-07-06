package com.stash.kyc.submission.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Spring application event published within the document-confirmation
 * transaction. {@link SubmissionReadyForReviewEventPublisher} listens and
 * publishes the real {@link SubmissionReadyForReviewEvent} to RabbitMQ
 * AFTER the transaction commits — see
 * docs/hands-on-testing-findings.md Finding 6 for why this matters: a
 * direct in-transaction {@code rabbitTemplate.convertAndSend} let
 * {@code AutomatedDecisionService} observe the submission before its
 * REVIEWING status was durably committed, silently dropping the decision.
 */
public class SubmissionReadyForReviewApplicationEvent extends ApplicationEvent {

    private final UUID submissionId;
    private final UUID userId;
    private final String correlationId;

    public SubmissionReadyForReviewApplicationEvent(Object source, UUID submissionId,
                                                    UUID userId, String correlationId) {
        super(source);
        this.submissionId = submissionId;
        this.userId = userId;
        this.correlationId = correlationId;
    }

    public UUID getSubmissionId() { return submissionId; }
    public UUID getUserId() { return userId; }
    public String getCorrelationId() { return correlationId; }
}
