package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SusuGroupRepository extends JpaRepository<SusuGroupEntity, UUID> {

    Optional<SusuGroupEntity> findByJoinCode(String joinCode);

    /**
     * Counts active groups where the user is the organiser.
     * Used for free-tier limit enforcement.
     * PENDING and ACTIVE groups both count toward the limit —
     * a user cannot circumvent the limit by leaving groups in PENDING forever.
     */
    @Query("""
            SELECT COUNT(g) FROM SusuGroupEntity g
            WHERE g.organiserUserId = :userId
              AND g.status IN ('PENDING', 'ACTIVE')
            """)
    long countActiveGroupsByOrganiser(@Param("userId") UUID userId);
}
