package com.stash.kyc.submission.service;

import static org.assertj.core.api.Assertions.*;

import com.stash.kyc.submission.api.dto.SubmissionStatusResponse;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.kyc.support.KycIntegrationTestSupport;
import com.stash.shared.apierrors.StashApiException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KycSubmissionStatusService")
class KycSubmissionStatusServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:16"
    )
        .withDatabaseName("kyc_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        KycIntegrationTestSupport.registerPostgres(registry, postgres);
    }

    @Autowired
    KycSubmissionStatusService statusService;

    @Autowired
    KycSubmissionRepository submissionRepository;

    @MockBean
    RabbitTemplate rabbitTemplate;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        submissionRepository.deleteAll();
    }

    private KycSubmission save(UUID userId, String status) {
        KycSubmission s = new KycSubmission(
            userId,
            "GHA-123456789-0",
            "Test User",
            "corr-1"
        );
        s.setStatus(status);
        if (
            KycSubmission.STATUS_APPROVED.equals(status) ||
            KycSubmission.STATUS_REJECTED.equals(status)
        ) {
            s.setDecision(status);
            s.setDecidedAt(java.time.Instant.now());
        }
        return submissionRepository.save(s);
    }

    @Test
    @DisplayName("PENDING_DOCUMENTS status returned correctly")
    void pending_documents_status() {
        KycSubmission s = save(USER_A, KycSubmission.STATUS_PENDING_DOCUMENTS);

        SubmissionStatusResponse response = statusService.getStatus(
            s.getId(),
            USER_A
        );

        assertThat(response.status()).isEqualTo(
            KycSubmission.STATUS_PENDING_DOCUMENTS
        );
        assertThat(response.rejectionReason()).isNull();
    }

    @Test
    @DisplayName(
        "REVIEWING status returned correctly (UNDER_REVIEW per issue, REVIEWING per schema)"
    )
    void reviewing_status() {
        KycSubmission s = save(USER_A, KycSubmission.STATUS_REVIEWING);

        SubmissionStatusResponse response = statusService.getStatus(
            s.getId(),
            USER_A
        );

        assertThat(response.status()).isEqualTo(KycSubmission.STATUS_REVIEWING);
    }

    @Test
    @DisplayName("APPROVED status returned correctly, no rejection_reason")
    void approved_status() {
        KycSubmission s = save(USER_A, KycSubmission.STATUS_APPROVED);
        // Compare against DB round-trip — Postgres truncates Instant to microseconds.
        KycSubmission persisted = submissionRepository
            .findById(s.getId())
            .orElseThrow();

        SubmissionStatusResponse response = statusService.getStatus(
            s.getId(),
            USER_A
        );

        assertThat(response.status()).isEqualTo(KycSubmission.STATUS_APPROVED);
        assertThat(response.rejectionReason()).isNull();
        assertThat(response.updatedAt()).isEqualTo(persisted.getDecidedAt());
    }

    @Test
    @DisplayName("REJECTED status returns rejection_reason")
    void rejected_status_includes_reason() {
        KycSubmission s = save(USER_A, KycSubmission.STATUS_REJECTED);
        s.setDecisionReason("Card image unreadable");
        submissionRepository.save(s);

        SubmissionStatusResponse response = statusService.getStatus(
            s.getId(),
            USER_A
        );

        assertThat(response.status()).isEqualTo(KycSubmission.STATUS_REJECTED);
        assertThat(response.rejectionReason()).isEqualTo(
            "Card image unreadable"
        );
    }

    @Test
    @DisplayName("requesting another user's submission returns 404")
    void cross_user_access_returns_404() {
        KycSubmission s = save(USER_A, KycSubmission.STATUS_PENDING_DOCUMENTS);

        assertThatExceptionOfType(StashApiException.class)
            .isThrownBy(() -> statusService.getStatus(s.getId(), USER_B))
            .satisfies(ex ->
                assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    @Test
    @DisplayName("unknown submission ID returns 404")
    void unknown_submission_returns_404() {
        assertThatExceptionOfType(StashApiException.class)
            .isThrownBy(() ->
                statusService.getStatus(UUID.randomUUID(), USER_A)
            )
            .satisfies(ex ->
                assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    @Test
    @DisplayName("getMyStatus returns 404 when user has no submission")
    void me_no_submission_returns_404() {
        assertThatExceptionOfType(StashApiException.class)
            .isThrownBy(() -> statusService.getMyStatus(USER_A))
            .satisfies(ex ->
                assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    @Test
    @DisplayName(
        "getMyStatus returns the most recent submission when multiple exist"
    )
    void me_returns_most_recent() {
        save(USER_A, KycSubmission.STATUS_REJECTED);
        KycSubmission newest = save(
            USER_A,
            KycSubmission.STATUS_PENDING_DOCUMENTS
        );

        SubmissionStatusResponse response = statusService.getMyStatus(USER_A);

        assertThat(response.id()).isEqualTo(newest.getId());
    }

    @Test
    @DisplayName(
        "response contains no ghana_card_number or storage-related fields"
    )
    void no_sensitive_fields_in_response() {
        KycSubmission s = save(USER_A, KycSubmission.STATUS_PENDING_DOCUMENTS);

        SubmissionStatusResponse response = statusService.getStatus(
            s.getId(),
            USER_A
        );

        String responseStr = response.toString();
        assertThat(responseStr)
            .doesNotContain("GHA-123456789-0")
            .doesNotContain("ghana")
            .doesNotContain("storage")
            .doesNotContain("document");
    }
}
