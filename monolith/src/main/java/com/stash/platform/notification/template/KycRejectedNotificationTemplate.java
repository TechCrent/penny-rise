package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class KycRejectedNotificationTemplate implements NotificationTemplate {

    @Override
    public String supportedEventType() { return "KycRejected"; }

    @Override
    public boolean requiresEmail() { return true; }

    @Override
    public RenderedNotification render(JsonNode payload) {
        String reason = payload.has("reason") ? payload.get("reason").asText()
                : "Please review and resubmit.";
        return new RenderedNotification(
                UUID.fromString(payload.get("user_id").asText()),
                "KYC_REJECTED",
                "Verification needs attention",
                reason,
                "stash://kyc/resubmit",
                payload);
    }
}
