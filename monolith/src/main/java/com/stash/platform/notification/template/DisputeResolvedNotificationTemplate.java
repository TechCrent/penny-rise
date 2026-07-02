package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * "dispute resolved" is in the AC's high-priority email list.
 * DisputeClosedEvent (close-no-action) has NO template — not in the AC's
 * explicit event list; flagged as a gap rather than silently added.
 */
@Component
public class DisputeResolvedNotificationTemplate implements NotificationTemplate {

    @Override
    public String supportedEventType() { return "DisputeResolvedEvent"; }

    @Override
    public boolean requiresEmail() { return true; }

    @Override
    public RenderedNotification render(JsonNode payload) {
        return new RenderedNotification(
                UUID.fromString(payload.get("raisedByUserId").asText()),
                "DISPUTE_RESOLVED",
                "Your dispute has been resolved",
                "Check the app for details on the outcome.",
                "stash://disputes/" + payload.get("disputeId").asText(),
                payload);
    }
}
