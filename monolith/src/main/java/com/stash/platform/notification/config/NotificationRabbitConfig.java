package com.stash.platform.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.List;
import java.util.stream.Stream;

/**
 * Bound to BOTH plausible exchange topologies until the §4.2 vs session-
 * primer conflict is resolved:
 *   - System Design §4.2: payments.events, kyc.events, monolith.events
 *   - Session-primer per-module model: user.events, admin.events, susu.events,
 *     vault.events, transfer.events
 * Extra bindings to exchanges nobody publishes to are harmless — Spring AMQP
 * declares them if missing; they carry no traffic. Same hedge needed
 * retroactively in audit-service's AuditMessagingConfig (v0.5-009).
 *
 * Uses Declarables (not List<Binding>) because the TopicExchange objects live
 * inline in the stream and are not individual Spring beans — RabbitAdmin
 * processes Declarables but skips List<Binding>.
 */
@Configuration
public class NotificationRabbitConfig {

    public static final String QUEUE_NAME        = "notification.events.incoming";
    public static final String DLX_NAME          = "notification.events.dlx";
    public static final String DLQ_NAME          = "notification.events.dlq";
    public static final String DLQ_ROUTING_KEY   = "notification.events.failed";

    private static final List<String> SOURCE_EXCHANGES = List.of(
            "payments.events", "kyc.events", "monolith.events",
            "user.events", "admin.events", "susu.events", "vault.events", "transfer.events");

    @Bean
    public DirectExchange notificationDlx() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding notificationDlqBinding(Queue notificationDlq, DirectExchange notificationDlx) {
        return BindingBuilder.bind(notificationDlq).to(notificationDlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue notificationIncomingQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Declarables notificationSourceBindings(Queue notificationIncomingQueue) {
        return new Declarables(
                SOURCE_EXCHANGES.stream()
                        .flatMap(name -> {
                            TopicExchange exchange = new TopicExchange(name, true, false);
                            Binding binding = BindingBuilder.bind(notificationIncomingQueue)
                                    .to(exchange).with("#");
                            return Stream.of(exchange, binding);
                        })
                        .toList());
    }

    @Bean
    public RetryOperationsInterceptor notificationRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(5)
                .backOffOptions(1000, 2.0, 30000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory notificationListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor notificationRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(notificationRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
