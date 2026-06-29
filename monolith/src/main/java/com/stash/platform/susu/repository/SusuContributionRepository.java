package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
