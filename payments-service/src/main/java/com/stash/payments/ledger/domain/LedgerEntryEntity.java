package com.stash.payments.ledger.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", schema = "ledger")
public class LedgerEntryEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "ledger_transaction_id", nullable = false)
    private UUID ledgerTransactionId;

    @Column(name = "account_id",            nullable = false)
    private UUID accountId;

    @Column(name = "direction",             nullable = false, length = 10)
    private String direction;

    @Column(name = "amount",                nullable = false)
    private long amount;

    @Column(name = "narrative",             length = 255)
    private String narrative;

    @Column(name = "created_at",            nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {}

    public LedgerEntryEntity(UUID ledgerTransactionId, UUID accountId,
                      EntryDirection direction, long amount,
                      String narrative, Instant createdAt) {
        this.id                  = UUID.randomUUID();
        this.ledgerTransactionId = ledgerTransactionId;
        this.accountId           = accountId;
        this.direction           = direction.name();
        this.amount              = amount;
        this.narrative           = narrative;
        this.createdAt           = createdAt;
    }

    public UUID   getId()                  { return id; }
    public UUID   getLedgerTransactionId() { return ledgerTransactionId; }
    public UUID   getAccountId()           { return accountId; }
    public String getDirection()           { return direction; }
    public long   getAmount()              { return amount; }
}
