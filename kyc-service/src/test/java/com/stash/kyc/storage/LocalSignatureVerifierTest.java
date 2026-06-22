package com.stash.kyc.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalSignatureVerifier")
class LocalSignatureVerifierTest {

    private final LocalSignatureVerifier verifier = new LocalSignatureVerifier();

    @Test
    @DisplayName("valid dev signature passes")
    void valid_signature_passes() {
        boolean result = verifier.verify(
                "{}".getBytes(), "local-dev-signature-do-not-use-in-prod");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("invalid signature fails")
    void invalid_signature_fails() {
        boolean result = verifier.verify("{}".getBytes(), "wrong-signature");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("missing signature fails")
    void missing_signature_fails() {
        boolean result = verifier.verify("{}".getBytes(), "");
        assertThat(result).isFalse();
    }
}
