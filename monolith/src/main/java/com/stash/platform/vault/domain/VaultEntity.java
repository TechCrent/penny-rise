package com.stash.platform.vault.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vaults", schema = "vault")
public class VaultEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "owner_user_id",          nullable = false)
    private UUID ownerUserId;

    @Column(name = "name",                   nullable = false, length = 100)
    private String name;

    @Column(name = "vault_type",             nullable = false, length = 20)
    private String vaultType;

    @Column(name = "status",                 nullable = false, length = 50)
    private String status;

    @Column(name = "ledger_account_id",      nullable = false)
    private UUID ledgerAccountId;

    @Column(name = "unlock_by_date")
    private Instant unlockByDate;

    @Column(name = "unlock_target_amount")
    private Long unlockTargetAmount;

    @Column(name = "unlock_condition_logic", length = 10)
    private String unlockConditionLogic;

    @Column(name = "early_exit_in_progress", nullable = false)
    private boolean earlyExitInProgress;

    @Column(name = "created_at",             nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    protected VaultEntity() {}

    public static VaultEntity createStandard(UUID ownerUserId, String name,
                                              UUID ledgerAccountId, Instant now) {
        VaultEntity v = new VaultEntity();
        v.ownerUserId       = ownerUserId;
        v.name              = name;
        v.vaultType         = "STANDARD";
        v.status            = "ACTIVE";
        v.ledgerAccountId   = ledgerAccountId;
        v.earlyExitInProgress = false;
        v.createdAt         = now;
        return v;
    }

    public static VaultEntity createLocked(UUID ownerUserId, String name,
                                            UUID ledgerAccountId,
                                            Instant unlockByDate,
                                            Long unlockTargetAmount,
                                            String unlockConditionLogic,
                                            Instant now) {
        VaultEntity v = new VaultEntity();
        v.ownerUserId           = ownerUserId;
        v.name                  = name;
        v.vaultType             = "LOCKED";
        v.status                = "ACTIVE";
        v.ledgerAccountId       = ledgerAccountId;
        v.unlockByDate          = unlockByDate;
        v.unlockTargetAmount    = unlockTargetAmount;
        v.unlockConditionLogic  = unlockConditionLogic;
        v.earlyExitInProgress   = false;
        v.createdAt             = now;
        return v;
    }

    // Getters
    public UUID    getId()                    { return id; }
    public UUID    getOwnerUserId()           { return ownerUserId; }
    public String  getName()                  { return name; }
    public String  getVaultType()             { return vaultType; }
    public String  getStatus()                { return status; }
    public UUID    getLedgerAccountId()       { return ledgerAccountId; }
    public Instant getUnlockByDate()          { return unlockByDate; }
    public Long    getUnlockTargetAmount()    { return unlockTargetAmount; }
    public String  getUnlockConditionLogic()  { return unlockConditionLogic; }
    public boolean isEarlyExitInProgress()    { return earlyExitInProgress; }
    public Instant getCreatedAt()             { return createdAt; }
    public Instant getDeletedAt()             { return deletedAt; }
    public Instant getUnlockedAt()            { return unlockedAt; }
}
