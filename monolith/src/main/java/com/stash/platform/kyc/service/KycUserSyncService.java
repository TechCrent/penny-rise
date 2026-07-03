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

    /**
     * v0.5-035: rejectionCount (total REJECTED submissions for this user,
     * including this one, computed by kyc-service at publish time) decides
     * REJECTED vs RESUBMISSION_REQUIRED — see
     * KycSubmissionRepository.countByUserIdAndStatus in kyc-service.
     *
     * <p>Idempotency is status-based, same as before: a replayed event
     * that would produce the same status as the user already has is a
     * no-op. This also correctly allows the *forward* transition from
     * REJECTED to RESUBMISSION_REQUIRED on a genuinely new second
     * rejection, which a purely "already REJECTED means skip" check
     * (the previous behaviour) would have incorrectly swallowed.
     */
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

        if (KycStatus.APPROVED.equals(user.getKycStatus())) {
            log.debug("KycRejected idempotent skip: user already APPROVED userId={} submissionId={}",
                    userId, submissionId);
            return;
        }

        KycStatus targetStatus = event.payload().rejectionCount() >= 2
                ? KycStatus.RESUBMISSION_REQUIRED
                : KycStatus.REJECTED;

        if (targetStatus.equals(user.getKycStatus())) {
            log.debug("KycRejected idempotent skip: user already {} userId={} submissionId={}",
                    targetStatus, userId, submissionId);
            return;
        }

        user.setKycStatus(targetStatus);
        userRepository.save(user);

        log.info("User KYC status synced to {} userId={} submissionId={} rejectionCount={}",
                targetStatus, userId, submissionId, event.payload().rejectionCount());
    }
}
