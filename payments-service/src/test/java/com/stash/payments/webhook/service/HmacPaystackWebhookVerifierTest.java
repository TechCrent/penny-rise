package com.stash.payments.webhook.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacPaystackWebhookVerifierTest {

    private static final String SECRET = "test-webhook-secret";
    private static final byte[] BODY   = """
            {"event":"charge.success","data":{"reference":"ref_abc"}}
            """.getBytes(StandardCharsets.UTF_8);

    private final HmacPaystackWebhookVerifier verifier =
            new HmacPaystackWebhookVerifier(SECRET);

    @Test
    void validSignature_returnsTrue() throws Exception {
        String sig = hmacSha512Hex(SECRET, BODY);
        assertThat(verifier.verify(BODY, sig)).isTrue();
    }

    @Test
    void wrongSignature_returnsFalse() {
        assertThat(verifier.verify(BODY, "deadbeef")).isFalse();
    }

    @Test
    void nullSignature_returnsFalse() {
        assertThat(verifier.verify(BODY, null)).isFalse();
    }

    @Test
    void tamperedBody_returnsFalse() throws Exception {
        String sig = hmacSha512Hex(SECRET, BODY);
        byte[] tampered = "tampered body".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(tampered, sig)).isFalse();
    }

    @Test
    void blankSecret_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new HmacPaystackWebhookVerifier(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAYSTACK_WEBHOOK_SECRET");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static String hmacSha512Hex(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data));
    }
}
