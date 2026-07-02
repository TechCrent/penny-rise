package com.stash.challenge.repository;

import com.stash.challenge.domain.SavingsChallengeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SavingsChallengeRepository extends JpaRepository<SavingsChallengeEntity, UUID> {
}
