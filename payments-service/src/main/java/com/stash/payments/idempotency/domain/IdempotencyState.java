package com.stash.payments.idempotency.domain;

public enum IdempotencyState {
    PROCESSING,
    COMPLETED,
    FAILED
}
