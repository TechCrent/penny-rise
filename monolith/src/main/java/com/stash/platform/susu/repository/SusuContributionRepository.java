package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SusuContributionRepository extends JpaRepository<SusuContributionEntity, UUID> {

    @Query("""
            SELECT c FROM SusuContributionEntity c
            WHERE c.susuRoundId = :roundId
            ORDER BY c.createdAt ASC
            """)
    List<SusuContributionEntity> findByRound(@Param("roundId") UUID roundId);
}
