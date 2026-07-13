package com.stash.payments.paystack.consumer.config;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "stash.paystack.subaccounts.enabled", havingValue = "true", matchIfMissing = false)
public class PaystackSubaccountConsumerConfig {

    public static final String PAYMENTS_EXCHANGE        = "payments.events";
    public static final String PROVISION_QUEUE          = "payments.paystack.subaccount.provision.queue";
    public static final String PROVISION_DLQ            = "payments.paystack.subaccount.provision.dlq";
    public static final String PROVISION_ROUTING_KEY    = "paystack.subaccount.provision.requested";

    @Bean
    public Queue paystackSubaccountProvisionDlq() {
        return QueueBuilder.durable(PROVISION_DLQ).build();
    }

    @Bean
    public Queue paystackSubaccountProvisionQueue() {
        return QueueBuilder.durable(PROVISION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", PROVISION_DLQ)
                .withArgument("x-message-ttl", 60_000)    // 60s per attempt before DLQ
                .build();
    }

    @Bean
    public TopicExchange paymentsEventsExchange() {
        return ExchangeBuilder.topicExchange(PAYMENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding paystackSubaccountProvisionBinding(
            Queue paystackSubaccountProvisionQueue,
            TopicExchange paymentsEventsExchange) {
        return BindingBuilder
                .bind(paystackSubaccountProvisionQueue)
                .to(paymentsEventsExchange)
                .with(PROVISION_ROUTING_KEY);
    }
}
