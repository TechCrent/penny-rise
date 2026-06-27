package com.stash.platform.user.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Spring application event published within the signup transaction.
 * {@link UserCreatedEventPublisher} listens and publishes to RabbitMQ
 * AFTER the transaction commits.
 */
public class UserCreatedApplicationEvent extends ApplicationEvent {

    private final UUID   userId;
    private final String email;
    private final String correlationId;

    public UserCreatedApplicationEvent(Object source,
                                       UUID userId, String email, String correlationId) {
        super(source);
        this.userId        = userId;
        this.email         = email;
        this.correlationId = correlationId;
    }

    public UUID   getUserId()        { return userId; }
    public String getEmail()         { return email; }
    public String getCorrelationId() { return correlationId; }
}
