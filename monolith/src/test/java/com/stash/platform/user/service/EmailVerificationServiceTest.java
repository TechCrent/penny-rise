package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.domain.EmailVerificationToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.EmailVerificationTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.StashApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("EmailVerificationService")
class EmailVerificationServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("monolith_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired EmailVerificationService verificationService;
    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;
    @MockBean  EmailSender emailSender;

    private String validRawToken;
    private User registeredUser;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        // Register a user and capture the verification token
        signupService.signup(new SignupRequest(
                "verify-test@example.com", "Str0ng!Pass", "Verify Test", null));

        registeredUser = userRepository.findByEmail("verify-test@example.com").orElseThrow();

        // Capture the token hash from the DB and derive the raw token
        // In a real test we'd capture the URL from the email mock; here we re-issue directly
        validRawToken = issueTestToken(registeredUser);
    }

    /**
     * Helper: issues a real token via the token repository so we have the raw value.
     */
    private String issueTestToken(User user) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw  = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
        tokenRepository.save(new EmailVerificationToken(user.getId(), hash));
        return raw;
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy path: sets email_verified_at and marks token consumed")
    void happy_path() {
        verificationService.verify(validRawToken);

        User updated = userRepository.findByIdIncludingDeleted(registeredUser.getId()).orElseThrow();
        assertThat(updated.getEmailVerifiedAt()).isNotNull();
        assertThat(updated.isEmailVerified()).isTrue();

        // Token is consumed
        String hash = sha256Hex(validRawToken.getBytes(StandardCharsets.UTF_8));
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash).orElseThrow();
        assertThat(token.isConsumed()).isTrue();
    }

    // ── Already consumed ──────────────────────────────────────────────────

    @Test
    @DisplayName("already-consumed token returns 409 AUTH_VERIFICATION_TOKEN_ALREADY_USED")
    void already_consumed_returns_409() {
        verificationService.verify(validRawToken);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> verificationService.verify(validRawToken))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("AUTH_VERIFICATION_TOKEN_ALREADY_USED");
                });
    }

    // ── Expired token ─────────────────────────────────────────────────────

    @Test
    @DisplayName("expired token returns 410 AUTH_VERIFICATION_TOKEN_EXPIRED")
    void expired_token_returns_410() {
        // Directly manipulate the token to be expired
        String hash = sha256Hex(validRawToken.getBytes(StandardCharsets.UTF_8));
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(60)); // expired
        tokenRepository.save(token);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> verificationService.verify(validRawToken))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.GONE);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("AUTH_VERIFICATION_TOKEN_EXPIRED");
                });
    }

    // ── Unknown token ─────────────────────────────────────────────────────

    @Test
    @DisplayName("unknown token returns 404 AUTH_VERIFICATION_TOKEN_INVALID")
    void unknown_token_returns_404() {
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> verificationService.verify("completely-unknown-token-value"))
                .satisfies(ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Cannot reuse consumed token ───────────────────────────────────────

    @Test
    @DisplayName("consumed token cannot be reused — second attempt fails")
    void consumed_token_cannot_be_reused() {
        verificationService.verify(validRawToken); // first use — succeeds

        // Second attempt must fail
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> verificationService.verify(validRawToken));

        // email_verified_at still set from the first successful verification
        User user = userRepository.findByIdIncludingDeleted(registeredUser.getId()).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
    }

    // ── Resend ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resend dispatches a new verification email")
    void resend_dispatches_email() {
        verificationService.resend("verify-test@example.com");

        verify(emailSender, atLeastOnce())
                .sendEmailVerification(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resend creates a new token in the DB")
    void resend_creates_new_token() {
        long beforeCount = tokenRepository.count();
        verificationService.resend("verify-test@example.com");
        assertThat(tokenRepository.count()).isGreaterThan(beforeCount);
    }

    @Test
    @DisplayName("resend on already-verified email returns 409 AUTH_EMAIL_ALREADY_VERIFIED")
    void resend_on_verified_email_returns_409() {
        verificationService.verify(validRawToken); // verify first

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> verificationService.resend("verify-test@example.com"))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("AUTH_EMAIL_ALREADY_VERIFIED");
                });
    }

    @Test
    @DisplayName("resend rate limit: 4th request within an hour returns 429")
    void resend_rate_limit_exceeded() {
        // 3 resends are allowed
        verificationService.resend("verify-test@example.com");
        verificationService.resend("verify-test@example.com");
        verificationService.resend("verify-test@example.com");

        // 4th must be rejected
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> verificationService.resend("verify-test@example.com"))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("AUTH_RESEND_RATE_LIMIT_EXCEEDED");
                });
    }

    @Test
    @DisplayName("resend for unknown email is silent — no error, no email")
    void resend_unknown_email_silent() {
        assertThatNoException()
                .isThrownBy(() -> verificationService.resend("nobody@example.com"));

        verify(emailSender, never())
                .sendEmailVerification(anyString(), anyString(), anyString());
    }

    // ── Private helper ────────────────────────────────────────────────────

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
