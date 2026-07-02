package com.stash.platform.notification.api;

import com.stash.platform.notification.api.dto.NotificationInboxResponse;
import com.stash.platform.notification.service.NotificationInboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationInboxController {

    private final NotificationInboxService inboxService;

    public NotificationInboxController(NotificationInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public NotificationInboxResponse list(
            @RequestParam(name = "unread_only", required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return inboxService.listInbox(userId, unreadOnly, cursor, limit);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        inboxService.markRead(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        inboxService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
