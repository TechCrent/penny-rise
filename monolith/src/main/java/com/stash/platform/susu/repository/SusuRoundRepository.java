package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuRoundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SusuRoundRepository extends JpaRepository<SusuRoundEntity, UUID> {

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
}
