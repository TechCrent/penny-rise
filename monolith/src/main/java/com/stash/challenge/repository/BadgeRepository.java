package com.stash.challenge.repository;

import com.stash.challenge.domain.BadgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BadgeRepository extends JpaRepository<BadgeEntity, UUID> {
}
