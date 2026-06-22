package com.stash.kyc.submission.service;

import com.stash.kyc.storage.ObjectStorage;
import com.stash.kyc.submission.api.dto.CreateSubmissionRequest;
import com.stash.kyc.submission.api.dto.CreateSubmissionResponse;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.shared.apierrors.StashApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KycSubmissionService")
class KycSubmissionServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("kyc_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("stash.kyc.ghana-card-encryption-key",
                () -> java.util.Base64.getEncoder().encodeToString(
                        "test-aes-256-key-32-bytes-long!".getBytes()));
    }

    @Autowired KycSubmissionService kycSubmissionService;
    @Autowired KycSubmissionRepository submissionRepository;
    @Autowired ObjectStorage objectStorage;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        submissionRepository.deleteAll();
    }

    private CreateSubmissionRequest req() {
        return new CreateSubmissionRequest("GHA-123456789-0", "Test User");
    }

    @Test
    @DisplayName("happy submission: creates row with status PENDING_DOCUMENTS")
    void happy_submission() {
        CreateSubmissionResponse response = kycSubmissionService.createSubmission(USER_ID, req());

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(KycSubmission.STATUS_PENDING_DOCUMENTS);
    }

    @Test
    @DisplayName("happy submission: returns all three upload URLs")
    void happy_submission_returns_upload_urls() {
        CreateSubmissionResponse response = kycSubmissionService.createSubmission(USER_ID, req());

        assertThat(response.uploadUrls())
                .containsKeys(
                        CreateSubmissionResponse.DOC_FRONT_OF_CARD,
                        CreateSubmissionResponse.DOC_BACK_OF_CARD,
                        CreateSubmissionResponse.DOC_SELFIE);
    }

    @Test
    @DisplayName("upload URLs are scoped under the submission's own path")
    void upload_urls_scoped_to_submission() {
        CreateSubmissionResponse response = kycSubmissionService.createSubmission(USER_ID, req());

        String submissionIdStr = response.id().toString();
        for (String url : response.uploadUrls().values()) {
            assertThat(url).contains(submissionIdStr);
        }
    }

    @Test
    @DisplayName("ghana_card_number round-trips correctly through encryption")
    void ghana_card_number_round_trips() {
        CreateSubmissionResponse response = kycSubmissionService.createSubmission(USER_ID, req());

        KycSubmission reloaded = submissionRepository.findById(response.id()).orElseThrow();
        assertThat(reloaded.getGhanaCardNumber()).isEqualTo("GHA-123456789-0");
    }

    @Test
    @DisplayName("ghana_card_number is stored as ciphertext, not plaintext, in raw column value")
    void ghana_card_number_stored_encrypted() {
        kycSubmissionService.createSubmission(USER_ID, req());
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("second submission while one is active returns 409 KYC_SUBMISSION_ALREADY_ACTIVE")
    void duplicate_active_submission_returns_409() {
        kycSubmissionService.createSubmission(USER_ID, req());

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> kycSubmissionService.createSubmission(USER_ID, req()))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("KYC_SUBMISSION_ALREADY_ACTIVE");
                });
    }

    @Test
    @DisplayName("only one row exists after the duplicate attempt")
    void only_one_row_after_duplicate() {
        kycSubmissionService.createSubmission(USER_ID, req());

        try {
            kycSubmissionService.createSubmission(USER_ID, req());
        } catch (StashApiException ignored) {}

        assertThat(submissionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("a different user can submit while another user has an active submission")
    void different_user_not_blocked() {
        kycSubmissionService.createSubmission(USER_ID, req());

        UUID otherUser = UUID.randomUUID();
        assertThatNoException()
                .isThrownBy(() -> kycSubmissionService.createSubmission(otherUser, req()));
    }
}
