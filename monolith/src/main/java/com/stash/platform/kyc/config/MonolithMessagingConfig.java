package com.stash.platform.kyc.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Monolith-side RabbitMQ bindings for KYC decision events.
 *
 * <p>The {@code kyc.events} exchange is owned by kyc-service; the monolith
 * declares its consumer queues and bindings so local/dev Spring AMQP and
 * {@code infra/docker/rabbitmq/definitions.json} stay aligned.
 *
 * <p>Issue numbering note: document-deletion scheduling was implemented under
 * the v0.2-026 label in kyc-service; this consumer is the Issue Plan's
 * original v0.2-026 monolith hook (sync {@code users.kyc_status} on approve/reject).
 */
@Configuration
public class MonolithMessagingConfig {

    public static final String KYC_EXCHANGE = "kyc.events";
    public static final String KYC_APPROVED_ROUTING_KEY = "kyc.approved";
    public static final String KYC_REJECTED_ROUTING_KEY = "kyc.rejected";
    public static final String MONOLITH_KYC_APPROVED_QUEUE = "monolith.kyc.approved.queue";
    public static final String MONOLITH_KYC_REJECTED_QUEUE = "monolith.kyc.rejected.queue";

    @Bean
    public DirectExchange kycEventsExchange() {
        return ExchangeBuilder.directExchange(KYC_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue monolithKycApprovedQueue() {
        return QueueBuilder.durable(MONOLITH_KYC_APPROVED_QUEUE).build();
    }

    @Bean
    public Queue monolithKycRejectedQueue() {
        return QueueBuilder.durable(MONOLITH_KYC_REJECTED_QUEUE).build();
    }

    @Bean
    public Binding monolithKycApprovedBinding(Queue monolithKycApprovedQueue,
                                              DirectExchange kycEventsExchange) {
        return BindingBuilder.bind(monolithKycApprovedQueue)
                .to(kycEventsExchange)
                .with(KYC_APPROVED_ROUTING_KEY);
    }

    @Bean
    public Binding monolithKycRejectedBinding(Queue monolithKycRejectedQueue,
                                              DirectExchange kycEventsExchange) {
        return BindingBuilder.bind(monolithKycRejectedQueue)
                .to(kycEventsExchange)
                .with(KYC_REJECTED_ROUTING_KEY);
    }
}
