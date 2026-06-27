package com.stash.payments.consumer.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the queue, dead-letter queue, and binding for user.created events.
 *
 * <p>The exchange {@code user.events} is owned and declared by the monolith.
 * The Payments Service declares only its consumer-side queue and binding.
 * Both services declaring the exchange is fine — RabbitMQ is idempotent on
 * exchange declarations when arguments match.
 */
@Configuration
public class UserEventsConsumerConfig {

    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String USER_CREATED_QUEUE   = "payments.user.created.queue";
    public static final String USER_CREATED_DLQ     = "payments.user.created.dlq";
    public static final String USER_CREATED_ROUTING = "user.created";

    @Bean
    public Queue userCreatedDeadLetterQueue() {
        return QueueBuilder.durable(USER_CREATED_DLQ).build();
    }

    @Bean
    public Queue userCreatedQueue() {
        return QueueBuilder.durable(USER_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", USER_CREATED_DLQ)
                .build();
    }

    @Bean
    public TopicExchange userEventsExchange() {
        return ExchangeBuilder.topicExchange(USER_EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Binding userCreatedBinding(Queue userCreatedQueue,
                                      TopicExchange userEventsExchange) {
        return BindingBuilder
                .bind(userCreatedQueue)
                .to(userEventsExchange)
                .with(USER_CREATED_ROUTING);
    }
}
