package com.stash.payments.ledger.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts", schema = "ledger")
public class LedgerAccountEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "account_type",       nullable = false, length = 50)
    private String accountType;

    @Column(name = "owner_type",         nullable = false, length = 50)
    private String ownerType;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "status",             nullable = false, length = 50)
    private String status;

    @Column(name = "description",        length = 255)
    private String description;

    @Column(name = "created_at",         nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerAccountEntity() {}

    public LedgerAccountEntity(String accountType, String ownerType,
                               UUID ownerId, String description, Instant createdAt) {
        this.id          = UUID.randomUUID();
        this.accountType = accountType;
        this.ownerType   = ownerType;
        this.ownerId     = ownerId;
        this.description = description;
        this.status      = "ACTIVE";
        this.createdAt   = createdAt;
    }

    public UUID   getId()                { return id; }
    public String getAccountType()       { return accountType; }
    public String getOwnerType()         { return ownerType; }
    public UUID   getOwnerId()           { return ownerId; }
    public String getStatus()            { return status; }
    public String getExternalReference() { return externalReference; }

    public void setExternalReference(String ref) {
        this.externalReference = ref;
    }
}
