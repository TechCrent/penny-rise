package com.stash.payments.idempotency.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a SHA-256 hash of the idempotency key scope:
 * {@code HTTP_METHOD + "|" + request_path + "|" + body_bytes}.
 *
 * <p>The method and path are included so that a client reusing
 * the same Idempotency-Key header value for a different operation
 * (a client bug) is detected and rejected with 422.
 */
@Component
public class RequestHasher {

    public String hash(String method, String path, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((method + "|" + path + "|").getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in all JVMs
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
