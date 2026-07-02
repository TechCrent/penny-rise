package com.stash.platform.notification.repository;

import com.stash.platform.notification.domain.DeviceTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceTokenEntity, UUID> {

    @Query("SELECT t FROM DeviceTokenEntity t WHERE t.userId = :userId AND t.isActive = true")
    List<DeviceTokenEntity> findActiveForUser(@Param("userId") UUID userId);

    Optional<DeviceTokenEntity> findByExpoPushToken(String expoPushToken);

    /** LRU ordering — least recently used first; used by the 5-token eviction logic. */
    @Query("SELECT t FROM DeviceTokenEntity t WHERE t.userId = :userId AND t.isActive = true ORDER BY t.lastUsedAt ASC")
    List<DeviceTokenEntity> findActiveOrderedByLastUsedAsc(@Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE DeviceTokenEntity t SET t.isActive = false WHERE t.id = :id")
    void deactivate(@Param("id") UUID id);
}
