package com.stash.admin.repository;

import com.stash.admin.domain.DisputeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<DisputeEntity, UUID> {

    @Query("""
            SELECT d FROM DisputeEntity d
            WHERE (:status IS NULL OR d.status = :status)
            ORDER BY
                CASE d.priority
                    WHEN 'CRITICAL' THEN 1
                    WHEN 'HIGH'     THEN 2
                    WHEN 'NORMAL'   THEN 3
                    WHEN 'LOW'      THEN 4
                END,
                d.createdAt ASC
            """)
    List<DisputeEntity> findQueue(@Param("status") String status);

    /** Backs the admin dashboard's "open disputes" count. */
    long countByStatus(String status);

    @Query("""
            SELECT d FROM DisputeEntity d
            WHERE d.raisedByUserId = :userId
            ORDER BY d.createdAt DESC
            """)
    List<DisputeEntity> findByRaisedByUser(@Param("userId") UUID userId);

    @Query("""
            SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END
            FROM DisputeEntity d
            WHERE d.relatedEntityType = :entityType
              AND d.relatedEntityId   = :entityId
              AND d.status IN ('OPEN', 'IN_REVIEW')
            """)
    boolean hasOpenDisputeForEntity(
            @Param("entityType") String entityType,
            @Param("entityId")   UUID   entityId);

    @Query("""
            SELECT COUNT(d) > 0 FROM DisputeEntity d
            WHERE d.raisedByUserId    = :userId
              AND d.relatedEntityType = :relatedEntityType
              AND d.relatedEntityId   = :relatedEntityId
              AND d.status IN ('OPEN', 'IN_REVIEW')
            """)
    boolean existsActiveForEntity(@Param("userId") UUID userId,
                                   @Param("relatedEntityType") String relatedEntityType,
                                   @Param("relatedEntityId") UUID relatedEntityId);

    /**
     * Priority-ordered, paginated queue. CRITICAL shown first; ties broken
     * by oldest-first (FIFO within same priority).
     */
    @Query("""
            SELECT d FROM DisputeEntity d
            WHERE (:status IS NULL OR d.status = :status)
            ORDER BY
                CASE d.priority
                    WHEN 'CRITICAL' THEN 4
                    WHEN 'HIGH'     THEN 3
                    WHEN 'NORMAL'   THEN 2
                    WHEN 'LOW'      THEN 1
                    ELSE 0
                END DESC,
                d.createdAt ASC
            """)
    Page<DisputeEntity> findQueuePaged(@Param("status") String status, Pageable pageable);

    /**
     * Allows (re)assignment from OPEN or IN_REVIEW; idempotent reassignment
     * is intentional — any admin can take over per Wireframe A7.
     */
    @Modifying
    @Query("""
            UPDATE DisputeEntity d
            SET d.status = 'IN_REVIEW', d.assignedToAdminId = :adminId, d.updatedAt = :now
            WHERE d.id = :id AND d.status IN ('OPEN', 'IN_REVIEW')
            """)
    int assignIfAssignable(@Param("id") UUID id, @Param("adminId") UUID adminId,
                            @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE DisputeEntity d
            SET d.status = 'RESOLVED', d.resolutionJson = :resolutionJson,
                d.resolvedAt = :now, d.resolvedByAdminId = :adminId, d.updatedAt = :now
            WHERE d.id = :id AND d.status = 'IN_REVIEW'
            """)
    int resolveIfInReview(@Param("id") UUID id, @Param("resolutionJson") String resolutionJson,
                           @Param("now") Instant now, @Param("adminId") UUID adminId);

    @Modifying
    @Query("""
            UPDATE DisputeEntity d
            SET d.status = 'CLOSED_NO_ACTION', d.resolutionJson = :resolutionJson,
                d.resolvedAt = :now, d.resolvedByAdminId = :adminId, d.updatedAt = :now
            WHERE d.id = :id AND d.status = 'IN_REVIEW'
            """)
    int closeNoActionIfInReview(@Param("id") UUID id, @Param("resolutionJson") String resolutionJson,
                                 @Param("now") Instant now, @Param("adminId") UUID adminId);
}
