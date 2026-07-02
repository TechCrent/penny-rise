package com.stash.challenge.repository;

import com.stash.challenge.domain.UserBadgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserBadgeRepository extends JpaRepository<UserBadgeEntity, UUID> {
}
