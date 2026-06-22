package com.stash.kyc.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local-dev signature verifier. Since local "uploads" are confirmed by
 * the test/dev client directly (not a real Supabase webhook), this
 * implementation accepts a fixed dev-only header value rather than
 * performing real HMAC verification.
 *
 * <p><strong>Never active outside local development.</strong> The real
 * Supabase-backed verifier (wired when {@code stash.kyc.storage.provider=supabase})
 * performs genuine HMAC-SHA256 verification against Supabase's webhook secret.
 */
@Component
@ConditionalOnProperty(name = "stash.kyc.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalSignatureVerifier implements DocumentUploadSignatureVerifier {

    private static final String LOCAL_DEV_SIGNATURE = "local-dev-signature-do-not-use-in-prod";

    @Override
    public boolean verify(byte[] rawBody, String signatureHeader) {
        return LOCAL_DEV_SIGNATURE.equals(signatureHeader);
    }
}
