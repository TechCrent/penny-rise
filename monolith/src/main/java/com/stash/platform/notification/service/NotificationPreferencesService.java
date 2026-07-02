package com.stash.platform.notification.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Placeholder per the AC: "Notification preferences from v1.5-015 honoured
 * once that issue ships — placeholder check always-true in v0.5."
 * Replace isEnabled's body when v1.5-015 lands; call site in
 * NotificationDispatchService does not need to change.
 */
@Service
public class NotificationPreferencesService {

    public boolean isEnabled(UUID userId, String notificationType) {
        return true; // TODO(v1.5-015): read from user notification preferences
    }
}
