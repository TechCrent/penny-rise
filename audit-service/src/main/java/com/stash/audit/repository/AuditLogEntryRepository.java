package com.stash.audit.repository;

import com.stash.audit.domain.AuditLogEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntryEntity, UUID> {
    boolean existsByEventId(String eventId);
}
