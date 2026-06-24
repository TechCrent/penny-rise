package com.stash.payments.outbox.domain;

public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
