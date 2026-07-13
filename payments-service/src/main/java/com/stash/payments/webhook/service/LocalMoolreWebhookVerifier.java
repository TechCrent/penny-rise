package com.stash.payments.webhook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-dev verifier — accepts all webhook deliveries so tunnel-less
 * local testing and WireMock can exercise the handler without a secret.
 *
 * <p>Active only when Spring profile {@code local} is set.
 */
@Component
@Profile("local")
public class LocalMoolreWebhookVerifier implements MoolreWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(LocalMoolreWebhookVerifier.class);

    @Override
    public boolean verify(byte[] rawBody, String secretHeader) {
        log.debug("LocalMoolreWebhookVerifier: accepting webhook (local profile)");
        return true;
    }
}
