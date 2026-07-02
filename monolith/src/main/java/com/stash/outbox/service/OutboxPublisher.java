package com.stash.outbox.service;

public interface OutboxPublisher {
    void publish(Object event);
}
