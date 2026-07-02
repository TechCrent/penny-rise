package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserRestoredNotificationTemplate implements NotificationTemplate {

    @Override
    public String supportedEventType() { return "UserRestoredEvent"; }

    @Override
    public boolean requiresEmail() { return false; }

    @Override
    public RenderedNotification render(JsonNode payload) {
        return new RenderedNotification(
                UUID.fromString(payload.get("userId").asText()),
                "ACCOUNT_RESTORED",
                "Your account has been restored",
                "You can now use Stash normally again.",
                "stash://home",
                payload);
    }
}
