package com.stash.platform.transfer.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "monthly_transfer_quotas", schema = "transfer")
public class MonthlyTransferQuotaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "user_id",              nullable = false) private UUID userId;
    @Column(name = "year",                 nullable = false) private int  year;
    @Column(name = "month",                nullable = false) private int  month;
    @Column(name = "free_transfers_used",  nullable = false) private int  freeTransfersUsed;
    @Column(name = "paid_transfers_count", nullable = false) private int  paidTransfersCount;

    protected MonthlyTransferQuotaEntity() {}

    public static MonthlyTransferQuotaEntity create(UUID userId, int year, int month) {
        MonthlyTransferQuotaEntity q = new MonthlyTransferQuotaEntity();
        q.userId              = userId;
        q.year                = year;
        q.month               = month;
        q.freeTransfersUsed   = 0;
        q.paidTransfersCount  = 0;
        return q;
    }

    /** Increments the appropriate counter and returns whether this was a free transfer. */
    public boolean consumeOneTransfer(int freeQuotaLimit) {
        if (freeTransfersUsed < freeQuotaLimit) {
            freeTransfersUsed++;
            return true;
        } else {
            paidTransfersCount++;
            return false;
        }
    }

    public UUID getId()                 { return id; }
    public UUID getUserId()             { return userId; }
    public int  getYear()               { return year; }
    public int  getMonth()              { return month; }
    public int  getFreeTransfersUsed()  { return freeTransfersUsed; }
    public int  getPaidTransfersCount() { return paidTransfersCount; }
}
