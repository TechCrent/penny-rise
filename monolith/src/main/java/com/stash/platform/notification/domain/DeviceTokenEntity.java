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

    @Column(name = "device_id",       nullable = false, length = 255)
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

    @Override public UUID   id()             { return id; }
    @Override public String expoPushToken()  { return expoPushToken; }

    public UUID    getUserId()      { return userId; }
    public String  getDeviceId()    { return deviceId; }
    public String  getPlatform()    { return platform; }
    public boolean isActive()       { return isActive; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getLastUsedAt()   { return lastUsedAt; }
}
