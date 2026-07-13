package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;

public interface NotificationTemplate {

    String supportedEventType();

    /** Only "deposit, withdrawal, security alert, KYC decision, dispute resolved" per the AC. */
    boolean requiresEmail();

    /** Opt-in SMS channel; default off so existing templates stay email/push only. */
    default boolean requiresSms() {
        return false;
    }

    RenderedNotification render(JsonNode payload);
}
