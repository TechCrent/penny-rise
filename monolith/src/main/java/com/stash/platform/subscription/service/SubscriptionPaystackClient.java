package com.stash.platform.subscription.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Test-mode only per this issue's AC ("All Paystack interactions use test
 * mode per Section 0.1"). Real production credentials/live-mode switching
 * is out of scope here.
 *
 * <p><strong>Architectural note, flagged as a judgment call, not a
 * certainty:</strong> every other external Paystack call in this codebase
 * (deposits, susu collections) is proxied through payments-service — a
 * repo-wide search found zero direct Paystack references or config keys
 * anywhere in monolith. Subscription billing never touches the ledger
 * (unlike deposits/collections, which route through Payments Service
 * specifically for ledger-integrated idempotency and webhook handling), so
 * this client lives directly in monolith rather than adding a new
 * cross-service dependency to Payments Service for something that doesn't
 * need its ledger machinery. Worth a second opinion if there's a standing
 * rule that ALL Paystack calls must go through payments-service regardless
 * of ledger involvement.
 *
 * <p><strong>Sketch-level only:</strong> Paystack's real Subscriptions API
 * request/response contract is not available in this codebase or its docs.
 * {@link #initializeTestSubscription} and {@link #createTestSubscription}
 * intentionally throw until wired to the real API — SubscriptionService's
 * tests mock this client rather than exercising a real (nonexistent) HTTP
 * integration.
 *
 * <p><strong>Two-step shape added in v0.5-031:</strong> the original
 * single-method design assumed a {@code paystackSubscriptionToken} would
 * already exist by the time a client calls upgrade — but nothing in this
 * codebase or any real Paystack integration produces one out of thin air.
 * Real Paystack subscription checkouts are a two-step handshake:
 * initialize (get an authorization URL to open in a browser), then verify
 * after the redirect. {@link #initializeTestSubscription} is the missing
 * first step; {@link #createTestSubscription} is now called with the
 * reference obtained from it, not an unexplained pre-existing token.
 */
@Component
public class SubscriptionPaystackClient {

    private final RestClient restClient;

    public SubscriptionPaystackClient(RestClient.Builder builder,
                                       @Value("${stash.paystack.test-secret-key:test-mode-not-configured}")
                                               String testSecretKey) {
        this.restClient = builder
                .baseUrl("https://api.paystack.co")
                .defaultHeader("Authorization", "Bearer " + testSecretKey)
                .build();
    }

    /** Result of initializing a Paystack checkout — open {@code authorizationUrl} in a browser. */
    public record InitializeResult(String authorizationUrl, String reference) {}

    public InitializeResult initializeTestSubscription(UUID userId) {
        throw new UnsupportedOperationException(
                "Wire to Paystack's real test-mode Subscriptions/Transaction initialize API — " +
                "sketch only, no contract available in this codebase to build against.");
    }

    /** @return the Paystack subscription_code to store as external_subscription_reference. */
    public String createTestSubscription(UUID userId, String paystackSubscriptionToken) {
        throw new UnsupportedOperationException(
                "Wire to Paystack's real test-mode Subscriptions API — sketch only, " +
                "no contract available in this codebase to build against.");
    }
}
