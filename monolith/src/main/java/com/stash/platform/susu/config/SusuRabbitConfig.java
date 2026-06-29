package com.stash.platform.susu.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ bindings for the susu disbursement consumer.
 *
 * <p>{@code susu.events} is the exchange {@code SusuEventPublisher}/
 * {@code SusuRoundFullyCollectedEventPublisher} already publish to; declared
 * here too so local/dev Spring AMQP and {@code infra/docker/rabbitmq/definitions.json}
 * stay aligned — same convention as {@code MonolithMessagingConfig} for kyc.events.
 *
 * <p>Dead-letter queue receives messages rejected without requeue. At v0.4
 * there's no requeue-on-failure from the consumer itself (see
 * {@code SusuDisbursementWorker}) — the scheduled fallback poller is the
 * retry mechanism, not RabbitMQ redelivery. The DLQ exists for the rarer
 * case of a malformed message that can't be parsed at all.
 */
@Configuration
public class SusuRabbitConfig {

    public static final String SUSU_EVENTS_EXCHANGE = "susu.events";
    public static final String DISBURSEMENT_QUEUE   = "susu.disbursement.queue";
    public static final String DISBURSEMENT_DLQ     = "susu.disbursement.dlq";
    public static final String FULLY_COLLECTED_KEY  = "susu.round.fully_collected";

    @Bean
    public TopicExchange susuEventsExchange() {
        return ExchangeBuilder.topicExchange(SUSU_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue susuDisbursementQueue() {
        return QueueBuilder.durable(DISBURSEMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DISBURSEMENT_DLQ)
                .build();
    }

    @Bean
    public Queue susuDisbursementDlq() {
        return QueueBuilder.durable(DISBURSEMENT_DLQ).build();
    }

    @Bean
    public Binding susuDisbursementBinding(Queue susuDisbursementQueue,
                                            TopicExchange susuEventsExchange) {
        return BindingBuilder.bind(susuDisbursementQueue)
                .to(susuEventsExchange)
                .with(FULLY_COLLECTED_KEY);
    }
}
