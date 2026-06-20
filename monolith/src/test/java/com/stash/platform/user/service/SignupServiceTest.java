package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.api.dto.SignupResponse;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SignupService")
class SignupServiceTest {

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

    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;
    @MockBean  EmailSender emailSender;

    @BeforeEach
    void cleanUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("happy path: creates user, token, dispatches email, returns 201 body")
    void happy_path() {
        var request = new SignupRequest(
                "Alice@example.com", "Str0ng!Pass", "Alice", null);

        SignupResponse response = signupService.signup(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.message()).isNotBlank();

        assertThat(userRepository.findByEmail("alice@example.com")).isPresent();
        assertThat(tokenRepository.findAll()).hasSize(1);
        verify(emailSender).sendEmailVerification(eq("alice@example.com"), eq("Alice"), anyString());
    }

    @Test
    @DisplayName("email is normalised to lowercase")
    void email_normalised_to_lowercase() {
        signupService.signup(new SignupRequest(
                "BOB@EXAMPLE.COM", "Str0ng!Pass", "Bob", null));

        assertThat(userRepository.findByEmail("bob@example.com")).isPresent();
        assertThat(userRepository.findByEmail("BOB@EXAMPLE.COM")).isEmpty();
    }

    @Test
    @DisplayName("password is stored as a hash, not plaintext")
    void password_stored_as_hash() {
        signupService.signup(new SignupRequest(
                "carol@example.com", "Str0ng!Pass", "Carol", null));

        User user = userRepository.findByEmail("carol@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("Str0ng!Pass");
    }

    @Test
    @DisplayName("new user has kyc_status=PENDING and account_status=ACTIVE")
    void correct_default_status() {
        signupService.signup(new SignupRequest(
                "dave@example.com", "Str0ng!Pass", "Dave", null));

        User user = userRepository.findByEmail("dave@example.com").orElseThrow();
        assertThat(user.getKycStatus().name()).isEqualTo("PENDING");
        assertThat(user.getAccountStatus().name()).isEqualTo("ACTIVE");
        assertThat(user.getEmailVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("valid referral code is stored on the user row")
    void referral_code_stored() {
        signupService.signup(new SignupRequest(
                "eve@example.com", "Str0ng!Pass", "Eve", "FRIEND50"));

        User user = userRepository.findByEmail("eve@example.com").orElseThrow();
        assertThat(user.getReferredByCode()).isEqualTo("FRIEND50");
    }

    @Test
    @DisplayName("invalid/blank referral code is silently ignored — no error")
    void invalid_referral_code_ignored() {
        assertThatNoException().isThrownBy(() ->
                signupService.signup(new SignupRequest(
                        "frank@example.com", "Str0ng!Pass", "Frank", "   ")));

        User user = userRepository.findByEmail("frank@example.com").orElseThrow();
        assertThat(user.getReferredByCode()).isNull();
    }

    @Test
    @DisplayName("duplicate email returns 409 with AUTH_EMAIL_ALREADY_REGISTERED")
    void duplicate_email_returns_409() {
        signupService.signup(new SignupRequest(
                "grace@example.com", "Str0ng!Pass", "Grace", null));

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> signupService.signup(new SignupRequest(
                        "grace@example.com", "Diff3rent!Pass", "Grace2", null)))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_EMAIL_ALREADY_REGISTERED");
                });

        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("duplicate email check is case-insensitive")
    void duplicate_email_case_insensitive() {
        signupService.signup(new SignupRequest(
                "henry@example.com", "Str0ng!Pass", "Henry", null));

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> signupService.signup(new SignupRequest(
                        "HENRY@EXAMPLE.COM", "Str0ng!Pass", "Henry2", null)));
    }

    @Test
    @DisplayName("response body contains no password hash")
    void response_contains_no_password() {
        SignupResponse response = signupService.signup(new SignupRequest(
                "iris@example.com", "Str0ng!Pass", "Iris", null));

        String responseString = response.toString();
        assertThat(responseString).doesNotContain("Str0ng!Pass");
    }
}