package com.stash.platform.susu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "susu_memberships", schema = "susu")
public class SusuMembershipEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "susu_group_id", nullable = false)
    private UUID susuGroupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rotation_position")
    private Integer rotationPosition;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected SusuMembershipEntity() {}

    /** Creates a new ACTIVE membership row for any member (organiser or joiner) — rotation_position is assigned later, at activation. */
    public static SusuMembershipEntity create(UUID susuGroupId,
                                               UUID userId,
                                               Instant now) {
        SusuMembershipEntity m = new SusuMembershipEntity();
        m.susuGroupId      = susuGroupId;
        m.userId           = userId;
        m.rotationPosition = null;   // assigned at activation
        m.status           = "ACTIVE";
        m.joinedAt         = now;
        return m;
    }

    public UUID    getId()               { return id; }
    public UUID    getSusuGroupId()      { return susuGroupId; }
    public UUID    getUserId()           { return userId; }
    public Integer getRotationPosition() { return rotationPosition; }
    public String  getStatus()           { return status; }
    public Instant getJoinedAt()         { return joinedAt; }
    public Instant getRemovedAt()        { return removedAt; }
}
