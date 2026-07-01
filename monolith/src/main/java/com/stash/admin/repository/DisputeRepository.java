package com.stash.admin.repository;

import com.stash.admin.domain.DisputeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
