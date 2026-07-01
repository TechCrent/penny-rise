package com.stash.admin.service;

import com.stash.admin.api.dto.CreateDisputeRequest;
import com.stash.admin.api.dto.CreateDisputeResponse;
import com.stash.admin.domain.DisputeEntity;
import com.stash.admin.dispute.DisputeEntityValidatorRegistry;
import com.stash.admin.dispute.DisputeType;
import com.stash.admin.dispute.RelatedEntityType;
import com.stash.admin.event.DisputeRaisedEvent;
import com.stash.admin.repository.DisputeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class DisputeService {

    /**
     * Maps each dispute_type to the exact related_entity_type it must be paired with.
     * OTHER maps to ACCOUNT — no external entity to validate; the user disputes
     * something about their own account.
     */
    private static final Map<DisputeType, RelatedEntityType> EXPECTED_RELATED_ENTITY_TYPE = Map.of(
            DisputeType.TRANSACTION, RelatedEntityType.TRANSACTION,
            DisputeType.SUSU,        RelatedEntityType.SUSU_GROUP,
            DisputeType.TRANSFER,    RelatedEntityType.TRANSFER,
            DisputeType.ACCOUNT,     RelatedEntityType.ACCOUNT,
            DisputeType.OTHER,       RelatedEntityType.ACCOUNT
    );

    private final DisputeRepository             disputeRepository;
    private final DisputeEntityValidatorRegistry validatorRegistry;
    private final ApplicationEventPublisher      eventPublisher;
    private final Clock                          clock;

    public DisputeService(DisputeRepository disputeRepository,
                           DisputeEntityValidatorRegistry validatorRegistry,
                           ApplicationEventPublisher eventPublisher,
                           Clock clock) {
        this.disputeRepository = disputeRepository;
        this.validatorRegistry = validatorRegistry;
        this.eventPublisher    = eventPublisher;
        this.clock             = clock;
    }

    @Transactional
    public CreateDisputeResponse raiseDispute(UUID userId, CreateDisputeRequest request) {
        DisputeType disputeType = parseEnum(DisputeType.class, request.disputeType(),
                "DISPUTE_TYPE_INVALID: Unrecognised dispute_type " + request.disputeType());
        RelatedEntityType relatedEntityType = parseEnum(RelatedEntityType.class, request.relatedEntityType(),
                "DISPUTE_RELATED_ENTITY_TYPE_INVALID: Unrecognised related_entity_type " + request.relatedEntityType());

        RelatedEntityType expected = EXPECTED_RELATED_ENTITY_TYPE.get(disputeType);
        if (expected != relatedEntityType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "DISPUTE_TYPE_ENTITY_MISMATCH: dispute_type=" + disputeType +
                    " requires related_entity_type=" + expected + ", got " + relatedEntityType);
        }

        validatorRegistry.get(relatedEntityType).validateOwnership(request.relatedEntityId(), userId);

        // Fast pre-check for a friendly error — the unique index (V25) is the actual race-proof guarantee.
        if (disputeRepository.existsActiveForEntity(userId, relatedEntityType.name(), request.relatedEntityId())) {
            throw activeConflict();
        }

        Instant now = Instant.now(clock);
        DisputeEntity dispute = DisputeEntity.create(
                userId, disputeType.name(), relatedEntityType.name(),
                request.relatedEntityId(), request.subject(), request.description(), now);

        try {
            dispute = disputeRepository.save(dispute);
        } catch (DataIntegrityViolationException e) {
            throw activeConflict();
        }

        eventPublisher.publishEvent(new DisputeRaisedEvent(
                dispute.getId(), userId, disputeType.name(), relatedEntityType.name(),
                request.relatedEntityId(), now));

        return new CreateDisputeResponse(dispute.getId(), dispute.getStatus(), dispute.getPriority());
    }

    private static ResponseStatusException activeConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "DISPUTE_ALREADY_ACTIVE: An open or in-review dispute already exists for this entity.");
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String errorMessage) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }
}
