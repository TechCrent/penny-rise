package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;

public interface NotificationTemplate {

    String supportedEventType();

    /** Only "deposit, withdrawal, security alert, KYC decision, dispute resolved" per the AC. */
    boolean requiresEmail();

    RenderedNotification render(JsonNode payload);
}
