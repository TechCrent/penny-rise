package com.stash.platform.transfer.repository;

import com.stash.platform.transfer.domain.MonthlyTransferQuotaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface MonthlyTransferQuotaRepository extends JpaRepository<MonthlyTransferQuotaEntity, UUID> {

    /**
     * Loads the quota row with a pessimistic write lock.
     *
     * Two concurrent transfers for the same user serialize here — the second blocks
     * until the first commits, then reads the already-incremented count and correctly
     * applies the fee if needed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT q FROM MonthlyTransferQuotaEntity q
            WHERE q.userId = :userId
              AND q.year   = :year
              AND q.month  = :month
            """)
    Optional<MonthlyTransferQuotaEntity> findByUserAndMonthForUpdate(
            @Param("userId") UUID userId,
            @Param("year")   int  year,
            @Param("month")  int  month);

    @Query("""
            SELECT q FROM MonthlyTransferQuotaEntity q
            WHERE q.userId = :userId
              AND q.year   = :year
              AND q.month  = :month
            """)
    Optional<MonthlyTransferQuotaEntity> findByUserAndMonth(
            @Param("userId") UUID userId,
            @Param("year")   int  year,
            @Param("month")  int  month);

    /**
     * Inserts a fresh zero-count quota row if none exists for this user/month.
     * Uses ON CONFLICT DO NOTHING so concurrent first-transfers are safe — exactly one
     * row is inserted regardless of how many threads race here simultaneously.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO transfer.monthly_transfer_quotas
                (id, user_id, year, month, free_transfers_used, paid_transfers_count)
            VALUES (:id, :userId, :year, :month, 0, 0)
            ON CONFLICT (user_id, year, month) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id")     UUID id,
                        @Param("userId") UUID userId,
                        @Param("year")   int  year,
                        @Param("month")  int  month);
}
