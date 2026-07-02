package com.stash.challenge.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "badges", schema = "challenge")
public class BadgeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "badge_code",  nullable = false, length = 100)
    private String badgeCode;

    @Column(name = "badge_name",  nullable = false, length = 255)
    private String badgeName;

    @Column(name = "asset_name",  nullable = false, length = 255)
    private String assetName;

    @Column(name = "created_at",  nullable = false, updatable = false)
    private Instant createdAt;

    protected BadgeEntity() {}

    public UUID   getId()        { return id; }
    public String getBadgeCode() { return badgeCode; }
    public String getBadgeName() { return badgeName; }
    public String getAssetName() { return assetName; }
}
