package com.stash.payments.outbox.repository;

import com.stash.payments.outbox.domain.OutboxDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxDeadLetterRepository extends JpaRepository<OutboxDeadLetter, UUID> {}
