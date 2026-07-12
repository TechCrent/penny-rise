package com.stash.challenge.repository;

import com.stash.challenge.domain.UserChallengeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface UserChallengeRepository extends JpaRepository<UserChallengeEntity, UUID> {

    List<UserChallengeEntity> findByUserId(UUID userId);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM challenge.user_challenges
                WHERE user_id = :userId AND challenge_id = :challengeId AND status = 'ACTIVE'
            )
            """, nativeQuery = true)
    boolean existsActiveEnrollment(@Param("userId") UUID userId, @Param("challengeId") UUID challengeId);

    // clearAutomatically flushes the persistence context so findNewlyCompletable
    // (called in the same transaction) sees the just-updated progress_amount values.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE challenge.user_challenges uc
            SET progress_amount = uc.progress_amount + :amount
            FROM challenge.savings_challenges sc
            WHERE uc.challenge_id = sc.id
              AND uc.user_id      = :userId
              AND uc.status       = 'ACTIVE'
              AND sc.challenge_type = 'SAVE_AMOUNT'
            """, nativeQuery = true)
    int incrementActiveSaveAmountProgress(@Param("userId") UUID userId, @Param("amount") long amount);

    @Query("""
            SELECT uc FROM UserChallengeEntity uc, SavingsChallengeEntity sc
            WHERE uc.challengeId  = sc.id
              AND uc.userId       = :userId
              AND uc.status       = 'ACTIVE'
              AND sc.targetAmount IS NOT NULL
              AND uc.progressAmount >= sc.targetAmount
            """)
    List<UserChallengeEntity> findNewlyCompletable(@Param("userId") UUID userId);
}
