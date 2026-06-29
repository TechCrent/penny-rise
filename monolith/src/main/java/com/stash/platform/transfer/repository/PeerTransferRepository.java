package com.stash.platform.transfer.repository;

import com.stash.platform.transfer.domain.PeerTransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PeerTransferRepository extends JpaRepository<PeerTransferEntity, UUID> {

    Optional<PeerTransferEntity> findByIdempotencyKey(String idempotencyKey);
}
