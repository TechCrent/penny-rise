package com.stash.kyc.submission.service;

import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.support.KycIntegrationTestSupport;
import com.stash.kyc.provider.GhanaCardProviderClient;
import com.stash.kyc.provider.StubGhanaCardProviderClient;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.event.KycRejectedEvent;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.kyc.submission.repository.ManualReviewQueueRepository;
import com.stash.kyc.submission.repository.ProviderDecisionRecordRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
@DisplayName("AutomatedDecisionService")
class AutomatedDecisionServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("kyc_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        KycIntegrationTestSupport.registerPostgres(registry, postgres);
        registry.add("stash.kyc.stub-provider.test-approve-numbers",
                () -> "GHA-111111111-1");
    }

    @Autowired AutomatedDecisionService decisionService;
    @Autowired KycSubmissionRepository submissionRepository;
    @Autowired KycSubmissionDocumentRepository documentRepository;
    @Autowired ProviderDecisionRecordRepository decisionRecordRepository;
    @Autowired ManualReviewQueueRepository manualReviewQueueRepository;
    @MockBean  RabbitTemplate rabbitTemplate;
    @BeforeEach
    void setUp() {
        manualReviewQueueRepository.deleteAll();
        decisionRecordRepository.deleteAll();
        documentRepository.deleteAll();
        submissionRepository.deleteAll();
    }

    private KycSubmission submissionInReviewing(String ghanaCardNumber) {
        return submissionInReviewing(UUID.randomUUID(), ghanaCardNumber);
    }

    private KycSubmission submissionInReviewing(UUID userId, String ghanaCardNumber) {
        KycSubmission s = new KycSubmission(userId, ghanaCardNumber, "Test User", "corr-1");
        s.setStatus(KycSubmission.STATUS_REVIEWING);
        return submissionRepository.save(s);
    }

