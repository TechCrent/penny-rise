package com.stash.kyc.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the kyc.events exchange and the queues this service publishes to
 * and consumes from.
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
    public DirectExchange kycExchange() {
        return ExchangeBuilder.directExchange(KYC_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue readyForReviewQueue() {
        return QueueBuilder.durable(READY_FOR_REVIEW_QUEUE).build();
    }

    @Bean
    public Binding readyForReviewBinding(Queue readyForReviewQueue, DirectExchange kycExchange) {
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
                                                    DirectExchange kycExchange) {
        return BindingBuilder.bind(deletionSchedulerApprovedQueue)
                .to(kycExchange)
                .with(KYC_APPROVED_ROUTING_KEY);
    }

    @Bean
    public Binding deletionSchedulerRejectedBinding(Queue deletionSchedulerRejectedQueue,
                                                    DirectExchange kycExchange) {
        return BindingBuilder.bind(deletionSchedulerRejectedQueue)
                .to(kycExchange)
                .with(KYC_REJECTED_ROUTING_KEY);
    }
}
