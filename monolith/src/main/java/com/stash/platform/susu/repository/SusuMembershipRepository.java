package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SusuMembershipRepository extends JpaRepository<SusuMembershipEntity, UUID> {

    /**
     * Counts active members for a group. Used to enforce target_member_count.
     * Only ACTIVE members count — REMOVED and COMPLETED memberships do not
     * occupy a slot.
     */
    @Query("""
            SELECT COUNT(m) FROM SusuMembershipEntity m
            WHERE m.susuGroupId = :groupId
              AND m.status = 'ACTIVE'
            """)
    long countActiveMembers(@Param("groupId") UUID groupId);

    /**
     * Checks whether a user is already a member of a group (any status).
     * Returning true for REMOVED members prevents re-joining a group
     * they have left — which is intentional at v0.4.
     */
    @Query("""
            SELECT COUNT(m) > 0 FROM SusuMembershipEntity m
            WHERE m.susuGroupId = :groupId
              AND m.userId = :userId
            """)
    boolean existsByGroupIdAndUserId(@Param("groupId") UUID groupId,
                                      @Param("userId")  UUID userId);

    /**
     * Returns true if the user has an ACTIVE membership in the group.
     * Used by the contribution service to gate payment access.
     */
    @Query("""
            SELECT COUNT(m) > 0 FROM SusuMembershipEntity m
            WHERE m.susuGroupId = :groupId
              AND m.userId = :userId
              AND m.status = 'ACTIVE'
            """)
    boolean isActiveMember(@Param("groupId") UUID groupId,
                            @Param("userId")  UUID userId);
}
