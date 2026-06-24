package com.stash.shared.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures RabbitMQ's {@link RabbitTemplate} to automatically attach
 * the current correlation ID to every outbound message's properties.
 *
 * <p>The correlation ID is set on the AMQP {@code MessageProperties}
 * via a {@link MessagePostProcessor} applied before the message is sent.
 *
 * <p>This means every event published to RabbitMQ carries the same
 * correlation ID as the inbound HTTP request that triggered the publish,
 * enabling end-to-end tracing across services and through the audit log.
 *
 * <p>JSON conversion is configured here because this module supplies the
 * {@code @Primary} {@link RabbitTemplate}; without an explicit converter,
 * Spring AMQP falls back to {@code SimpleMessageConverter}, which cannot
 * serialise the domain event records published by kyc-service.
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class CorrelationIdRabbitConfig {

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @Primary
    public RabbitTemplate correlationAwareRabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setBeforePublishPostProcessors(correlationMessagePostProcessor());
        return template;
    }

    @Bean
    @Primary
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }

    private MessagePostProcessor correlationMessagePostProcessor() {
        return message -> {
            String correlationId = CorrelationContext.get();
            if (correlationId != null && !correlationId.isBlank()) {
                message.getMessageProperties().setCorrelationId(correlationId);
                message.getMessageProperties()
                        .getHeaders()
                        .put(CorrelationContext.MDC_KEY, correlationId);
            }
            return message;
        };
    }
}
