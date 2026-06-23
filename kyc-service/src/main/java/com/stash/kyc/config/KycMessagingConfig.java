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
 * Declares the kyc.events exchange and the ready-for-review queue this
 * service publishes to and consumes from.
 */
@Configuration
public class KycMessagingConfig {

    public static final String KYC_EXCHANGE = "kyc.events";
    public static final String READY_FOR_REVIEW_QUEUE = "kyc.provider.ready_for_review.queue";
    public static final String READY_FOR_REVIEW_ROUTING_KEY = "kyc.submission.ready_for_review";

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
}
