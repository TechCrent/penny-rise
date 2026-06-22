package com.stash.kyc.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the kyc.events exchange this service publishes to.
 * Matches the topology pattern established for monolith.events /
 * payments.events in v0.1-004's RabbitMQ definitions.
 */
@Configuration
public class KycMessagingConfig {

    public static final String KYC_EXCHANGE = "kyc.events";

    @Bean
    public DirectExchange kycExchange() {
        return ExchangeBuilder.directExchange(KYC_EXCHANGE)
                .durable(true)
                .build();
    }
}
