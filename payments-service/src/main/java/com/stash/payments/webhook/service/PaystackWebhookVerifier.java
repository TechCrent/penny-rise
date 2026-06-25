package com.stash.payments.webhook.service;

/**
 * Verifies that an inbound Paystack webhook request was sent by Paystack
 * and has not been tampered with.
 *
 * <p>Paystack signs the raw request body using HMAC-SHA512 with the
 * webhook secret configured in the Paystack dashboard. The signature
 * is delivered in the {@code x-paystack-signature} HTTP header.
 */
public interface PaystackWebhookVerifier {

    /**
     * Returns {@code true} if the signature matches the expected HMAC-SHA512
     * of the raw body using the configured webhook secret.
     *
     * @param rawBody   the exact bytes of the HTTP request body
     * @param signature the value of the {@code x-paystack-signature} header
     */
    boolean verify(byte[] rawBody, String signature);
}
