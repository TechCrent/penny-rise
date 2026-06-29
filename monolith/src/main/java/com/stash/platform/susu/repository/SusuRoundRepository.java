package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuRoundEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SusuRoundRepository extends JpaRepository<SusuRoundEntity, UUID> {

    /**
     * Finds the round with a pessimistic write lock.
     * Used to safely update round status after all contributions are collected
     * without a concurrent update race.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SusuRoundEntity r WHERE r.id = :id")
    Optional<SusuRoundEntity> findByIdForUpdate(@Param("id") UUID id);
}
