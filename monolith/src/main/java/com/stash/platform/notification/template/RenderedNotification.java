package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record RenderedNotification(
        UUID userId, String notificationType, String title, String body, String deepLink, JsonNode data
) {}
