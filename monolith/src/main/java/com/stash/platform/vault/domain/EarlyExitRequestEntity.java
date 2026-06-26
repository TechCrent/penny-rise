package com.stash.platform.vault.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "locked_vault_early_exit_requests", schema = "vault")
public class EarlyExitRequestEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "vault_id",              nullable = false)
    private UUID vaultId;

    @Column(name = "requested_by_user_id",  nullable = false)
    private UUID requestedByUserId;

    @Column(name = "reason",                nullable = false, length = 50)
    private String reason;

    @Column(name = "balance_at_request",    nullable = false)
    private long balanceAtRequest;

    @Column(name = "penalty_amount",        nullable = false)
    private long penaltyAmount;

    @Column(name = "release_amount",        nullable = false)
    private long releaseAmount;

    @Column(name = "scheduled_release_at",  nullable = false)
    private Instant scheduledReleaseAt;

    @Column(name = "status",                nullable = false, length = 50)
    private String status;

    @Column(name = "created_at",            nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected EarlyExitRequestEntity() {}

    public static EarlyExitRequestEntity create(UUID vaultId, UUID userId,
                                                 String reason,
                                                 long balanceAtRequest,
                                                 long penaltyAmount,
                                                 long releaseAmount,
                                                 Instant scheduledReleaseAt,
                                                 Instant now) {
        EarlyExitRequestEntity r = new EarlyExitRequestEntity();
        r.vaultId            = vaultId;
        r.requestedByUserId  = userId;
        r.reason             = reason;
        r.balanceAtRequest   = balanceAtRequest;
        r.penaltyAmount      = penaltyAmount;
        r.releaseAmount      = releaseAmount;
        r.scheduledReleaseAt = scheduledReleaseAt;
        r.status             = "PENDING";
        r.createdAt          = now;
        return r;
    }

    public void markCompleted(Instant now) {
        this.status     = "COMPLETED";
        this.resolvedAt = now;
    }

    public void markCancelled(Instant now) {
        this.status     = "CANCELLED";
        this.resolvedAt = now;
    }

    // Getters
    public UUID    getId()                  { return id; }
    public UUID    getVaultId()             { return vaultId; }
    public UUID    getRequestedByUserId()   { return requestedByUserId; }
    public String  getReason()              { return reason; }
    public long    getBalanceAtRequest()    { return balanceAtRequest; }
    public long    getPenaltyAmount()       { return penaltyAmount; }
    public long    getReleaseAmount()       { return releaseAmount; }
    public Instant getScheduledReleaseAt()  { return scheduledReleaseAt; }
    public String  getStatus()              { return status; }
    public Instant getCreatedAt()           { return createdAt; }
    public Instant getResolvedAt()          { return resolvedAt; }
}
