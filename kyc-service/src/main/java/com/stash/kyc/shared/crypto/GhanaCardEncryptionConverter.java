package com.stash.kyc.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter — AES-256-GCM encryption for Ghana Card numbers.
 *
 * <p>Key material is loaded by {@link GhanaCardCryptoConfig} at startup
 * because Hibernate instantiates converters outside Spring's DI container.
 *
 * <p>Format: base64(nonce[12 bytes] || ciphertext || authTag[16 bytes]).
 */
@Converter
public class GhanaCardEncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS = 128;
    private static final int    NONCE_BYTES  = 12;
    private static final int    KEY_BYTES    = 32;

    private static volatile SecretKey staticKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Called once at startup by {@link GhanaCardCryptoConfig}. */
    public static void initialize(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "stash.kyc.ghana-card-encryption-key must decode to exactly "
                            + KEY_BYTES + " bytes for AES-256");
        }
        staticKey = new SecretKeySpec(keyBytes, "AES");
    }

    /** No-arg constructor used by Hibernate. */
    public GhanaCardEncryptionConverter() {}

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) return null;
        requireKey();

        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, staticKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt Ghana Card number", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encoded) {
        if (encoded == null) return null;
        requireKey();

        try {
            byte[] combined = Base64.getDecoder().decode(encoded);

            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[combined.length - NONCE_BYTES];
            System.arraycopy(combined, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(combined, NONCE_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, staticKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));

            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt Ghana Card number", e);
        }
    }

    private static void requireKey() {
        if (staticKey == null) {
            throw new IllegalStateException(
                    "GhanaCardEncryptionConverter not initialised — "
                            + "stash.kyc.ghana-card-encryption-key must be set");
        }
    }
}
