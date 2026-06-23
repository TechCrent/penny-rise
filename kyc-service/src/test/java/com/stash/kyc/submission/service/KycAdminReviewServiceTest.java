package com.stash.kyc.submission.service;

import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.submission.api.dto.AdminQueuePageResponse;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.domain.ManualReviewQueueEntry;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.kyc.submission.repository.ManualReviewQueueRepository;
import com.stash.kyc.submission.repository.ProviderDecisionRecordRepository;
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
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KycAdminReviewService")
class KycAdminReviewServiceTest {

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

    @Autowired KycAdminReviewService adminReviewService;
    @Autowired KycSubmissionRepository submissionRepository;
    @Autowired KycSubmissionDocumentRepository documentRepository;
    @Autowired ManualReviewQueueRepository manualReviewQueueRepository;
    @Autowired ProviderDecisionRecordRepository decisionRecordRepository;
    @MockBean  RabbitTemplate rabbitTemplate;

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        manualReviewQueueRepository.deleteAll();
        decisionRecordRepository.deleteAll();
        documentRepository.deleteAll();
        submissionRepository.deleteAll();
    }

    private KycSubmission escalatedSubmission() {
        KycSubmission s = new KycSubmission(
                UUID.randomUUID(), "GHA-555555555-5", "Manual Review User", "corr-1");
        s.setStatus(KycSubmission.STATUS_REVIEWING);
        s = submissionRepository.save(s);

        manualReviewQueueRepository.save(
                new ManualReviewQueueEntry(s.getId(), "Auto-flagged for review"));

        var doc = new KycSubmissionDocument(s.getId(), "FRONT_OF_CARD", "LOCAL",
                "key1", "image/jpeg", 1000L, "hash1");
        documentRepository.save(doc);

        return s;
    }

    @Test
    @DisplayName("queue lists escalated submissions")
    void queue_lists_escalated() {
        KycSubmission submission = escalatedSubmission();

        AdminQueuePageResponse page = adminReviewService.getQueue(null, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).submissionId()).isEqualTo(submission.getId());
    }

    @Test
    @DisplayName("queue includes document view URLs")
    void queue_includes_view_urls() {
        escalatedSubmission();

        AdminQueuePageResponse page = adminReviewService.getQueue(null, 20);

        assertThat(page.items().get(0).documentViewUrls()).containsKey("FRONT_OF_CARD");
    }

    @Test
    @DisplayName("detail returns ghana_card_number decrypted for admin view")
    void detail_includes_ghana_card_number() {
        KycSubmission submission = escalatedSubmission();

        var detail = adminReviewService.getDetail(submission.getId());

        assertThat(detail.ghanaCardNumber()).isEqualTo("GHA-555555555-5");
    }

    @Test
    @DisplayName("approve transitions submission to APPROVED")
    void approve_happy_path() {
        KycSubmission submission = escalatedSubmission();

        adminReviewService.decide(submission.getId(), KycAdminReviewService.DECIDE_APPROVE, null, ADMIN_ID);

        KycSubmission reloaded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(KycSubmission.DECISION_APPROVED);
        assertThat(reloaded.getReviewerAdminId()).isEqualTo(ADMIN_ID);
        assertThat(reloaded.getReviewPath()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("approve publishes kyc.approved event")
    void approve_publishes_event() {
        KycSubmission submission = escalatedSubmission();

        adminReviewService.decide(submission.getId(), KycAdminReviewService.DECIDE_APPROVE, null, ADMIN_ID);

        verify(rabbitTemplate).convertAndSend(eq("kyc.events"), eq("kyc.approved"), any(Object.class));
    }

    @Test
    @DisplayName("approve schedules document deletion")
    void approve_schedules_deletion() {
        KycSubmission submission = escalatedSubmission();
        var doc = documentRepository.findBySubmissionId(submission.getId()).get(0);

        adminReviewService.decide(submission.getId(), KycAdminReviewService.DECIDE_APPROVE, null, ADMIN_ID);

        var reloaded = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(reloaded.getDeletionStatus()).isEqualTo("PENDING_DELETION");
    }

    @Test
    @DisplayName("decided submission disappears from queue")
    void decided_submission_leaves_queue() {
        KycSubmission submission = escalatedSubmission();

        adminReviewService.decide(submission.getId(), KycAdminReviewService.DECIDE_APPROVE, null, ADMIN_ID);

        AdminQueuePageResponse page = adminReviewService.getQueue(null, 20);
        assertThat(page.items()).isEmpty();
    }

    @Test
    @DisplayName("reject with reason transitions submission to REJECTED")
    void reject_happy_path() {
        KycSubmission submission = escalatedSubmission();

        adminReviewService.decide(
                submission.getId(), KycAdminReviewService.DECIDE_REJECT, "Card image unreadable", ADMIN_ID);

        KycSubmission reloaded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(KycSubmission.DECISION_REJECTED);
        assertThat(reloaded.getDecisionReason()).isEqualTo("Card image unreadable");
    }

    @Test
    @DisplayName("reject without reason throws 422 KYC_REJECTION_REASON_REQUIRED")
    void reject_without_reason_throws_422() {
        KycSubmission submission = escalatedSubmission();

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> adminReviewService.decide(
                        submission.getId(), KycAdminReviewService.DECIDE_REJECT, null, ADMIN_ID))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getErrorCode().name()).isEqualTo("KYC_REJECTION_REASON_REQUIRED");
                });
    }

    @Test
    @DisplayName("reject without reason does not modify the submission")
    void reject_without_reason_no_side_effects() {
        KycSubmission submission = escalatedSubmission();

        try {
            adminReviewService.decide(submission.getId(), KycAdminReviewService.DECIDE_REJECT, null, ADMIN_ID);
        } catch (StashApiException ignored) {}

        KycSubmission reloaded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(KycSubmission.STATUS_REVIEWING);
    }
}
