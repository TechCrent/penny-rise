package com.stash.admin.repository;

import com.stash.admin.domain.AdminAuditActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Intentionally exposes only {@code save} (via JpaRepository's inherited
 * insert path) and read queries. There is no update or delete method
 * anywhere in this interface — matching the append-only nature of the
 * underlying table.
 */
public interface AdminAuditActionRepository extends JpaRepository<AdminAuditActionEntity, UUID> {

    @Query("""
            SELECT a FROM AdminAuditActionEntity a
            WHERE a.adminAccountId = :adminId
            ORDER BY a.createdAt DESC
            """)
    List<AdminAuditActionEntity> findRecentByAdmin(@Param("adminId") UUID adminId);

    @Query("""
            SELECT a FROM AdminAuditActionEntity a
            WHERE a.targetType = :targetType
              AND a.targetId   = :targetId
            ORDER BY a.createdAt DESC
            """)
    List<AdminAuditActionEntity> findByTarget(
            @Param("targetType") String targetType,
            @Param("targetId")   UUID   targetId);
}
