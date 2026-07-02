package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Event field names guessed — the KYC Service's kyc.submission.approved
 * payload shape has not been confirmed in this thread.
 */
@Component
public class KycApprovedNotificationTemplate implements NotificationTemplate {

    @Override
    public String supportedEventType() { return "KycApproved"; }

    @Override
    public boolean requiresEmail() { return true; }

    @Override
    public RenderedNotification render(JsonNode payload) {
        return new RenderedNotification(
                UUID.fromString(payload.get("user_id").asText()),
                "KYC_APPROVED",
                "You're verified!",
                "Your identity verification was successful. All features are now unlocked.",
                "stash://home",
                payload);
    }
}
