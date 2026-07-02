package com.stash.platform.notification.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationTemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateRegistry.class);

    private final Map<String, NotificationTemplate> templatesByEventType;

    public NotificationTemplateRegistry(List<NotificationTemplate> templates) {
        this.templatesByEventType = templates.stream()
                .collect(Collectors.toMap(NotificationTemplate::supportedEventType, Function.identity()));
    }

    /**
     * Empty for event types with no template — not every event on the bound exchanges
     * is user-facing (internal reconciliation, etc.), so this is expected, not an error.
     */
    public Optional<NotificationTemplate> find(String eventType) {
        var template = templatesByEventType.get(eventType);
        if (template == null) {
            log.debug("No notification template for event_type={} — not a user-facing event", eventType);
        }
        return Optional.ofNullable(template);
    }
}
