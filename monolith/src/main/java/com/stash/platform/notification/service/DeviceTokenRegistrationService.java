package com.stash.platform.notification.service;

import com.stash.platform.notification.domain.DeviceTokenEntity;
import com.stash.platform.notification.repository.DeviceTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DeviceTokenRegistrationService {

    /**
     * Expo's documented format is ExponentPushToken[<opaque-id>]. The opaque
     * id is treated permissively (alphanumeric, dash, underscore) — tighten
     * if Expo's actual token alphabet is narrower.
     */
    private static final Pattern EXPO_TOKEN_PATTERN =
            Pattern.compile("^ExponentPushToken\\[[A-Za-z0-9_-]+]$");
    private static final Set<String> VALID_PLATFORMS = Set.of("IOS", "ANDROID");
    private static final int MAX_TOKENS_PER_USER = 5;

    private final DeviceTokenRepository repository;
    private final Clock clock;

    public DeviceTokenRegistrationService(DeviceTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock      = clock;
    }

    @Transactional
    public void register(UUID userId, String token, String platform) {
        if (token == null || !EXPO_TOKEN_PATTERN.matcher(token).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_INVALID_TOKEN_FORMAT: token does not match the expected "
                    + "ExponentPushToken[...] format.");
        }
        if (platform == null || !VALID_PLATFORMS.contains(platform)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "NOTIFICATION_INVALID_PLATFORM: platform must be IOS or ANDROID.");
        }

        Instant now = Instant.now(clock);
        var existing = repository.findByExpoPushToken(token);

        if (existing.isPresent()) {
            // Upsert path: reactivate and refresh the existing row.
            // The 5-token limit is intentionally NOT re-enforced here — refreshing
            // an existing token doesn't add a net-new active device.
            existing.get().refresh(userId, platform, now);
            repository.save(existing.get());
            return;
        }

        repository.save(DeviceTokenEntity.create(userId, null, token, platform, now));
        enforceTokenLimit(userId);
    }

    /**
     * LRU eviction: evicts least-recently-used device(s) when a user exceeds
     * MAX_TOKENS_PER_USER active tokens. "Least recently used" (last_used_at)
     * is preferred over "oldest registered" (registered_at) — the user's
     * daily-driver phone registered a year ago shouldn't be evicted ahead of
     * a device they tried once and abandoned.
     */
    private void enforceTokenLimit(UUID userId) {
        List<DeviceTokenEntity> active = repository.findActiveOrderedByLastUsedAsc(userId);
        int excess = active.size() - MAX_TOKENS_PER_USER;
        if (excess <= 0) return;

        List<DeviceTokenEntity> toDeactivate = active.subList(0, excess);
        toDeactivate.forEach(DeviceTokenEntity::deactivate);
        repository.saveAll(toDeactivate);
    }
}
