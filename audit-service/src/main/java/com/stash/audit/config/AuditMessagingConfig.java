package com.stash.audit.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the Audit Log Service.
 *
 * The audit service subscribes to ALL significant domain events from every
 * other service. At this stage the queue is declared and bound as a placeholder;
 * the actual @RabbitListener consumer will be wired in when audit_log_entries
 * schema is in place (v0.2+).
 *
 * Routing key pattern "#" on each exchange means: receive every event
 * published to that exchange regardless of routing key.
 */
@Configuration
public class AuditMessagingConfig {

    // Queue names
    public static final String AUDIT_PAYMENTS_QUEUE  = "audit.payments.events";
    public static final String AUDIT_MONOLITH_QUEUE  = "audit.monolith.events";
    public static final String AUDIT_DEAD_LETTER_QUEUE = "dead.letter.queue";

    // Exchange names — must match definitions.json declarations
    public static final String PAYMENTS_EXCHANGE = "payments.events";
    public static final String MONOLITH_EXCHANGE  = "monolith.events";
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter";

    // ── Queues ────────────────────────────────────────────────────────────

    @Bean
    public Queue auditPaymentsQueue() {
        return QueueBuilder.durable(AUDIT_PAYMENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Queue auditMonolithQueue() {
        return QueueBuilder.durable(AUDIT_MONOLITH_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    // ── Exchanges (passive — already declared in definitions.json) ────────

    @Bean
    public TopicExchange paymentsExchange() {
        return ExchangeBuilder.topicExchange(PAYMENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public TopicExchange monolithExchange() {
        return ExchangeBuilder.topicExchange(MONOLITH_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────

    @Bean
    public Binding auditPaymentsBinding() {
        // "#" = receive ALL events from payments.events exchange
        return BindingBuilder
                .bind(auditPaymentsQueue())
                .to(paymentsExchange())
                .with("#");
    }

    @Bean
    public Binding auditMonolithBinding() {
        // "#" = receive ALL events from monolith.events exchange
        return BindingBuilder
                .bind(auditMonolithQueue())
                .to(monolithExchange())
                .with("#");
    }
}