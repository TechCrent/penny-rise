package com.stash.platform.kyc.consumer;

import com.stash.platform.kyc.config.MonolithMessagingConfig;
import com.stash.platform.kyc.event.KycApprovedEvent;
import com.stash.platform.kyc.event.KycRejectedEvent;
import com.stash.platform.kyc.service.KycUserSyncService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes terminal KYC decision events published by kyc-service and
 * updates the monolith's canonical {@code users} record.
 */
@Component
public class KycDecisionEventConsumer {

    private final KycUserSyncService kycUserSyncService;

    public KycDecisionEventConsumer(KycUserSyncService kycUserSyncService) {
        this.kycUserSyncService = kycUserSyncService;
    }

    @RabbitListener(queues = MonolithMessagingConfig.MONOLITH_KYC_APPROVED_QUEUE,
                     containerFactory = "kycDecisionListenerContainerFactory")
    public void onKycApproved(KycApprovedEvent event) {
        kycUserSyncService.applyApproved(event);
    }

    @RabbitListener(queues = MonolithMessagingConfig.MONOLITH_KYC_REJECTED_QUEUE,
                     containerFactory = "kycDecisionListenerContainerFactory")
    public void onKycRejected(KycRejectedEvent event) {
        kycUserSyncService.applyRejected(event);
    }
}
