package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuRoundEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SusuRoundRepository extends JpaRepository<SusuRoundEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SusuRoundEntity r WHERE r.id = :id")
    Optional<SusuRoundEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT r FROM SusuRoundEntity r
            WHERE r.susuGroupId = :groupId
              AND r.roundNumber = :roundNumber
            """)
    Optional<SusuRoundEntity> findByGroupAndRoundNumber(@Param("groupId")     UUID groupId,
                                                          @Param("roundNumber") int  roundNumber);

    @Query("""
            SELECT r FROM SusuRoundEntity r
            WHERE r.susuGroupId = :groupId
            ORDER BY r.roundNumber ASC
            """)
    List<SusuRoundEntity> findAllByGroup(@Param("groupId") UUID groupId);

    /**
     * Finds rounds stuck in DISBURSING status, with SKIP LOCKED so multiple
     * worker instances don't double-process the same round. Used by the
     * scheduled fallback poller to catch rounds whose event-driven
     * disbursement never completed (broker restart, consumer offline, or a
     * failed attempt that left the round in DISBURSING).
     *
     * <p>Native SQL because Spring Data's {@code @Lock} annotation has no
     * SKIP LOCKED mode — same pattern as VaultAutoUnlockWorker's candidate query.
     */
    @Query(value = """
            SELECT * FROM susu.susu_rounds
            WHERE status = 'DISBURSING'
            ORDER BY scheduled_collection_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SusuRoundEntity> findDisbursingRoundsForUpdate(@Param("batchSize") int batchSize);

    @Query("""
            SELECT r FROM SusuRoundEntity r
            WHERE r.susuGroupId     = :groupId
              AND r.recipientUserId = :userId
              AND r.status          = 'PENDING'
            ORDER BY r.roundNumber ASC
            """)
    List<SusuRoundEntity> findPendingRoundsByRecipient(@Param("groupId") UUID groupId,
                                                        @Param("userId")  UUID userId);
}
