package com.stash.kyc.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * <p>Per System Design §10.2 ("Sensitive data at rest encrypted using
 * AES-256") and this issue's explicit requirement, ghana_card_number is
 * encrypted at the application layer in addition to the managed Postgres
 * provider's storage-level encryption — defence in depth. A compromised
 * database credential alone cannot decrypt the column; the application's
 * key (held in the secret manager, never the database) is also required.
 *
 * <p>Format: base64(nonce[12 bytes] || ciphertext || authTag[16 bytes]).
 * A fresh random nonce is generated per encryption — required for GCM
 * security (nonce reuse with the same key breaks AES-GCM's guarantees).
 *
 * <p><strong>Security invariants:</strong>
 * <ul>
 *   <li>The encryption key is NEVER logged.</li>
 *   <li>Plaintext Ghana Card numbers are NEVER logged.</li>
 *   <li>Ciphertext IS safe to log/store — it's the whole point.</li>
 * </ul>
 */
@Converter
@Component
public class GhanaCardEncryptionConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(GhanaCardEncryptionConverter.class);

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS = 128;
    private static final int    NONCE_BYTES  = 12;

    private static volatile SecretKey staticKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public GhanaCardEncryptionConverter(
            @Value("${stash.kyc.ghana-card-encryption-key}") String base64Key) {
        staticKey = new SecretKeySpec(
                Base64.getDecoder().decode(base64Key), "AES");
        log.info("GhanaCardEncryptionConverter initialised with AES-256-GCM");
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) return null;

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
}
