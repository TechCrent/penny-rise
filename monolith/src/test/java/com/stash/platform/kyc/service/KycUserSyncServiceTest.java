package com.stash.platform.kyc.service;

import com.stash.platform.kyc.event.KycApprovedEvent;
import com.stash.platform.kyc.event.KycRejectedEvent;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.notification.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KycUserSyncService")
class KycUserSyncServiceTest {

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

    @Autowired KycUserSyncService kycUserSyncService;
    @Autowired UserRepository userRepository;
    @MockBean EmailSender emailSender;

    private User user;
    private UUID submissionId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        user = new User("kyc-sync@example.com", "hash", "KYC Sync Test");
        user = userRepository.save(user);
        submissionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("approve sets kyc_status APPROVED and ghana_card_number")
    void approve_updates_user() {
        kycUserSyncService.applyApproved(approvedEvent("GHA-123456789-0"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(updated.getGhanaCardNumber()).isEqualTo("GHA-123456789-0");
    }

    @Test
    @DisplayName("duplicate approve is idempotent")
    void approve_idempotent() {
        kycUserSyncService.applyApproved(approvedEvent("GHA-123456789-0"));
        kycUserSyncService.applyApproved(approvedEvent("GHA-999999999-9"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(updated.getGhanaCardNumber()).isEqualTo("GHA-123456789-0");
    }

    @Test
    @DisplayName("reject with rejectionCount=1 sets kyc_status REJECTED")
    void reject_updates_user() {
        kycUserSyncService.applyRejected(rejectedEvent("Document unreadable", 1));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getKycStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    @DisplayName("duplicate reject at the same rejectionCount is idempotent")
    void reject_idempotent() {
        kycUserSyncService.applyRejected(rejectedEvent("First reason", 1));
        kycUserSyncService.applyRejected(rejectedEvent("Second reason", 1));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getKycStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    @DisplayName("v0.5-035: a second rejection (rejectionCount=2) transitions REJECTED to RESUBMISSION_REQUIRED")
    void second_reject_flags_for_resubmission() {
        kycUserSyncService.applyRejected(rejectedEvent("First reason", 1));
        kycUserSyncService.applyRejected(rejectedEvent("Second reason", 2));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getKycStatus()).isEqualTo(KycStatus.RESUBMISSION_REQUIRED);
    }

    @Test
    @DisplayName("v0.5-035: a single submission whose rejectionCount is already 2+ goes straight to RESUBMISSION_REQUIRED")
    void reject_with_count_two_from_the_start_flags_directly() {
        kycUserSyncService.applyRejected(rejectedEvent("Second reason", 2));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getKycStatus()).isEqualTo(KycStatus.RESUBMISSION_REQUIRED);
    }

    @Test
    @DisplayName("v0.5-035: duplicate reject at rejectionCount=2 is idempotent")
    void second_reject_idempotent() {
        kycUserSyncService.applyRejected(rejectedEvent("First reason", 1));
        kycUserSyncService.applyRejected(rejectedEvent("Second reason", 2));
        kycUserSyncService.applyRejected(rejectedEvent("Replayed second reason", 2));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getKycStatus()).isEqualTo(KycStatus.RESUBMISSION_REQUIRED);
    }

    private KycApprovedEvent approvedEvent(String ghanaCardNumber) {
        return new KycApprovedEvent(
                UUID.randomUUID().toString(),
                KycApprovedEvent.EVENT_TYPE,
                "1.0",
                "kyc-service",
                Instant.now(),
                "corr-1",
                new KycApprovedEvent.Payload(submissionId, user.getId(), ghanaCardNumber));
    }

    private KycRejectedEvent rejectedEvent(String reason, int rejectionCount) {
        return new KycRejectedEvent(
                UUID.randomUUID().toString(),
                KycRejectedEvent.EVENT_TYPE,
                "1.0",
                "kyc-service",
                Instant.now(),
                "corr-2",
                new KycRejectedEvent.Payload(submissionId, user.getId(), reason, rejectionCount));
    }
}
