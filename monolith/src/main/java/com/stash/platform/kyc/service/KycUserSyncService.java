package com.stash.platform.kyc.service;

import com.stash.platform.kyc.event.KycApprovedEvent;
import com.stash.platform.kyc.event.KycRejectedEvent;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Applies KYC terminal decision events to {@code user_module.users}.
 *
 * <p>Idempotency: repeated approve events for an already-{@code APPROVED}
 * user are no-ops; repeated reject events for an already-{@code REJECTED}
 * user are no-ops. Ghana Card numbers are never logged.
 */
@Service
public class KycUserSyncService {

    private static final Logger log = LoggerFactory.getLogger(KycUserSyncService.class);

    private final UserRepository userRepository;

    public KycUserSyncService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void applyApproved(KycApprovedEvent event) {
        UUID userId = event.payload().userId();
        UUID submissionId = event.payload().submissionId();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isDeleted()) {
            log.warn("KycApproved ignored: user missing or deleted userId={} submissionId={}",
                    userId, submissionId);
            return;
        }

        if (KycStatus.APPROVED.equals(user.getKycStatus())) {
            log.debug("KycApproved idempotent skip: user already APPROVED userId={} submissionId={}",
                    userId, submissionId);
            return;
        }

        user.setKycStatus(KycStatus.APPROVED);
        user.setGhanaCardNumber(event.payload().ghanaCardNumber());
        userRepository.save(user);

        log.info("User KYC status synced to APPROVED userId={} submissionId={}", userId, submissionId);
    }

    @Transactional
    public void applyRejected(KycRejectedEvent event) {
        UUID userId = event.payload().userId();
        UUID submissionId = event.payload().submissionId();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isDeleted()) {
            log.warn("KycRejected ignored: user missing or deleted userId={} submissionId={}",
                    userId, submissionId);
            return;
        }

        if (KycStatus.REJECTED.equals(user.getKycStatus())) {
            log.debug("KycRejected idempotent skip: user already REJECTED userId={} submissionId={}",
                    userId, submissionId);
            return;
        }

        user.setKycStatus(KycStatus.REJECTED);
        userRepository.save(user);

        log.info("User KYC status synced to REJECTED userId={} submissionId={}", userId, submissionId);
    }
}
