package com.stash.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.admin.api.dto.AdminDisputeListItem;
import com.stash.admin.api.dto.AdminDisputeListResponse;
import com.stash.admin.domain.AdminAuditActionEntity;
import com.stash.admin.domain.DisputeEntity;
import com.stash.admin.event.DisputeClosedEvent;
import com.stash.admin.event.DisputeResolvedEvent;
import com.stash.admin.repository.AdminAuditActionRepository;
import com.stash.admin.repository.DisputeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.stash.admin.rbac.AdminAuditActionType.*;

@Service
public class AdminDisputeService {

    private final DisputeRepository          disputeRepository;
    private final AdminAuditActionRepository auditRepo;
    private final ApplicationEventPublisher  eventPublisher;
    private final ObjectMapper               objectMapper;
    private final Clock                      clock;

    public AdminDisputeService(DisputeRepository disputeRepository,
                                AdminAuditActionRepository auditRepo,
                                ApplicationEventPublisher eventPublisher,
                                ObjectMapper objectMapper,
                                Clock clock) {
        this.disputeRepository = disputeRepository;
        this.auditRepo         = auditRepo;
        this.eventPublisher    = eventPublisher;
        this.objectMapper      = objectMapper;
        this.clock             = clock;
    }

    public AdminDisputeListResponse listQueue(String status, Pageable pageable) {
        Page<DisputeEntity> page = disputeRepository.findQueuePaged(status, pageable);
        var items = page.getContent().stream().map(this::toListItem).toList();
        return new AdminDisputeListResponse(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Assign (or reassign) a dispute to this admin. Transitions OPEN → IN_REVIEW;
     * reassignment within IN_REVIEW is also permitted per Wireframe A7.
     * Deliberately does NOT emit an event — no event name is specified for assignment,
     * and inventing one risks an unintended downstream consumer contract.
     */
    @Transactional
    public void assign(UUID disputeId, UUID adminAccountId) {
        Instant now = Instant.now(clock);
        int updated = disputeRepository.assignIfAssignable(disputeId, adminAccountId, now);
        if (updated == 0) {
            throw notFoundOrTerminalConflict(disputeId);
        }

        auditRepo.save(AdminAuditActionEntity.create(
                adminAccountId, DISPUTE_ASSIGNED, "DISPUTE", disputeId, null, null, now));
    }

    @Transactional
    public void resolve(UUID disputeId, UUID adminAccountId, JsonNode resolution) {
        Instant now = Instant.now(clock);
        String resolutionJson = writeJson(resolution);

        int updated = disputeRepository.resolveIfInReview(disputeId, resolutionJson, now, adminAccountId);
        if (updated == 0) {
            throw notInReviewConflict(disputeId);
        }

        DisputeEntity dispute = mustExist(disputeId);

        auditRepo.save(AdminAuditActionEntity.create(
                adminAccountId, DISPUTE_RESOLVED, "DISPUTE", disputeId, resolutionJson, null, now));

        eventPublisher.publishEvent(new DisputeResolvedEvent(
                disputeId, dispute.getRaisedByUserId(), adminAccountId, resolutionJson, now));
    }

    @Transactional
    public void closeNoAction(UUID disputeId, UUID adminAccountId, String reason) {
        Instant now = Instant.now(clock);
        String resolutionJson = writeJson(objectMapper.createObjectNode()
                .put("outcome", "NO_ACTION")
                .put("reason", reason));

        int updated = disputeRepository.closeNoActionIfInReview(disputeId, resolutionJson, now, adminAccountId);
        if (updated == 0) {
            throw notInReviewConflict(disputeId);
        }

        DisputeEntity dispute = mustExist(disputeId);

        auditRepo.save(AdminAuditActionEntity.create(
                adminAccountId, DISPUTE_CLOSED_NO_ACTION, "DISPUTE", disputeId, resolutionJson, null, now));

        eventPublisher.publishEvent(new DisputeClosedEvent(
                disputeId, dispute.getRaisedByUserId(), adminAccountId, reason, now));
    }

    // ── private helpers ────────────────────────────────────────────────────

    private AdminDisputeListItem toListItem(DisputeEntity d) {
        return new AdminDisputeListItem(
                d.getId(), d.getRaisedByUserId(), d.getDisputeType(), d.getRelatedEntityType(),
                d.getRelatedEntityId(), d.getSubject(), d.getStatus(), d.getPriority(),
                d.getAssignedToAdminId(), d.getCreatedAt());
    }

    private DisputeEntity mustExist(UUID disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND: No dispute with id " + disputeId));
    }

    private ResponseStatusException notFoundOrTerminalConflict(UUID disputeId) {
        return disputeRepository.findById(disputeId)
                .map(d -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "DISPUTE_TERMINAL: Cannot assign a dispute in terminal status " + d.getStatus()))
                .orElseGet(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND: No dispute with id " + disputeId));
    }

    private ResponseStatusException notInReviewConflict(UUID disputeId) {
        return disputeRepository.findById(disputeId)
                .map(d -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "DISPUTE_NOT_IN_REVIEW: Dispute is in status " + d.getStatus() + ", not IN_REVIEW"))
                .orElseGet(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_NOT_FOUND: No dispute with id " + disputeId));
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize resolution to JSON", e);
        }
    }
}
