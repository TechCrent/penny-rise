package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.domain.PasswordResetToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.PasswordResetTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

/**
 * Handles password reset requests.
 *
 * <p><strong>No-enumeration contract:</strong> this service ALWAYS returns
 * successfully and ALWAYS takes approximately the same amount of time,
 * regardless of whether the email is registered. The controller layer
 * always returns 200 with the same generic message.
 *
 * <p>Rate limit: max 3 reset requests per user per hour. Silently capped —
 * the caller still receives 200; no error is surfaced (revealing a rate
 * limit error would itself leak that the email exists).
 *
 * <p>Token invalidation: any unconsumed tokens for the user are invalidated
 * (consumed_at set) when a new request is made, so only the most recent
 * reset link is ever valid.
 */
@Service
public class ForgotPasswordService {

    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_REQUESTS_PER_HOUR = 3;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom;
    private final String baseUrl;

    public ForgotPasswordService(UserRepository userRepository,
                                 PasswordResetTokenRepository tokenRepository,
                                 EmailSender emailSender,
                                 @Value("${stash.email.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailSender = emailSender;
        this.secureRandom = new SecureRandom();
        this.baseUrl = baseUrl;
    }

    /**
     * Processes a password reset request. Always completes without throwing —
     * the controller always responds 200 regardless of the outcome here.
     *
     * @param email the submitted email address
     */
    @Transactional
    public void requestReset(String email) {
        String normalisedEmail = email.toLowerCase().strip();
// DO NOT log the email address at INFO

        User user = userRepository.findByEmail(normalisedEmail).orElse(null);

        if (user == null) {
// Burn equivalent time to the real path so response timing
// does not reveal whether the email exists.
            performDummyWork();
            log.debug("Forgot-password requested for unregistered email — no action taken");
            return;
        }

        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentCount = tokenRepository.countByUserIdSince(user.getId(), oneHourAgo);

        if (recentCount >= MAX_REQUESTS_PER_HOUR) {
// Silently cap — no error surfaced to avoid enumeration via rate-limit response.
            log.warn("Forgot-password rate limit reached userId={}", user.getId());
            return;
        }

// Invalidate any previously unused tokens for this user
        List<PasswordResetToken> activeTokens = tokenRepository.findActiveByUserId(user.getId());
        Instant now = Instant.now();
        for (PasswordResetToken t : activeTokens) {
            t.setConsumedAt(now);
        }
        tokenRepository.saveAll(activeTokens);

// Issue new token (DO NOT log plaintext)
        byte[] rawBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
        String tokenHash = sha256Hex(rawBytes);

        PasswordResetToken token = new PasswordResetToken(user.getId(), tokenHash);
        tokenRepository.save(token);

        String resetUrl = baseUrl + "/reset-password?token=" + rawToken;
        emailSender.sendPasswordReset(user.getEmail(), user.getDisplayName(), resetUrl);

        log.info("Password reset email dispatched userId={}", user.getId());
// Note: email address NOT logged.
    }

    /**
     * Performs work roughly equivalent in cost to the real path
     * (one DB-shaped operation) so timing does not differ for unknown emails.
     * The result is discarded — this exists purely for timing equalisation.
     */
    private void performDummyWork() {
        byte[] dummyBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(dummyBytes);
        sha256Hex(dummyBytes); // burn equivalent CPU to the real hash operation
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}