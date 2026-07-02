package com.stash.platform.notification.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification")
public class NotificationEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "user_id",           nullable = false)
    private UUID userId;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "channel",           nullable = false, length = 50)
    private String channel;

    @Column(name = "title",             nullable = false, length = 255)
    private String title;

    @Column(name = "body",              nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "deep_link",         length = 500)
    private String deepLink;

    @Column(name = "payload",           columnDefinition = "JSONB")
    private String payloadJson;

    @Column(name = "delivery_status",   nullable = false, length = 50)
    private String deliveryStatus;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at",        nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationEntity() {}

    public static NotificationEntity create(UUID userId, String notificationType, String channel,
                                             String title, String body, String deepLink,
                                             JsonNode payload, String deliveryStatus, Instant createdAt) {
        NotificationEntity n = new NotificationEntity();
        n.id               = UUID.randomUUID(); // pre-set so unit tests can call getId()
        n.userId           = userId;
        n.notificationType = notificationType;
        n.channel          = channel;
        n.title            = title;
        n.body             = body;
        n.deepLink         = deepLink;
        n.payloadJson      = serializePayload(payload);
        n.deliveryStatus   = deliveryStatus;
        n.deliveryAttempts = 0;
        n.createdAt        = createdAt;
        return n;
    }

    private static String serializePayload(JsonNode node) {
        if (node == null) return null;
        try { return MAPPER.writeValueAsString(node); } catch (Exception e) { return "{}"; }
    }

    public UUID    getId()             { return id; }
    public UUID    getUserId()         { return userId; }
    public String  getChannel()        { return channel; }
    public String  getDeliveryStatus() { return deliveryStatus; }
    public int     getDeliveryAttempts() { return deliveryAttempts; }
    public Instant getCreatedAt()      { return createdAt; }
}
