package com.stash.shared.correlation;

import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class CorrelationIdRabbitConfig {

    @Bean
    @Primary
    public RabbitTemplate correlationAwareRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setBeforePublishPostProcessors(correlationMessagePostProcessor());
        return template;
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
