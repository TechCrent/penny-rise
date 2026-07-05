package com.stash.kyc.document.service;

import static org.assertj.core.api.Assertions.*;

import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.event.KycApprovedEvent;
import com.stash.kyc.submission.event.KycRejectedEvent;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.kyc.support.KycIntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("DocumentDeletionSchedulerService")
class DocumentDeletionSchedulerServiceTest {

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
    DocumentDeletionSchedulerService schedulerService;

    @Autowired
    KycSubmissionRepository submissionRepository;

    @Autowired
    KycSubmissionDocumentRepository documentRepository;

    @MockBean
    RabbitTemplate rabbitTemplate;

    private KycSubmission submission;
    private KycSubmissionDocument doc1;
    private KycSubmissionDocument doc2;
    private KycSubmissionDocument doc3;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        submissionRepository.deleteAll();

        submission = new KycSubmission(
            UUID.randomUUID(),
            "GHA-123456789-0",
            "Scheduler Test",
            "corr-1"
        );
        submission.setStatus(KycSubmission.STATUS_APPROVED);
        submissionRepository.save(submission);

        doc1 = documentRepository.save(
            new KycSubmissionDocument(
                submission.getId(),
                "FRONT_OF_CARD",
                "LOCAL",
                "keys/front.jpg",
                "image/jpeg",
                1000L,
                "h1"
            )
        );
        doc2 = documentRepository.save(
            new KycSubmissionDocument(
                submission.getId(),
                "BACK_OF_CARD",
                "LOCAL",
                "keys/back.jpg",
                "image/jpeg",
                1000L,
                "h2"
            )
        );
        doc3 = documentRepository.save(
            new KycSubmissionDocument(
                submission.getId(),
                "SELFIE",
                "LOCAL",
                "keys/selfie.jpg",
                "image/jpeg",
                1000L,
                "h3"
            )
        );
    }

    @Test
    @DisplayName(
        "kyc.approved schedules all documents for deletion at decided_at + 24h"
    )
    void approval_schedules_24h_deletion() {
        Instant approvedAt = Instant.now();
        KycApprovedEvent event = new KycApprovedEvent(
            UUID.randomUUID().toString(),
            "KycApproved",
            "1.0",
            "kyc-service",
            approvedAt,
            "corr-1",
            new KycApprovedEvent.Payload(
                submission.getId(),
                submission.getUserId(),
                "GHA-123456789-0"
            )
        );

        schedulerService.onKycApproved(event);

        List<KycSubmissionDocument> docs =
            documentRepository.findBySubmissionId(submission.getId());
        Instant expectedScheduledAt = approvedAt.plus(24, ChronoUnit.HOURS);

        assertThat(docs).hasSize(3);
        for (KycSubmissionDocument doc : docs) {
            assertThat(doc.getDeletionStatus()).isEqualTo("PENDING_DELETION");
            assertThat(doc.getDeletionScheduledAt())
                .isAfterOrEqualTo(expectedScheduledAt.minusSeconds(2))
                .isBeforeOrEqualTo(expectedScheduledAt.plusSeconds(2));
        }
    }

    @Test
    @DisplayName(
        "all three document storage_keys are accessible from the scheduled documents"
    )
    void document_references_accessible_after_scheduling() {
        Instant approvedAt = Instant.now();
        schedulerService.scheduleDeletion(
            submission.getId(),
            approvedAt.plus(24, ChronoUnit.HOURS),
            "kyc.approved"
        );

        List<KycSubmissionDocument> docs =
            documentRepository.findBySubmissionId(submission.getId());
        List<String> keys = docs
            .stream()
            .map(KycSubmissionDocument::getStorageKey)
            .toList();

        assertThat(keys).containsExactlyInAnyOrder(
            "keys/front.jpg",
            "keys/back.jpg",
            "keys/selfie.jpg"
        );
    }

    @Test
    @DisplayName(
        "kyc.rejected schedules all documents for deletion at decided_at + 72h"
    )
    void rejection_schedules_72h_deletion() {
        Instant rejectedAt = Instant.now();
        KycRejectedEvent event = new KycRejectedEvent(
            UUID.randomUUID().toString(),
            "KycRejected",
            "1.0",
            "kyc-service",
            rejectedAt,
            "corr-1",
            new KycRejectedEvent.Payload(
                submission.getId(),
                submission.getUserId(),
                "Card unreadable",
                1
            )
        );

        schedulerService.onKycRejected(event);

        List<KycSubmissionDocument> docs =
            documentRepository.findBySubmissionId(submission.getId());
        Instant expectedScheduledAt = rejectedAt.plus(72, ChronoUnit.HOURS);

        for (KycSubmissionDocument doc : docs) {
            assertThat(doc.getDeletionStatus()).isEqualTo("PENDING_DELETION");
            assertThat(doc.getDeletionScheduledAt())
                .isAfterOrEqualTo(expectedScheduledAt.minusSeconds(2))
                .isBeforeOrEqualTo(expectedScheduledAt.plusSeconds(2));
        }
    }

    @Test
    @DisplayName(
        "rejection grace window is 72h, not 24h — confirmed distinct from approval"
    )
    void rejection_grace_window_longer_than_approval() {
        Instant decisionAt = Instant.now();

        KycSubmission approved = submissionRepository.save(
            new KycSubmission(
                UUID.randomUUID(),
                "GHA-100000000-1",
                "Approved",
                "c1"
            )
        );
        documentRepository.save(
            new KycSubmissionDocument(
                approved.getId(),
                "FRONT_OF_CARD",
                "LOCAL",
                "a/front.jpg",
                "image/jpeg",
                1000L,
                "hA"
            )
        );

        KycSubmission rejected = submissionRepository.save(
            new KycSubmission(
                UUID.randomUUID(),
                "GHA-200000000-2",
                "Rejected",
                "c2"
            )
        );
        documentRepository.save(
            new KycSubmissionDocument(
                rejected.getId(),
                "FRONT_OF_CARD",
                "LOCAL",
                "r/front.jpg",
                "image/jpeg",
                1000L,
                "hR"
            )
        );

        schedulerService.scheduleDeletion(
            approved.getId(),
            decisionAt.plus(24, ChronoUnit.HOURS),
            "kyc.approved"
        );
        schedulerService.scheduleDeletion(
            rejected.getId(),
            decisionAt.plus(72, ChronoUnit.HOURS),
            "kyc.rejected"
        );

        KycSubmissionDocument approvedDoc = documentRepository
            .findBySubmissionId(approved.getId())
            .get(0);
        KycSubmissionDocument rejectedDoc = documentRepository
            .findBySubmissionId(rejected.getId())
            .get(0);

        assertThat(rejectedDoc.getDeletionScheduledAt()).isAfter(
            approvedDoc.getDeletionScheduledAt()
        );

        long hoursDiff = ChronoUnit.HOURS.between(
            approvedDoc.getDeletionScheduledAt(),
            rejectedDoc.getDeletionScheduledAt()
        );
        assertThat(hoursDiff).isEqualTo(48L);
    }

    @Test
    @DisplayName(
        "duplicate event for same submission updates zero rows — idempotent"
    )
    void duplicate_event_is_idempotent() {
        Instant approvedAt = Instant.now();
        KycApprovedEvent event = new KycApprovedEvent(
            UUID.randomUUID().toString(),
            "KycApproved",
            "1.0",
            "kyc-service",
            approvedAt,
            "corr-1",
            new KycApprovedEvent.Payload(
                submission.getId(),
                submission.getUserId(),
                "GHA-123456789-0"
            )
        );

        schedulerService.onKycApproved(event);
        int rowsOnSecondDelivery = schedulerService.scheduleDeletion(
            submission.getId(),
            approvedAt.plus(24, ChronoUnit.HOURS),
            "kyc.approved"
        );

        assertThat(rowsOnSecondDelivery).isEqualTo(0);
    }

    @Test
    @DisplayName("deletion_scheduled_at is not changed on duplicate delivery")
    void scheduled_at_not_overwritten_on_redelivery() {
        Instant firstDeliveryAt = Instant.now();
        schedulerService.scheduleDeletion(
            submission.getId(),
            firstDeliveryAt.plus(24, ChronoUnit.HOURS),
            "kyc.approved"
        );

        Instant firstScheduledAt = documentRepository
            .findBySubmissionId(submission.getId())
            .get(0)
            .getDeletionScheduledAt();

        Instant laterAt = firstDeliveryAt.plusSeconds(300);
        schedulerService.scheduleDeletion(
            submission.getId(),
            laterAt.plus(24, ChronoUnit.HOURS),
            "kyc.approved"
        );

        Instant afterRedelivery = documentRepository
            .findBySubmissionId(submission.getId())
            .get(0)
            .getDeletionScheduledAt();

        assertThat(afterRedelivery).isEqualTo(firstScheduledAt);
    }
}
