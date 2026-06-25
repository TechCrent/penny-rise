package com.stash.payments.consumer.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Inbound event emitted by the monolith's signup endpoint when a new
 * user account is successfully created. Consumed by the Payments Service
 * to provision a USER_WALLET ledger account.
 *
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} — the monolith
 * may add new fields to this event in future versions; the consumer
 * must not break on unknown fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserCreatedEvent(
        UUID   userId,
        String email,
        String correlationId
) {}
