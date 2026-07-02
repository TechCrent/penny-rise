package com.stash.platform.notification.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_tokens", schema = "notification")
public class DeviceTokenEntity implements DeviceTokenLike {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "user_id",         nullable = false)
    private UUID userId;

    @Column(name = "device_id",       nullable = true, length = 255)
    private String deviceId;

    @Column(name = "expo_push_token", nullable = false, length = 255)
    private String expoPushToken;

    @Column(name = "platform",        nullable = false, length = 20)
    private String platform;

    @Column(name = "is_active",       nullable = false)
    private boolean isActive;

    @Column(name = "registered_at",   nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "last_used_at",    nullable = false)
    private Instant lastUsedAt;

    protected DeviceTokenEntity() {}

    public static DeviceTokenEntity create(UUID userId, String deviceId, String expoPushToken,
                                            String platform, Instant now) {
        DeviceTokenEntity t = new DeviceTokenEntity();
        t.id             = UUID.randomUUID();
        t.userId         = userId;
        t.deviceId       = deviceId;
        t.expoPushToken  = expoPushToken;
        t.platform       = platform;
        t.isActive       = true;
        t.registeredAt   = now;
        t.lastUsedAt     = now;
        return t;
    }

    /**
     * Called when the same token is re-submitted by a user — reactivates
     * it (handles the case where v0.5-013's DeviceNotRegistered handling
     * deactivated it and the app re-registers on a later launch) and
     * bumps last_used_at so LRU eviction keeps this device alive.
     *
     * NOTE: if the same token string is submitted by a different user
     * than currently owns the row (factory-reset + new account before
     * old row was cleaned up), this silently reassigns ownership. The
     * safer fix is a 409 conflict — flagged as a policy decision, not
     * resolved unilaterally here.
     */
    public void refresh(UUID userId, String platform, Instant now) {
        this.userId     = userId;
        this.platform   = platform;
        this.lastUsedAt = now;
        this.isActive   = true;
    }

    /** In-memory deactivation — caller must persist via save/saveAll. */
    public void deactivate() {
        this.isActive = false;
    }

    @Override public UUID   id()             { return id; }
    @Override public String expoPushToken()  { return expoPushToken; }

    public UUID    getUserId()       { return userId; }
    public String  getDeviceId()     { return deviceId; }
    public String  getPlatform()     { return platform; }
    public boolean isActive()        { return isActive; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getLastUsedAt()   { return lastUsedAt; }
}
