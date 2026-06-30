package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.api.dto.SignupResponse;
import com.stash.platform.user.domain.EmailVerificationToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.event.UserCreatedApplicationEvent;
import com.stash.platform.user.repository.EmailVerificationTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SignupService {

    private static final Logger log = LoggerFactory.getLogger(SignupService.class);
    private static final int VERIFICATION_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordHasher passwordHasher;
    private final EmailSender emailSender;
    private final ApplicationEventPublisher eventPublisher;
    private final BetaAllowlistService betaAllowlistService;
    private final SecureRandom secureRandom;
    private final String baseUrl;

    public SignupService(UserRepository userRepository,
                         EmailVerificationTokenRepository tokenRepository,
                         PasswordHasher passwordHasher,
                         EmailSender emailSender,
                         ApplicationEventPublisher eventPublisher,
                         BetaAllowlistService betaAllowlistService,
                         @Value("${stash.email.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository      = userRepository;
        this.tokenRepository     = tokenRepository;
        this.passwordHasher      = passwordHasher;
        this.emailSender         = emailSender;
        this.eventPublisher      = eventPublisher;
        this.betaAllowlistService = betaAllowlistService;
        this.secureRandom        = new SecureRandom();
        this.baseUrl             = baseUrl;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        betaAllowlistService.assertAllowed(request.email());

        String normalisedEmail = request.email().toLowerCase().strip();

        if (userRepository.existsByEmail(normalisedEmail)) {
            log.debug("Signup rejected: email already registered");
            throw new StashApiException(
                    ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED,
                    "An account with this email address already exists.",
                    HttpStatus.CONFLICT
            );
        }

        String passwordHash = passwordHasher.hash(request.password());
        User user = new User(normalisedEmail, passwordHash, request.displayName().strip());

        if (request.referralCode() != null && !request.referralCode().isBlank()) {
            user.setReferredByCode(request.referralCode().strip());
        }

        userRepository.save(user);

        // Fires AFTER_COMMIT via UserCreatedEventPublisher — never inside this tx
        eventPublisher.publishEvent(
                new UserCreatedApplicationEvent(this, user.getId(), user.getEmail(), null));

        byte[] rawBytes = new byte[VERIFICATION_TOKEN_BYTES];
        secureRandom.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
        String tokenHash = sha256Hex(rawToken.getBytes(StandardCharsets.UTF_8));

        EmailVerificationToken verificationToken = new EmailVerificationToken(user.getId(), tokenHash);
        tokenRepository.save(verificationToken);

        log.info("User registered successfully userId={}", user.getId());

        String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + rawToken;
        emailSender.sendEmailVerification(
                normalisedEmail,
                user.getDisplayName(),
                verificationUrl
        );

        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                "Account created. Please check your email to verify your account."
        );
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