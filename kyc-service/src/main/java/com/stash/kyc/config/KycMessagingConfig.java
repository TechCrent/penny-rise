package com.stash.kyc.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the kyc.events exchange and the queues this service publishes to
 * and consumes from.
 *
 * <p><strong>Must stay a {@link TopicExchange}</strong> — found via
 * hands-on testing (see docs/hands-on-testing-findings.md Finding 5) that
 * this was declared as a {@code DirectExchange} while monolith's
 * {@code MonolithMessagingConfig}/{@code NotificationRabbitConfig} and
 * {@code infra/docker/rabbitmq/definitions.json} all declare/expect
 * {@code kyc.events} as topic. monolith's notification fan-in binds to
 * every source exchange with a {@code "#"} wildcard routing key, which
 * only a topic exchange honors as a wildcard — a direct exchange would
 * only match messages literally routed to {@code "#"}. The type mismatch
 * caused monolith to fail its first several startup attempts with
 * {@code PRECONDITION_FAILED} until Spring Retry's backoff outlasted the
 * race with this service's own (conflicting) declaration.
 */
@Configuration
public class KycMessagingConfig {

    public static final String KYC_EXCHANGE = "kyc.events";
    public static final String READY_FOR_REVIEW_QUEUE = "kyc.provider.ready_for_review.queue";
    public static final String READY_FOR_REVIEW_ROUTING_KEY = "kyc.submission.ready_for_review";
    public static final String DELETION_SCHEDULER_APPROVED_QUEUE = "kyc.deletion_scheduler.approved.queue";
    public static final String DELETION_SCHEDULER_REJECTED_QUEUE = "kyc.deletion_scheduler.rejected.queue";
    public static final String KYC_APPROVED_ROUTING_KEY = "kyc.approved";
    public static final String KYC_REJECTED_ROUTING_KEY = "kyc.rejected";

    @Bean
    public TopicExchange kycExchange() {
        return ExchangeBuilder.topicExchange(KYC_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue readyForReviewQueue() {
        return QueueBuilder.durable(READY_FOR_REVIEW_QUEUE).build();
    }

    @Bean
    public Binding readyForReviewBinding(Queue readyForReviewQueue, TopicExchange kycExchange) {
        return BindingBuilder.bind(readyForReviewQueue)
                .to(kycExchange)
                .with(READY_FOR_REVIEW_ROUTING_KEY);
    }

    @Bean
    public Queue deletionSchedulerApprovedQueue() {
        return QueueBuilder.durable(DELETION_SCHEDULER_APPROVED_QUEUE).build();
    }

    @Bean
    public Queue deletionSchedulerRejectedQueue() {
        return QueueBuilder.durable(DELETION_SCHEDULER_REJECTED_QUEUE).build();
    }

    @Bean
    public Binding deletionSchedulerApprovedBinding(Queue deletionSchedulerApprovedQueue,
                                                    TopicExchange kycExchange) {
        return BindingBuilder.bind(deletionSchedulerApprovedQueue)
                .to(kycExchange)
                .with(KYC_APPROVED_ROUTING_KEY);
    }

    @Bean
    public Binding deletionSchedulerRejectedBinding(Queue deletionSchedulerRejectedQueue,
                                                    TopicExchange kycExchange) {
        return BindingBuilder.bind(deletionSchedulerRejectedQueue)
                .to(kycExchange)
                .with(KYC_REJECTED_ROUTING_KEY);
    }
}
