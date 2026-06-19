package com.stash.platform.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Single point of responsibility for password hashing and verification.
 *
 * <p>BCrypt at cost factor {@code stash.security.bcrypt-cost} (default 12,
 * per System Design §10.2). The cost factor is configurable via application
 * property so it can be bumped in a future migration without a code change.
 *
 * <p><strong>Security invariants — never violate:</strong>
 * <ul>
 *   <li>Raw passwords are NEVER passed to any logger at any level.</li>
 *   <li>Raw passwords are NEVER stored, returned, or included in exceptions.</li>
 *   <li>The only string stored is the BCrypt hash output from {@link #hash}.</li>
 * </ul>
 *
 * <p>BCrypt properties:
 * <ul>
 *   <li>Cost 12 ≈ 250ms per hash on modern hardware — intentionally slow.</li>
 *   <li>Each hash embeds a random salt; two hashes of the same password differ.</li>
 *   <li>The hash is self-contained: {@link #verify} extracts the salt and cost
 *       from the stored hash string automatically.</li>
 * </ul>
 */
@Service
public class PasswordHasher {

    // The logger intentionally has NO methods called with password values.
    // If you add logging here, ensure no parameter contains a raw password.
    private static final Logger log = LoggerFactory.getLogger(PasswordHasher.class);

    private final BCryptPasswordEncoder encoder;

    public PasswordHasher(@Value("${stash.security.bcrypt-cost:12}") int bcryptCost) {
        this.encoder = new BCryptPasswordEncoder(bcryptCost);
        log.info("PasswordHasher initialised with BCrypt cost factor {}", bcryptCost);
        // Note: bcryptCost is logged, not the password. This is intentional.
    }

    /**
     * Hashes a plaintext password using BCrypt.
     *
     * <p>The returned string is in the format {@code $2a$12$<salt><hash>} and
     * is safe to store directly in {@code user_module.users.password_hash}.
     *
     * @param plaintext the raw password — must not be null or empty
     * @return a BCrypt hash string
     * @throws IllegalArgumentException if plaintext is null or blank
     */
    public String hash(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Password must not be null or blank");
        }
        // DO NOT log plaintext here or anywhere in this method.
        return encoder.encode(plaintext);
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     *
     * <p>Uses constant-time comparison internally to prevent timing attacks.
     *
     * @param plaintext  the raw password to verify — must not be null
     * @param storedHash the BCrypt hash string from the database — must not be null
     * @return {@code true} if the password matches the hash, {@code false} otherwise
     */
    public boolean verify(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null) {
            return false;
        }
        // DO NOT log plaintext here or anywhere in this method.
        // The result (true/false) is safe to log; the inputs are not.
        return encoder.matches(plaintext, storedHash);
    }
}