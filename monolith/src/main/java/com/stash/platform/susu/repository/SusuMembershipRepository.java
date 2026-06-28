package com.stash.platform.susu.repository;

import com.stash.platform.susu.domain.SusuMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SusuMembershipRepository extends JpaRepository<SusuMembershipEntity, UUID> {}
