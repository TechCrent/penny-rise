package com.stash.kyc.document.service;

import com.stash.kyc.document.api.dto.DocumentUploadConfirmationRequest;
import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.document.repository.ProcessedDocumentEventRepository;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.shared.apierrors.StashApiException;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DocumentUploadConfirmationService")
class DocumentUploadConfirmationServiceTest {

    private static final byte[] TEST_AES_KEY =
            "test-aes-256-key-32-bytes-long!!".getBytes();

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
                () -> java.util.Base64.getEncoder().encodeToString(TEST_AES_KEY));
    }

    @Autowired DocumentUploadConfirmationService confirmationService;
    @Autowired KycSubmissionRepository submissionRepository;
    @Autowired KycSubmissionDocumentRepository documentRepository;
    @Autowired ProcessedDocumentEventRepository processedEventRepository;
    @MockBean  RabbitTemplate rabbitTemplate;

    private KycSubmission submission;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
        documentRepository.deleteAll();
        submissionRepository.deleteAll();

        submission = new KycSubmission(
                UUID.randomUUID(), "GHA-123456789-0", "Test User", "test-correlation");
        submissionRepository.save(submission);
    }

    private DocumentUploadConfirmationRequest req(String eventId, String docType) {
        return new DocumentUploadConfirmationRequest(
                eventId, docType, "submissions/" + submission.getId() + "/" + docType.toLowerCase() + ".jpg",
                "image/jpeg", 102400L, "abc123sha256hash");
    }

    @Test
    @DisplayName("single document upload: creates row, submission stays PENDING_DOCUMENTS")
    void single_document_leaves_pending() {
        var response = confirmationService.confirmUpload(submission.getId(),
                req("event-1", KycSubmissionDocument.TYPE_FRONT_OF_CARD));

        assertThat(response.submissionStatus()).isEqualTo(KycSubmission.STATUS_PENDING_DOCUMENTS);
        assertThat(documentRepository.findBySubmissionId(submission.getId())).hasSize(1);
    }

    @Test
    @DisplayName("final document upload transitions submission to REVIEWING")
    void final_document_transitions_to_reviewing() {
        confirmationService.confirmUpload(submission.getId(),
                req("event-1", KycSubmissionDocument.TYPE_FRONT_OF_CARD));
        confirmationService.confirmUpload(submission.getId(),
                req("event-2", KycSubmissionDocument.TYPE_BACK_OF_CARD));
        var response = confirmationService.confirmUpload(submission.getId(),
                req("event-3", KycSubmissionDocument.TYPE_SELFIE));

        assertThat(response.submissionStatus()).isEqualTo(KycSubmission.STATUS_REVIEWING);

        KycSubmission reloaded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(KycSubmission.STATUS_REVIEWING);
    }

    @Test
    @DisplayName("ready-for-review event published exactly once on transition")
    void event_published_on_transition() {
        confirmationService.confirmUpload(submission.getId(),
                req("event-1", KycSubmissionDocument.TYPE_FRONT_OF_CARD));
        confirmationService.confirmUpload(submission.getId(),
                req("event-2", KycSubmissionDocument.TYPE_BACK_OF_CARD));
        confirmationService.confirmUpload(submission.getId(),
                req("event-3", KycSubmissionDocument.TYPE_SELFIE));

        verify(rabbitTemplate, times(1))
                .convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("duplicate webhook delivery (same provider_event_id) is idempotent")
    void duplicate_delivery_idempotent() {
        var request = req("duplicate-event", KycSubmissionDocument.TYPE_FRONT_OF_CARD);

        confirmationService.confirmUpload(submission.getId(), request);
        confirmationService.confirmUpload(submission.getId(), request);

        assertThat(documentRepository.findBySubmissionId(submission.getId())).hasSize(1);
    }

    @Test
    @DisplayName("final document delivered twice does not double-transition or double-publish")
    void final_document_duplicate_does_not_double_fire() {
        confirmationService.confirmUpload(submission.getId(),
                req("event-1", KycSubmissionDocument.TYPE_FRONT_OF_CARD));
        confirmationService.confirmUpload(submission.getId(),
                req("event-2", KycSubmissionDocument.TYPE_BACK_OF_CARD));

        var finalRequest = req("event-3-selfie", KycSubmissionDocument.TYPE_SELFIE);
        confirmationService.confirmUpload(submission.getId(), finalRequest);
        confirmationService.confirmUpload(submission.getId(), finalRequest);

        verify(rabbitTemplate, times(1))
                .convertAndSend(anyString(), anyString(), any(Object.class));

        KycSubmission reloaded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(KycSubmission.STATUS_REVIEWING);
    }

    @Test
    @DisplayName("two of three documents leaves submission in PENDING_DOCUMENTS")
    void partial_uploads_leave_pending() {
        confirmationService.confirmUpload(submission.getId(),
                req("event-1", KycSubmissionDocument.TYPE_FRONT_OF_CARD));
        var response = confirmationService.confirmUpload(submission.getId(),
                req("event-2", KycSubmissionDocument.TYPE_BACK_OF_CARD));

        assertThat(response.submissionStatus()).isEqualTo(KycSubmission.STATUS_PENDING_DOCUMENTS);
    }

    @Test
    @DisplayName("unknown submission ID throws 404")
    void unknown_submission_throws_404() {
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> confirmationService.confirmUpload(
                        UUID.randomUUID(), req("event-x", KycSubmissionDocument.TYPE_SELFIE)))
                .satisfies(ex -> assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