// ── Happy approval ────────────────────────────────────────────────────
    @Test
    @DisplayName("happy approval: test card number transitions submission to APPROVED")
    void happy_approval() {
        KycSubmission submission = submissionInReviewing("GHA-111111111-1");

// Documents in RETAINED state to verify scheduling
        var doc = new KycSubmissionDocument(submission.getId(), "FRONT_OF_CARD", "LOCAL",
                "key1", "image/jpeg", 1000L, "hash1");
        documentRepository.save(doc);
        decisionService.processSubmission(submission.getId(), "corr-1");
        KycSubmission reloaded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(KycSubmission.DECISION_APPROVED);
        assertThat(reloaded.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("approval records a provider_decisions row")
    void approval_records_decision() {
        KycSubmission submission = submissionInReviewing("GHA-111111111-1");
        decisionService.processSubmission(submission.getId(), "corr-1");
        assertThat(decisionRecordRepository.findAll()).hasSize(1);
        assertThat(decisionRecordRepository.findAll().get(0).getDecision()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("approval does not schedule document deletion inline — event consumer handles it")
    void approval_does_not_schedule_deletion_inline() {
        KycSubmission submission = submissionInReviewing("GHA-111111111-1");
        var doc = new KycSubmissionDocument(submission.getId(), "FRONT_OF_CARD", "LOCAL",
                "key1", "image/jpeg", 1000L, "hash1");
        documentRepository.save(doc);
        decisionService.processSubmission(submission.getId(), "corr-1");
        var reloadedDoc = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(reloadedDoc.getDeletionStatus()).isEqualTo("RETAINED");
        assertThat(reloadedDoc.getDeletionScheduledAt()).isNull();
    }

    @Test
    @DisplayName("approval publishes kyc.approved event")
    void approval_publishes_event() {
        KycSubmission submission = submissionInReviewing("GHA-111111111-1");
        decisionService.processSubmission(submission.getId(), "corr-1");
        verify(rabbitTemplate).convertAndSend(eq("kyc.events"), eq("kyc.approved"), any(Object.class));
    }

// ── Rejection escalation ─────────────────────────────────────────────

    @Test
    @DisplayName("non-test card number transitions submission to REJECTED")
    void non_test_number_rejected() {
        KycSubmission submission = submissionInReviewing("GHA-999999999-9");
        decisionService.processSubmission(submission.getId(), "corr-1");
        KycSubmission reloaded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(KycSubmission.DECISION_REJECTED);
    }

    @Test
    @DisplayName("rejection creates a manual_review_queue entry")
    void rejection_creates_queue_entry() {
        KycSubmission submission = submissionInReviewing("GHA-999999999-9");
        decisionService.processSubmission(submission.getId(), "corr-1");
        assertThat(manualReviewQueueRepository.findAll()).hasSize(1);
        assertThat(manualReviewQueueRepository.findAll().get(0).getSubmissionId())
                .isEqualTo(submission.getId());
    }

    @Test
    @DisplayName("rejection publishes kyc.rejected event")
    void rejection_publishes_event() {
        KycSubmission submission = submissionInReviewing("GHA-999999999-9");
        decisionService.processSubmission(submission.getId(), "corr-1");
        verify(rabbitTemplate).convertAndSend(eq("kyc.events"), eq("kyc.rejected"), any(Object.class));
    }

    @Test
    @DisplayName("v0.5-035: a user's first rejection publishes rejectionCount=1")
    void first_rejection_has_count_one() {
        KycSubmission submission = submissionInReviewing("GHA-999999999-9");
        decisionService.processSubmission(submission.getId(), "corr-1");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("kyc.events"), eq("kyc.rejected"), captor.capture());
        assertThat(((KycRejectedEvent) captor.getValue()).payload().rejectionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("v0.5-035: a user's second rejection (across two separate submissions) publishes rejectionCount=2")
    void second_rejection_for_same_user_has_count_two() {
        UUID userId = UUID.randomUUID();
        KycSubmission first = submissionInReviewing(userId, "GHA-999999999-9");
        decisionService.processSubmission(first.getId(), "corr-1");

        KycSubmission second = submissionInReviewing(userId, "GHA-999999999-9");
        decisionService.processSubmission(second.getId(), "corr-2");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(2))
                .convertAndSend(eq("kyc.events"), eq("kyc.rejected"), captor.capture());
        int secondCallCount = ((KycRejectedEvent) captor.getAllValues().get(1)).payload().rejectionCount();
        assertThat(secondCallCount).isEqualTo(2);
    }

// ── Idempotent re-processing ─────────────────────────────────────────

    @Test
    @DisplayName("re-processing an already-decided submission is a no-op")
    void idempotent_reprocessing() {
        KycSubmission submission = submissionInReviewing("GHA-111111111-1");
        decisionService.processSubmission(submission.getId(), "corr-1");
        decisionService.processSubmission(submission.getId(), "corr-1"); // redelivery
// Only one decision record, one event published
        assertThat(decisionRecordRepository.findAll()).hasSize(1);
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq("kyc.events"), eq("kyc.approved"), any(Object.class));
    }

// ── Provider transient failure with retry ────────────────────────────

    @Test
    @DisplayName("transient provider failure routes to manual review after retries exhausted")
    void transient_failure_routes_to_manual() {
        GhanaCardProviderClient failingProvider = mock(GhanaCardProviderClient.class);
        when(failingProvider.verify(any(), any(), any()))
                .thenThrow(new RuntimeException("Simulated transient provider failure"));
        AutomatedDecisionService serviceWithFailingProvider = new AutomatedDecisionService(
                submissionRepository, decisionRecordRepository,
                manualReviewQueueRepository, failingProvider, rabbitTemplate);
        KycSubmission submission = submissionInReviewing("GHA-111111111-1");
        serviceWithFailingProvider.processSubmission(submission.getId(), "corr-1");

// Routed to manual review, not crashed
        assertThat(manualReviewQueueRepository.findAll()).hasSize(1);
        verify(failingProvider, atLeast(1)).verify(any(), any(), any());
    }
}