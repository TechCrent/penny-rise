package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** NOT in the AC's high-priority email list — in-app + push only. */
@Component
public class UserSuspendedNotificationTemplate implements NotificationTemplate {

    @Override
    public String supportedEventType() { return "UserSuspendedEvent"; }

    @Override
    public boolean requiresEmail() { return false; }

    @Override
    public RenderedNotification render(JsonNode payload) {
        return new RenderedNotification(
                UUID.fromString(payload.get("userId").asText()),
                "ACCOUNT_SUSPENDED",
                "Your account has been suspended",
                "Contact support for details on how to resolve this.",
                "stash://support",
                payload);
    }
}
