package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuGroupEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * Loads the group by join_code and acquires a row-level lock.
     * Used by the join endpoint to prevent concurrent joins from
     * racing past the member count check.
     *
     * <p>The lock is held until the transaction commits — once a
     * joiner acquires it, all other concurrent join attempts for the
     * same group queue behind this transaction.
     */
    @Query(value = """
            SELECT * FROM susu.susu_groups
            WHERE join_code = :joinCode
            FOR UPDATE
            """, nativeQuery = true)
    Optional<SusuGroupEntity> findByJoinCodeForUpdate(@Param("joinCode") String joinCode);

    @Query("SELECT g FROM SusuGroupEntity g WHERE g.status = :status")
    List<SusuGroupEntity> findByStatus(@Param("status") String status, Pageable pageable);

    /**
     * PENDING/ACTIVE groups organised by this user, oldest first — used by
     * SusuFreezingService (v0.5-029) to determine which groups beyond the
     * limit get frozen. Oldest-first means the user's newest group stays
     * active, matching VaultFreezingService's convention.
     */
    @Query("""
            SELECT g FROM SusuGroupEntity g
            WHERE g.organiserUserId = :userId
              AND g.status IN ('PENDING', 'ACTIVE')
            ORDER BY g.createdAt ASC
            """)
    List<SusuGroupEntity> findActiveByOrganiserOrderByCreatedAtAsc(@Param("userId") UUID userId);

    /**
     * Freezes a single PENDING or ACTIVE group — a no-op (returns 0) if the
     * group is already in some other status.
     */
    @Modifying
    @Query("""
            UPDATE SusuGroupEntity g SET g.status = 'FROZEN'
            WHERE g.id = :groupId AND g.status IN ('PENDING', 'ACTIVE')
            """)
    int freezeIfActive(@Param("groupId") UUID groupId);
}
