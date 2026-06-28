package com.stash.platform.susu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "susu_groups", schema = "susu")
public class SusuGroupEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "organiser_user_id", nullable = false)
    private UUID organiserUserId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "contribution_amount", nullable = false)
    private long contributionAmount;

    @Column(name = "frequency", nullable = false)
    private String frequency;

    @Column(name = "target_member_count", nullable = false)
    private int targetMemberCount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "current_round_number")
    private Integer currentRoundNumber;

    @Column(name = "join_code", nullable = false)
    private String joinCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SusuGroupEntity() {}

    public static SusuGroupEntity create(UUID organiserUserId, String name,
                                          long contributionAmount, String frequency,
                                          int targetMemberCount, String joinCode,
                                          Instant now) {
        SusuGroupEntity g = new SusuGroupEntity();
        g.organiserUserId  = organiserUserId;
        g.name             = name;
        g.contributionAmount = contributionAmount;
        g.frequency        = frequency;
        g.targetMemberCount = targetMemberCount;
        g.joinCode         = joinCode;
        g.status           = "PENDING";
        g.createdAt        = now;
        return g;
    }

    // Getters
    public UUID      getId()                 { return id; }
    public UUID      getOrganiserUserId()    { return organiserUserId; }
    public String    getName()               { return name; }
    public long      getContributionAmount() { return contributionAmount; }
    public String    getFrequency()          { return frequency; }
    public int       getTargetMemberCount()  { return targetMemberCount; }
    public LocalDate getStartDate()          { return startDate; }
    public String    getStatus()             { return status; }
    public Integer   getCurrentRoundNumber() { return currentRoundNumber; }
    public String    getJoinCode()           { return joinCode; }
    public Instant   getCreatedAt()          { return createdAt; }
}
