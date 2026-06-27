package com.stash.payments.paystack.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paystack_sub_accounts", schema = "paystack")
public class PaystackSubaccountEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "owner_type",                nullable = false, length = 50)
    private String ownerType;

    @Column(name = "owner_id",                  nullable = false)
    private UUID ownerId;

    @Column(name = "paystack_subaccount_code",  nullable = false, unique = true, length = 255)
    private String paystackSubaccountCode;

    @Column(name = "ledger_account_id",         nullable = false)
    private UUID ledgerAccountId;

    @Column(name = "status",                    nullable = false, length = 50)
    private String status;

    @Column(name = "metadata",                  columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at",                nullable = false, updatable = false)
    private Instant createdAt;

    protected PaystackSubaccountEntity() {}

    public PaystackSubaccountEntity(String ownerType, UUID ownerId,
                                    String paystackSubaccountCode,
                                    UUID ledgerAccountId,
                                    String metadata,
                                    Instant createdAt) {
        this.ownerType              = ownerType;
        this.ownerId                = ownerId;
        this.paystackSubaccountCode = paystackSubaccountCode;
        this.ledgerAccountId        = ledgerAccountId;
        this.status                 = "ACTIVE";
        this.metadata               = metadata;
        this.createdAt              = createdAt;
    }

    public UUID   getId()                      { return id; }
    public String getOwnerType()               { return ownerType; }
    public UUID   getOwnerId()                 { return ownerId; }
    public String getPaystackSubaccountCode()  { return paystackSubaccountCode; }
    public UUID   getLedgerAccountId()         { return ledgerAccountId; }
    public String getStatus()                  { return status; }
    public String getMetadata()                { return metadata; }
}
