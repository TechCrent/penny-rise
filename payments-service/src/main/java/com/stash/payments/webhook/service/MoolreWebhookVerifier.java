package com.stash.payments.webhook.service;

/**
 * Verifies inbound Moolre webhook requests.
 *
 * <p>Moolre's callback auth is not fully documented — production uses a shared
 * secret header check for now. See {@link SharedSecretMoolreWebhookVerifier}.
 */
public interface MoolreWebhookVerifier {

    /**
     * @param rawBody   exact HTTP body bytes
     * @param secretHeader value of {@code X-Moolre-Secret} if present
     * @return true if the request should be accepted
     */
    boolean verify(byte[] rawBody, String secretHeader);
}
