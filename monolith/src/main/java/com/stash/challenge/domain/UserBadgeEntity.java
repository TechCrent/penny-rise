package com.stash.challenge.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_badges", schema = "challenge")
public class UserBadgeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "user_id",                  nullable = false)
    private UUID userId;

    @Column(name = "badge_code",               nullable = false, length = 100)
    private String badgeCode;

    @Column(name = "badge_name",               nullable = false, length = 255)
    private String badgeName;

    @Column(name = "awarded_at",               nullable = false)
    private Instant awardedAt;

    @Column(name = "awarded_for_entity_type",  nullable = false, length = 50)
    private String awardedForEntityType;

    @Column(name = "awarded_for_entity_id")
    private UUID awardedForEntityId;

    protected UserBadgeEntity() {}

    public static UserBadgeEntity create(UUID userId, String badgeCode, String badgeName,
                                          String entityType, UUID entityId, Instant now) {
        UserBadgeEntity b = new UserBadgeEntity();
        b.id                   = UUID.randomUUID();
        b.userId               = userId;
        b.badgeCode            = badgeCode;
        b.badgeName            = badgeName;
        b.awardedAt            = now;
        b.awardedForEntityType = entityType;
        b.awardedForEntityId   = entityId;
        return b;
    }

    public UUID   getId()                   { return id; }
    public String getBadgeCode()            { return badgeCode; }
    public String getAwardedForEntityType() { return awardedForEntityType; }
}
