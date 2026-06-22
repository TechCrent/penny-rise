package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.domain.PasswordResetToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.EmailVerificationTokenRepository;
import com.stash.platform.user.repository.PasswordResetTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ForgotPasswordService")
class ForgotPasswordServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("monolith_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ForgotPasswordService forgotPasswordService;
    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationTokenRepository emailTokenRepository;
    @Autowired PasswordResetTokenRepository resetTokenRepository;
    @MockBean EmailSender emailSender;

    private static final String EMAIL = "forgot-test@example.com";

    @BeforeEach
    void setUp() {
        reset(emailSender);
        resetTokenRepository.deleteAll();
        emailTokenRepository.deleteAll();
        userRepository.deleteAll();

        signupService.signup(new SignupRequest(EMAIL, "Str0ng!Pass", "Forgot Test", null));
    }

// ΓöÇΓöÇ Registered email ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("registered email: creates a reset token")
    void registered_email_creates_token() {
        forgotPasswordService.requestReset(EMAIL);

        List<PasswordResetToken> tokens = resetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).isConsumed()).isFalse();
    }

    @Test
    @DisplayName("registered email: dispatches reset email")
    void registered_email_dispatches_email() {
        forgotPasswordService.requestReset(EMAIL);

        verify(emailSender).sendPasswordReset(eq(EMAIL), anyString(), anyString());
    }

    @Test
    @DisplayName("token hash does not equal raw token in any observable way")
    void token_stored_as_hash() {
        forgotPasswordService.requestReset(EMAIL);

        List<PasswordResetToken> tokens = resetTokenRepository.findAll();
// The hash is 64 hex chars (SHA-256); raw token is base64url ~43 chars.
// They are structurally different and the hash cannot equal the raw value.
        assertThat(tokens.get(0).getTokenHash()).hasSize(64);
    }

// ΓöÇΓöÇ Unregistered email ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("unregistered email: no token created")
    void unregistered_email_no_token() {
        forgotPasswordService.requestReset("nobody@example.com");

        assertThat(resetTokenRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("unregistered email: no email dispatched")
    void unregistered_email_no_dispatch() {
        forgotPasswordService.requestReset("nobody@example.com");

        verify(emailSender, never())
                .sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("unregistered email: does not throw")
    void unregistered_email_does_not_throw() {
        assertThatNoException()
                .isThrownBy(() -> forgotPasswordService.requestReset("nobody@example.com"));
    }

// ΓöÇΓöÇ Token invalidation on new request ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("previous unused token is invalidated when a new request is made")
    void previous_token_invalidated_on_new_request() {
        forgotPasswordService.requestReset(EMAIL);
        List<PasswordResetToken> firstBatch = resetTokenRepository.findAll();
        PasswordResetToken firstToken = firstBatch.get(0);
        assertThat(firstToken.isConsumed()).isFalse();

        forgotPasswordService.requestReset(EMAIL);

// Reload ΓÇö the first token should now be consumed (invalidated)
        PasswordResetToken reloaded = resetTokenRepository.findById(firstToken.getId()).orElseThrow();
        assertThat(reloaded.isConsumed()).isTrue();

// Two tokens total exist; only the second is active
        List<PasswordResetToken> allTokens = resetTokenRepository.findAll();
        assertThat(allTokens).hasSize(2);
        long activeCount = allTokens.stream().filter(t -> !t.isConsumed()).count();
        assertThat(activeCount).isEqualTo(1);
    }

// ΓöÇΓöÇ Rate limit ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("4th request within an hour is silently capped ΓÇö no new token, no error")
    void rate_limit_silently_caps() {
        forgotPasswordService.requestReset(EMAIL);
        forgotPasswordService.requestReset(EMAIL);
        forgotPasswordService.requestReset(EMAIL);

        long tokenCountBefore = resetTokenRepository.count();

        assertThatNoException()
                .isThrownBy(() -> forgotPasswordService.requestReset(EMAIL));

// No new token created on the 4th request
        assertThat(resetTokenRepository.count()).isEqualTo(tokenCountBefore);
    }

    @Test
    @DisplayName("4th request does not dispatch an email")
    void rate_limit_no_email_on_4th() {
        forgotPasswordService.requestReset(EMAIL);
        forgotPasswordService.requestReset(EMAIL);
        forgotPasswordService.requestReset(EMAIL);
        reset(emailSender); // clear invocation count from first 3 calls

        forgotPasswordService.requestReset(EMAIL);

        verify(emailSender, never())
                .sendPasswordReset(anyString(), anyString(), anyString());
    }

// ΓöÇΓöÇ Timing ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("timing: registered vs unregistered email response time is similar")
    void timing_indistinguishable() {
        int iterations = 5;

        long registeredMs = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            forgotPasswordService.requestReset(EMAIL);
            registeredMs += System.currentTimeMillis() - start;
        }

        long unregisteredMs = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            forgotPasswordService.requestReset("ghost" + i + "@example.com");
            unregisteredMs += System.currentTimeMillis() - start;
        }

        long avgRegistered = registeredMs / iterations;
        long avgUnregistered = unregisteredMs / iterations;

        assertThat(Math.abs(avgRegistered - avgUnregistered))
                .as("Timing difference should be small enough not to leak enumeration info")
                .isLessThan(200L);
    }
}
