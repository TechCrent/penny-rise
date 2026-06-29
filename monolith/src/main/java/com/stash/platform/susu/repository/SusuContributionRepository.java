package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SusuContributionRepository extends JpaRepository<SusuContributionEntity, UUID> {

    /**
     * Finds this member's contribution for a specific round.
     * Used to validate the caller's contribution state before payment.
     */
    @Query("""
            SELECT c FROM SusuContributionEntity c
            WHERE c.susuRoundId = :roundId
              AND c.memberUserId = :userId
            """)
    Optional<SusuContributionEntity> findByRoundAndMember(
            @Param("roundId") UUID roundId,
            @Param("userId")  UUID userId);

    /**
     * Counts contributions for a round that are NOT in a terminal state.
     * Used after a payment to check if all members have paid.
     * Terminal states: PAID, MISSED, WAIVED.
     */
    @Query("""
            SELECT COUNT(c) FROM SusuContributionEntity c
            WHERE c.susuRoundId = :roundId
              AND c.status NOT IN ('PAID', 'MISSED', 'WAIVED')
            """)
    long countNonTerminalContributions(@Param("roundId") UUID roundId);

    /**
     * Sums collected_amount for all PAID contributions in a round.
     * Used to set actual_pot_amount on the round when fully collected.
     */
    @Query("""
            SELECT COALESCE(SUM(c.collectedAmount), 0)
            FROM SusuContributionEntity c
            WHERE c.susuRoundId = :roundId
              AND c.status = 'PAID'
            """)
    long sumCollectedAmountForRound(@Param("roundId") UUID roundId);
}
