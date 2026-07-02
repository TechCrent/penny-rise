package com.stash.challenge.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_challenges", schema = "challenge")
public class UserChallengeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "user_id",         nullable = false)
    private UUID userId;

    @Column(name = "challenge_id",    nullable = false)
    private UUID challengeId;

    @Column(name = "status",          nullable = false, length = 50)
    private String status;

    @Column(name = "progress_amount", nullable = false)
    private long progressAmount;

    @Column(name = "progress_count",  nullable = false)
    private int progressCount;

    @Column(name = "started_at",      nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected UserChallengeEntity() {}

    public static UserChallengeEntity create(UUID userId, UUID challengeId, Instant now) {
        UserChallengeEntity uc = new UserChallengeEntity();
        uc.id             = UUID.randomUUID();
        uc.userId         = userId;
        uc.challengeId    = challengeId;
        uc.status         = "ACTIVE";
        uc.progressAmount = 0L;
        uc.progressCount  = 0;
        uc.startedAt      = now;
        return uc;
    }

    public void complete(Instant now) {
        this.status      = "COMPLETED";
        this.completedAt = now;
    }

    public UUID    getId()             { return id; }
    public UUID    getUserId()         { return userId; }
    public UUID    getChallengeId()    { return challengeId; }
    public String  getStatus()         { return status; }
    public long    getProgressAmount() { return progressAmount; }
    public Instant getStartedAt()      { return startedAt; }
}
