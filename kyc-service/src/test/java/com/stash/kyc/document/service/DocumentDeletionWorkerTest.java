package com.stash.kyc.document.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.stash.kyc.document.domain.DocumentDeletionJob;
import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.repository.DocumentDeletionJobRepository;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.storage.ObjectStorage;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.kyc.support.KycIntegrationTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DocumentDeletionWorker")
class DocumentDeletionWorkerTest {

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
    DocumentDeletionWorker worker;

    @Autowired
    DocumentEscalationService escalationService;

    @Autowired
    KycSubmissionRepository submissionRepository;

    @Autowired
    KycSubmissionDocumentRepository documentRepository;

    @Autowired
    DocumentDeletionJobRepository deletionJobRepository;

    @Autowired
    MeterRegistry meterRegistry;

    @MockBean
    ObjectStorage objectStorage;

    @MockBean
    RabbitTemplate rabbitTemplate;

    private KycSubmissionDocument document;

    @BeforeEach
    void setUp() {
        deletionJobRepository.deleteAll();
        documentRepository.deleteAll();
        submissionRepository.deleteAll();

        KycSubmission submission = submissionRepository.save(
            new KycSubmission(
                UUID.randomUUID(),
                "GHA-123456789-0",
                "Test",
                "corr-1"
            )
        );

        document = new KycSubmissionDocument(
            submission.getId(),
            "FRONT_OF_CARD",
            "LOCAL",
            "submissions/abc/front.jpg",
            "image/jpeg",
            1024L,
            "hash1"
        );
        document.setDeletionStatus("PENDING_DELETION");
        document.setDeletionScheduledAt(Instant.now().minusSeconds(60));
        document = documentRepository.save(document);
    }

    @Test
    @DisplayName(
        "happy deletion: storage_key nulled, status=DELETED, audit row inserted"
    )
    void happy_deletion() {
        doNothing().when(objectStorage).delete(eq("submissions/abc/front.jpg"));
        // Baseline, not an absolute assertion — the MeterRegistry is a shared
        // Spring singleton across every test method in this class, so its
        // count reflects every test that ran before this one, not just this test.
        double before = meterRegistry.get("kyc.document.deletion.failures").counter().count();

        boolean result = worker.attemptDeletion(document);

        assertThat(result).isTrue();

        KycSubmissionDocument reloaded = documentRepository
            .findById(document.getId())
            .orElseThrow();
        assertThat(reloaded.getDeletionStatus()).isEqualTo("DELETED");
        assertThat(reloaded.getDeletionCompletedAt()).isNotNull();
        assertThat(reloaded.getStorageKey()).isNull();

        List<DocumentDeletionJob> jobs = deletionJobRepository.findAll();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getOutcome()).isEqualTo("SUCCESS");

        assertThat(meterRegistry.get("kyc.document.deletion.failures").counter().count()).isEqualTo(before);
    }

    @Test
    @DisplayName(
        "storage failure: status=DELETE_FAILED, failure_count incremented, audit row inserted"
    )
    void storage_failure_increments_count() {
        doThrow(new RuntimeException("Supabase timeout"))
            .when(objectStorage)
            .delete(any());
        double before = meterRegistry.get("kyc.document.deletion.failures").counter().count();

        boolean result = worker.attemptDeletion(document);

        assertThat(result).isFalse();

        KycSubmissionDocument reloaded = documentRepository
            .findById(document.getId())
            .orElseThrow();
        assertThat(reloaded.getDeletionStatus()).isEqualTo("DELETE_FAILED");
        assertThat(reloaded.getDeletionFailureCount()).isEqualTo(1);
        assertThat(reloaded.getStorageKey()).isEqualTo(
            "submissions/abc/front.jpg"
        );

        List<DocumentDeletionJob> jobs = deletionJobRepository.findAll();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getOutcome()).isEqualTo("FAILURE");
        assertThat(jobs.get(0).getErrorMessage()).contains("Supabase timeout");

        assertThat(meterRegistry.get("kyc.document.deletion.failures").counter().count()).isEqualTo(before + 1.0);
    }

    @Test
    @DisplayName(
        "partial failure: one document fails, others in same batch still attempted"
    )
    void partial_failure_does_not_stop_others() {
        KycSubmission sub = submissionRepository.findAll().get(0);

        KycSubmissionDocument doc2 = documentRepository.save(
            new KycSubmissionDocument(
                sub.getId(),
                "BACK_OF_CARD",
                "LOCAL",
                "submissions/abc/back.jpg",
                "image/jpeg",
                512L,
                "h2"
            )
        );
        doc2.setDeletionStatus("PENDING_DELETION");
        doc2.setDeletionScheduledAt(Instant.now().minusSeconds(60));
        doc2 = documentRepository.save(doc2);

        doThrow(new RuntimeException("timeout"))
            .when(objectStorage)
            .delete("submissions/abc/front.jpg");
        doNothing().when(objectStorage).delete("submissions/abc/back.jpg");

        worker.attemptDeletion(document);
        worker.attemptDeletion(doc2);

        KycSubmissionDocument reloaded1 = documentRepository
            .findById(document.getId())
            .orElseThrow();
        KycSubmissionDocument reloaded2 = documentRepository
            .findById(doc2.getId())
            .orElseThrow();

        assertThat(reloaded1.getDeletionStatus()).isEqualTo("DELETE_FAILED");
        assertThat(reloaded2.getDeletionStatus()).isEqualTo("DELETED");
        assertThat(reloaded2.getStorageKey()).isNull();
    }

    @Test
    @DisplayName(
        "max attempts reached: document marked DELETE_FAILED, escalation triggered"
    )
    void max_attempts_triggers_escalation() {
        doThrow(new RuntimeException("persistent failure"))
            .when(objectStorage)
            .delete(any());

        document.setDeletionFailureCount(4);
        document = documentRepository.save(document);

        worker.attemptDeletion(document);

        KycSubmissionDocument reloaded = documentRepository
            .findById(document.getId())
            .orElseThrow();
        assertThat(reloaded.getDeletionFailureCount()).isEqualTo(5);
        assertThat(escalationService.requiresEscalation(reloaded)).isTrue();
    }

    @Test
    @DisplayName(
        "already-DELETED document: scheduler does not select it (no PENDING_DELETION)"
    )
    void already_deleted_not_selected() {
        document.setDeletionStatus("DELETED");
        document.setStorageKey(null);
        document = documentRepository.save(document);

        List<KycSubmissionDocument> due = documentRepository.findDueForDeletion(
            100
        );

        assertThat(due).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName(
        "scheduler: SELECT FOR UPDATE SKIP LOCKED query selects due documents"
    )
    void skip_locked_selects_due_rows() {
        List<KycSubmissionDocument> due = documentRepository.findDueForDeletion(
            100
        );
        assertThat(due).hasSize(1);
        assertThat(due.get(0).getId()).isEqualTo(document.getId());
    }
}
