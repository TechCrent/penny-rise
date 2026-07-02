package com.stash.platform.notification.repository;

import com.stash.platform.notification.domain.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEntity n SET n.deliveryStatus = 'DELIVERED' WHERE n.id = :id")
    void markDelivered(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEntity n SET n.deliveryStatus = 'FAILED', n.deliveryAttempts = :attempts WHERE n.id = :id")
    void markFailed(@Param("id") UUID id, @Param("attempts") int attempts);
}
