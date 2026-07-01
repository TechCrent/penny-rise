package com.stash.audit.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class AuditMessagingConfig {

    public static final String QUEUE_NAME       = "audit.events.incoming";
    public static final String DLX_NAME         = "audit.events.dlx";
    public static final String DLQ_NAME         = "audit.events.dlq";
    public static final String DLQ_ROUTING_KEY  = "audit.events.failed";

    /**
     * Every exchange this consumer subscribes to. Declaring them here is
     * idempotent — RabbitMQ allows redeclaration as long as type/durability
     * match the producing service's declaration. A mismatch (e.g. non-durable
     * elsewhere) will surface as a channel error at startup — fix the mismatch,
     * don't change durability here.
     */
    private static final List<String> SOURCE_EXCHANGES = List.of(
            "payments.events", "kyc.events", "user.events",
            "admin.events", "susu.events", "vault.events", "transfer.events"
    );

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue auditIncomingQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * One binding per source exchange, catch-all pattern within each.
     * Uses Declarables so RabbitAdmin processes all exchanges + bindings
     * from a single @Bean method.
     */
    @Bean
    public Declarables auditSourceBindings(Queue auditIncomingQueue) {
        List<Declarable> declarations = SOURCE_EXCHANGES.stream()
                .flatMap(exchangeName -> {
                    TopicExchange exchange = new TopicExchange(exchangeName, true, false);
                    Binding binding = BindingBuilder.bind(auditIncomingQueue).to(exchange).with("#");
                    return Stream.of(exchange, binding);
                })
                .collect(Collectors.toList());
        return new Declarables(declarations);
    }

    /**
     * 5 attempts, exponential backoff starting at 1s (capped at 30s), then
     * reject-without-requeue → DLQ via x-dead-letter-exchange.
     */
    @Bean
    public RetryOperationsInterceptor auditRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(5)
                .backOffOptions(1000, 2.0, 30000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor auditRetryInterceptor,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(auditRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
