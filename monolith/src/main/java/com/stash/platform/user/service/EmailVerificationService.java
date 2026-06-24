package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.domain.EmailVerificationToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.EmailVerificationTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Handles email verification and resend flows.
 *
 * <p>Verification is atomic: user.emailVerifiedAt and token.consumedAt
 * are set in a single transaction. No partial state is possible.
 *
 * <p>Resend is rate-limited: max 3 tokens per user within a 1-hour window,
 * counted by tokens whose expiresAt is within the last 24h (since each token
 * has a 24h expiry, counting active tokens approximates the hourly window).
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int MAX_RESENDS_PER_HOUR = 3;
    private static final int TOKEN_BYTES           = 32;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom;
    private final String baseUrl;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    EmailSender emailSender,
                                    @Value("${stash.email.base-url:http://localhost:8080}") String baseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository  = userRepository;
        this.emailSender     = emailSender;
        this.secureRandom    = new SecureRandom();
        this.baseUrl         = baseUrl;
    }

    // ── Verify ────────────────────────────────────────────────────────────

    /**
     * Verifies an email address using the token from the verification link.
     *
     * <p>Transaction: sets email_verified_at on the user AND consumed_at
     * on the token atomically. Concurrent requests with the same token
     * are safe — the second call will find consumedAt already set and
     * return 409 rather than double-consuming.
     *
     * @param rawToken the plaintext token from the verification URL
     * @throws StashApiException 410 if expired, 409 if already consumed,
     *                           404 if token not found
     */
    @Transactional
    public void verify(String rawToken) {
        // DO NOT log rawToken
        String hash = sha256Hex(rawToken);

        EmailVerificationToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new StashApiException(
                        ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID,
                        "Verification token is invalid.",
                        HttpStatus.NOT_FOUND
                ));

        if (token.isConsumed()) {
            log.debug("Verification rejected: token already consumed userId={}",
                    token.getUserId());
            throw new StashApiException(
                    ErrorCode.AUTH_VERIFICATION_TOKEN_ALREADY_USED,
                    "This verification link has already been used. " +
                    "Please request a new verification email.",
                    HttpStatus.CONFLICT
            );
        }

        if (token.isExpired()) {
            log.debug("Verification rejected: token expired userId={}", token.getUserId());
            throw new StashApiException(
                    ErrorCode.AUTH_VERIFICATION_TOKEN_EXPIRED,
                    "This verification link has expired. " +
                    "Please request a new verification email.",
                    HttpStatus.GONE
            );
        }

        User user = userRepository.findByIdIncludingDeleted(token.getUserId())
                .orElseThrow(() -> new StashApiException(
                        ErrorCode.NOT_FOUND,
                        "User not found.",
                        HttpStatus.NOT_FOUND
                ));

        // Atomically mark token consumed and user verified
        token.setConsumedAt(Instant.now());
        tokenRepository.save(token);

        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        log.info("Email verified successfully userId={}", user.getId());
        // Note: email address NOT logged.
    }

    // ── Resend ────────────────────────────────────────────────────────────

    /**
     * Resends a verification email to the given address.
     *
     * <p>Rate limited: maximum {@value MAX_RESENDS_PER_HOUR} resend requests
     * per user per hour. Checked by counting tokens created in the last hour.
     *
     * <p>If the email is already verified, returns a 409 so the client can
     * redirect to login instead of staying on the verification-waiting screen.
     *
     * @param email the email address to resend to
     * @throws StashApiException 409 if already verified, 429 if rate limit exceeded
     */
    @Transactional
    public void resend(String email) {
        // DO NOT log the email address at INFO
        User user = userRepository.findByEmail(email.toLowerCase().strip())
                .orElse(null); // silent — don't reveal whether the email exists

        if (user == null) {
            // Return silently — no enumeration leak
            log.debug("Resend requested for unknown email — no action taken");
            return;
        }

        if (user.isEmailVerified()) {
            throw new StashApiException(
                    ErrorCode.AUTH_EMAIL_ALREADY_VERIFIED,
                    "Your email address is already verified. Please log in.",
                    HttpStatus.CONFLICT
            );
        }

        // Rate limit: count tokens issued in the last hour
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentCount = tokenRepository.countByUserIdSince(user.getId(), oneHourAgo);

        if (recentCount >= MAX_RESENDS_PER_HOUR) {
            log.warn("Resend rate limit exceeded userId={}", user.getId());
            throw new StashApiException(
                    ErrorCode.AUTH_RESEND_RATE_LIMIT_EXCEEDED,
                    "Too many verification emails requested. " +
                    "Please wait at least an hour before trying again.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        // Issue new token (DO NOT log plaintext)
        byte[] rawBytes  = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(rawBytes);
        String rawToken  = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
        String tokenHash = sha256Hex(rawToken);

        EmailVerificationToken newToken =
                new EmailVerificationToken(user.getId(), tokenHash);
        tokenRepository.save(newToken);

        String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + rawToken;
        emailSender.sendEmailVerification(user.getEmail(), user.getDisplayName(), verificationUrl);

        log.info("Verification email resent userId={}", user.getId());
        // Note: email address NOT logged.
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
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
