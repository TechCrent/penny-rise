package com.stash.challenge.event;

import com.stash.outbox.service.OutboxPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class ChallengeOutboxPublisher implements OutboxPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public ChallengeOutboxPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(Object event) {
        eventPublisher.publishEvent(event);
    }
}
