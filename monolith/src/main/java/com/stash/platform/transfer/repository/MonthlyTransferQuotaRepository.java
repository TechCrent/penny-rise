package com.stash.platform.transfer.repository;

import com.stash.platform.transfer.domain.MonthlyTransferQuotaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
