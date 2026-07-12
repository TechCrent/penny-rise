package com.stash.challenge.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "savings_challenges", schema = "challenge")
public class SavingsChallengeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "name",                 nullable = false, length = 255)
    private String name;

    @Column(name = "description",          nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "challenge_type",       nullable = false, length = 50)
    private String challengeType;

    @Column(name = "target_amount")
    private Long targetAmount;

    @Column(name = "target_duration_days")
    private Integer targetDurationDays;

    @Column(name = "system_owned",         nullable = false)
    private boolean systemOwned;

    @Column(name = "creator_user_id")
    private UUID creatorUserId;

    @Column(name = "is_active",            nullable = false)
    private boolean active;

    @Column(name = "badge_id")
    private UUID badgeId;

    @Column(name = "created_at",           nullable = false, updatable = false)
    private Instant createdAt;

    protected SavingsChallengeEntity() {}

    public UUID    getId()                { return id; }
    public String  getName()              { return name; }
    public String  getDescription()       { return description; }
    public String  getChallengeType()     { return challengeType; }
    public Long    getTargetAmount()      { return targetAmount; }
    public Integer getTargetDurationDays(){ return targetDurationDays; }
    public boolean isSystemOwned()        { return systemOwned; }
    public boolean isActive()             { return active; }
    public UUID    getBadgeId()           { return badgeId; }
}
