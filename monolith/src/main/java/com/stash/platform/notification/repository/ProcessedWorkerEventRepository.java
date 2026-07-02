package com.stash.platform.notification.repository;

import com.stash.platform.notification.domain.ProcessedWorkerEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedWorkerEventRepository extends JpaRepository<ProcessedWorkerEventEntity, UUID> {

    boolean existsByEventId(String eventId);
}
