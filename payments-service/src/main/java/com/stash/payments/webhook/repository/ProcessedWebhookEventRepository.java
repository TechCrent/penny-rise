package com.stash.payments.webhook.repository;

import com.stash.payments.webhook.domain.ProcessedWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessedWebhookEventRepository
        extends JpaRepository<ProcessedWebhookEventEntity, UUID> {

    Optional<ProcessedWebhookEventEntity> findByProviderAndEventId(
            String provider, String eventId);
}
