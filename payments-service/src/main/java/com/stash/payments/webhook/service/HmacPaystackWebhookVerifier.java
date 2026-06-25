package com.stash.payments.webhook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Production HMAC-SHA512 verifier using the Paystack webhook secret.
 *
 * <p>The secret is distinct from the API secret key — it is the webhook
 * signing secret configured in the Paystack dashboard under
 * Settings → Webhooks. Delivered via {@code PAYSTACK_WEBHOOK_SECRET}
 * environment variable.
 *
 * <p>Uses {@link MessageDigest#isEqual} (constant-time comparison) to
 * prevent timing attacks.
 */
@Component
@Primary
public class HmacPaystackWebhookVerifier implements PaystackWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(HmacPaystackWebhookVerifier.class);
    private static final String ALGORITHM = "HmacSHA512";

    private final byte[] secretBytes;

    public HmacPaystackWebhookVerifier(
            @Value("${paystack.webhook-secret}") String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "PAYSTACK_WEBHOOK_SECRET must be set. " +
                    "Get it from Paystack dashboard → Settings → Webhooks.");
        }
        this.secretBytes = webhookSecret.getBytes(StandardCharsets.UTF_8);
        log.info("HmacPaystackWebhookVerifier initialised (HMAC-SHA512)");
    }

    @Override
    public boolean verify(byte[] rawBody, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));
            byte[] expected = mac.doFinal(rawBody);
            byte[] actual   = hexToBytes(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            log.warn("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
