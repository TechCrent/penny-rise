package com.stash.platform.user.repository;

import com.stash.platform.user.domain.DeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, UUID> {

    @Query("""
            SELECT d FROM DeletionRequest d
            WHERE d.userId = :userId AND d.status = 'PENDING'
            """)
    Optional<DeletionRequest> findPendingByUserId(@Param("userId") UUID userId);
}
