package com.stash.payments.webhook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Production Moolre webhook verifier.
 *
 * <p><strong>TODO:</strong> Moolre docs do not document HMAC/signature headers.
 * Until they do, we require header {@code X-Moolre-Secret} to equal
 * {@code moolre.webhook-secret}. If the secret is blank, verification fails
 * closed (rejects all) outside the local profile.
 *
 * <p>Not active on {@code local} — {@link LocalMoolreWebhookVerifier} handles that.
 */
@Component
@Primary
@Profile("!local")
public class SharedSecretMoolreWebhookVerifier implements MoolreWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(SharedSecretMoolreWebhookVerifier.class);

    private final byte[] expectedSecret;

    public SharedSecretMoolreWebhookVerifier(
            @Value("${moolre.webhook-secret:}") String webhookSecret) {
        this.expectedSecret = webhookSecret != null
                ? webhookSecret.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (this.expectedSecret.length == 0) {
            log.warn("SharedSecretMoolreWebhookVerifier: moolre.webhook-secret is blank — " +
                    "all webhooks will be rejected until configured");
        } else {
            log.info("SharedSecretMoolreWebhookVerifier initialised (X-Moolre-Secret header)");
        }
    }

    @Override
    public boolean verify(byte[] rawBody, String secretHeader) {
        if (expectedSecret.length == 0) {
            return false;
        }
        if (!StringUtils.hasText(secretHeader)) {
            log.warn("Moolre webhook rejected: missing X-Moolre-Secret header");
            return false;
        }
        byte[] actual = secretHeader.getBytes(StandardCharsets.UTF_8);
        boolean ok = MessageDigest.isEqual(expectedSecret, actual);
        if (!ok) {
            log.warn("Moolre webhook rejected: X-Moolre-Secret mismatch");
        }
        return ok;
    }
}
