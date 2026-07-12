package com.stash.platform.kyc.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

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
    private static final String DLX_NAME = "monolith.kyc.events.dlx";
    private static final String DLQ_ROUTING_KEY = "monolith.kyc.events.failed";
    private static final String DLQ_NAME = "monolith.kyc.events.dlq";

    @Bean
    public TopicExchange kycEventsExchange() {
        return ExchangeBuilder.topicExchange(KYC_EXCHANGE)
                .durable(true)
                .build();
    }

    // Defense-in-depth: KycUserSyncService already handles the one known
    // permanent-failure case (duplicate ghana_card_number) without throwing,
    // but any *other* unexpected exception here would otherwise redeliver
    // forever — these queues had no dead-letter config, so a single
    // unfixable message would spin RabbitMQ redelivery in a tight loop
    // indefinitely, burning CPU (observed: one such message redelivered
    // 4700+ times before the root cause above was found and fixed).
    @Bean
    public DirectExchange monolithKycEventsDlx() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue monolithKycEventsDlq() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding monolithKycEventsDlqBinding(Queue monolithKycEventsDlq,
                                                DirectExchange monolithKycEventsDlx) {
        return BindingBuilder.bind(monolithKycEventsDlq).to(monolithKycEventsDlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue monolithKycApprovedQueue() {
        return QueueBuilder.durable(MONOLITH_KYC_APPROVED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue monolithKycRejectedQueue() {
        return QueueBuilder.durable(MONOLITH_KYC_REJECTED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding monolithKycApprovedBinding(Queue monolithKycApprovedQueue,
                                              TopicExchange kycEventsExchange) {
        return BindingBuilder.bind(monolithKycApprovedQueue)
                .to(kycEventsExchange)
                .with(KYC_APPROVED_ROUTING_KEY);
    }

    @Bean
    public Binding monolithKycRejectedBinding(Queue monolithKycRejectedQueue,
                                              TopicExchange kycEventsExchange) {
        return BindingBuilder.bind(monolithKycRejectedQueue)
                .to(kycEventsExchange)
                .with(KYC_REJECTED_ROUTING_KEY);
    }

    // The shared default rabbitListenerContainerFactory (CorrelationIdRabbitConfig)
    // requeues on any exception with no retry limit — fine for most listeners,
    // but wrong here: a permanent failure (e.g. the DB constraint violation
    // this queue actually hit) would otherwise redeliver forever. Bounded
    // retry + reject-without-requeue routes it to the DLX above instead,
    // matching NotificationRabbitConfig's established pattern.
    @Bean
    public RetryOperationsInterceptor kycDecisionRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(5)
                .backOffOptions(1000, 2.0, 30000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory kycDecisionListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter,
            RetryOperationsInterceptor kycDecisionRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(kycDecisionRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
