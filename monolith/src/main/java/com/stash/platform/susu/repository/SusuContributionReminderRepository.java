package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuContributionReminderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface SusuContributionReminderRepository
        extends JpaRepository<SusuContributionReminderEntity, UUID> {

    /** Returns all reminder types already emitted for a given contribution. */
    @Query("""
            SELECT r.reminderType FROM SusuContributionReminderEntity r
            WHERE r.contributionId = :contributionId
            """)
    Set<String> findSentTypesForContribution(@Param("contributionId") UUID contributionId);

    /** Checks if a specific reminder type has already been emitted for a contribution. */
    @Query("""
            SELECT COUNT(r) > 0 FROM SusuContributionReminderEntity r
            WHERE r.contributionId = :contributionId
              AND r.reminderType   = :reminderType
            """)
    boolean existsByContributionAndType(
            @Param("contributionId") UUID   contributionId,
            @Param("reminderType")   String reminderType);
}
