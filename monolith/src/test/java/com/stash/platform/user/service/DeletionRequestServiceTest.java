package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.domain.DeletionRequest;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.DeletionRequestRepository;
import com.stash.platform.user.repository.EmailVerificationTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.StashApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DeletionRequestService")
class DeletionRequestServiceTest {

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

    @Autowired DeletionRequestService deletionRequestService;
    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired DeletionRequestRepository deletionRequestRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;
    @MockBean  EmailSender emailSender;

    private User user;

    @BeforeEach
    void setUp() {
        reset(emailSender);
        deletionRequestRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        signupService.signup(new SignupRequest(
                "delete-test@example.com", "Str0ng!Pass", "Delete Test", null, true));
        user = userRepository.findByEmail("delete-test@example.com").orElseThrow();
    }

    @Test
    @DisplayName("happy submission: creates a PENDING request")
    void happy_submission() {
        DeletionRequest request = deletionRequestService.submit(user.getId());

        assertThat(request.getId()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(DeletionRequest.STATUS_PENDING);
        assertThat(request.getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("scheduled_completion_at is 30 days from submission")
    void scheduled_completion_30_days() {
        DeletionRequest request = deletionRequestService.submit(user.getId());

        Duration gap = Duration.between(request.getSubmittedAt(), request.getScheduledCompletionAt());
        assertThat(gap.toDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("blockers_at_submission is an empty array at v0.2 (no blocker sources wired yet)")
    void blockers_snapshot_empty_at_v02() {
        DeletionRequest request = deletionRequestService.submit(user.getId());

        assertThat(request.getBlockersAtSubmission().isArray()).isTrue();
        assertThat(request.getBlockersAtSubmission()).isEmpty();
    }

    @Test
    @DisplayName("request is persisted and retrievable")
    void request_persisted() {
        DeletionRequest request = deletionRequestService.submit(user.getId());

        assertThat(deletionRequestRepository.findById(request.getId())).isPresent();
    }

    @Test
    @DisplayName("submitting a second request while one is PENDING returns 409")
    void duplicate_request_returns_409() {
        deletionRequestService.submit(user.getId());

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> deletionRequestService.submit(user.getId()))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("USER_DELETION_REQUEST_ALREADY_PENDING");
                });
    }

    @Test
    @DisplayName("only one row exists after the duplicate attempt")
    void only_one_row_after_duplicate_attempt() {
        deletionRequestService.submit(user.getId());

        try {
            deletionRequestService.submit(user.getId());
        } catch (StashApiException ignored) {}

        assertThat(deletionRequestRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("happy cancel: sets status=CANCELLED and cancelled_at")
    void happy_cancel() {
        deletionRequestService.submit(user.getId());
        deletionRequestService.cancel(user.getId());

        DeletionRequest cancelled = deletionRequestRepository.findAll().get(0);
        assertThat(cancelled.getStatus()).isEqualTo(DeletionRequest.STATUS_CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("after cancellation, user can submit a new deletion request")
    void can_resubmit_after_cancel() {
        deletionRequestService.submit(user.getId());
        deletionRequestService.cancel(user.getId());

        assertThatNoException()
                .isThrownBy(() -> deletionRequestService.submit(user.getId()));
    }

    @Test
    @DisplayName("cancelling with no existing request returns 404")
    void cancel_nonexistent_returns_404() {
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> deletionRequestService.cancel(user.getId()))
                .satisfies(ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("cancelling an already-cancelled request returns 404")
    void cancel_already_cancelled_returns_404() {
        deletionRequestService.submit(user.getId());
        deletionRequestService.cancel(user.getId());

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> deletionRequestService.cancel(user.getId()))
                .satisfies(ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("user account remains ACTIVE and usable after submitting a deletion request")
    void user_remains_active_during_cooloff() {
        deletionRequestService.submit(user.getId());

        User reloaded = userRepository.findByEmail("delete-test@example.com").orElseThrow();
        assertThat(reloaded.getAccountStatus().name()).isEqualTo("ACTIVE");
        assertThat(reloaded.isDeleted()).isFalse();
    }
}
