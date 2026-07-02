package com.stash.challenge.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class ChallengeRabbitConfig {

    public static final String QUEUE_NAME       = "challenge.deposit.progress";
    public static final String DLX_NAME         = "challenge.deposit.progress.dlx";
    public static final String DLQ_NAME         = "challenge.deposit.progress.dlq";
    public static final String DLQ_ROUTING_KEY  = "challenge.deposit.progress.failed";

    @Bean
    public DirectExchange challengeDlx() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue challengeDlq() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding challengeDlqBinding(Queue challengeDlq, DirectExchange challengeDlx) {
        return BindingBuilder.bind(challengeDlq).to(challengeDlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue challengeDepositQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange",    DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public TopicExchange paymentsEventsExchange() {
        return new TopicExchange("payments.events", true, false);
    }

    @Bean
    public Binding challengeDepositBinding(Queue challengeDepositQueue,
                                            TopicExchange paymentsEventsExchange) {
        // Narrow binding — only deposit.completed, not "#" like the
        // notification catch-all. This module only reacts to deposits.
        return BindingBuilder.bind(challengeDepositQueue)
                .to(paymentsEventsExchange)
                .with("deposit.completed");
    }

    @Bean
    public RetryOperationsInterceptor challengeRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(5)
                .backOffOptions(1000, 2.0, 30000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory challengeListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryOperationsInterceptor challengeRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(challengeRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
