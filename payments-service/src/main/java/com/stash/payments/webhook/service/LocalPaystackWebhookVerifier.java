package com.stash.payments.webhook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-dev stub verifier. Accepts any request whose signature equals
 * the literal string {@code "local-dev-signature"} — WireMock and
 * integration tests use this value.
 *
 * <p>Active only when Spring profile {@code local} is set.
 * {@link HmacPaystackWebhookVerifier} is the {@code @Primary} bean
 * for all other profiles.
 */
@Component
@Profile("local")
public class LocalPaystackWebhookVerifier implements PaystackWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(LocalPaystackWebhookVerifier.class);
    static final String LOCAL_SIGNATURE = "local-dev-signature";

    @Override
    public boolean verify(byte[] rawBody, String signature) {
        boolean valid = LOCAL_SIGNATURE.equals(signature);
        if (!valid) {
            log.warn("LocalPaystackWebhookVerifier: signature mismatch (expected '{}', got '{}')",
                    LOCAL_SIGNATURE, signature);
        }
        return valid;
    }
}
