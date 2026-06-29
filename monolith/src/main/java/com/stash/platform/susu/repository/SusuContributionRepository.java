package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SusuContributionRepository extends JpaRepository<SusuContributionEntity, UUID> {

    @Query("""
            SELECT c FROM SusuContributionEntity c
            WHERE c.susuRoundId = :roundId
            ORDER BY c.createdAt ASC
            """)
    List<SusuContributionEntity> findByRound(@Param("roundId") UUID roundId);

    @Query("""
            SELECT c FROM SusuContributionEntity c
            WHERE c.susuRoundId = :roundId
              AND c.memberUserId = :userId
            """)
    Optional<SusuContributionEntity> findByRoundAndMember(
            @Param("roundId") UUID roundId,
            @Param("userId")  UUID userId);

    @Query("""
            SELECT COUNT(c) FROM SusuContributionEntity c
            WHERE c.susuRoundId = :roundId
              AND c.status NOT IN ('PAID', 'MISSED', 'WAIVED')
            """)
    long countNonTerminalContributions(@Param("roundId") UUID roundId);

    @Query("""
            SELECT COALESCE(SUM(c.collectedAmount), 0)
            FROM SusuContributionEntity c
            WHERE c.susuRoundId = :roundId
              AND c.status = 'PAID'
            """)
    long sumCollectedAmountForRound(@Param("roundId") UUID roundId);

    /**
     * Finds PENDING contributions where the round's scheduled_collection_at
     * has passed the grace period cutoff. Used by the late-penalty job.
     * Only PENDING rows are returned — LATE rows have already been
     * processed, making re-runs idempotent.
     */
    @Query(value = """
            SELECT c.* FROM susu.susu_contributions c
            INNER JOIN susu.susu_rounds r ON r.id = c.susu_round_id
            WHERE c.status = 'PENDING'
              AND r.status = 'COLLECTING'
              AND r.scheduled_collection_at <= :cutoff
            ORDER BY c.created_at ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    List<SusuContributionEntity> findOverduePendingContributions(
            @Param("cutoff")    Instant cutoff,
            @Param("batchSize") int batchSize);

    @Modifying
    @Query("""
            UPDATE SusuContributionEntity c
            SET c.status = 'MISSED'
            WHERE c.susuGroupId  = :groupId
              AND c.memberUserId = :userId
              AND c.status       = 'PENDING'
            """)
    int cancelPendingContributions(@Param("groupId") UUID groupId,
                                   @Param("userId")  UUID userId);
}
