package com.stash.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.admin.domain.DisputeEntity;
import com.stash.admin.event.DisputeClosedEvent;
import com.stash.admin.event.DisputeResolvedEvent;
import com.stash.admin.repository.AdminAuditActionRepository;
import com.stash.admin.repository.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.stash.admin.rbac.AdminAuditActionType.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminDisputeServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"), ZoneOffset.UTC);

    private final DisputeRepository          disputeRepository = mock(DisputeRepository.class);
    private final AdminAuditActionRepository auditRepo         = mock(AdminAuditActionRepository.class);
    private final ApplicationEventPublisher  eventPublisher    = mock(ApplicationEventPublisher.class);
    private final ObjectMapper               objectMapper      = new ObjectMapper();

    private final AdminDisputeService service = new AdminDisputeService(
            disputeRepository, auditRepo, eventPublisher, objectMapper, FIXED_CLOCK);

    private static final UUID DISPUTE_ID      = UUID.randomUUID();
    private static final UUID ADMIN_ID        = UUID.randomUUID();
    private static final UUID RAISING_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(disputeRepository.findById(DISPUTE_ID)).thenReturn(Optional.of(sampleDispute()));
    }

    // ── full lifecycle: OPEN -> IN_REVIEW -> RESOLVED ───────────────────

    @Test
    @DisplayName("full lifecycle: assign then resolve succeeds end to end")
    void fullLifecycleResolve() {
        when(disputeRepository.assignIfAssignable(eq(DISPUTE_ID), eq(ADMIN_ID), any())).thenReturn(1);
        when(disputeRepository.resolveIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID))).thenReturn(1);

        service.assign(DISPUTE_ID, ADMIN_ID);
        service.resolve(DISPUTE_ID, ADMIN_ID, objectMapper.createObjectNode().put("refund_reference", "RFD-001"));

        verify(disputeRepository).assignIfAssignable(eq(DISPUTE_ID), eq(ADMIN_ID), any());
        verify(disputeRepository).resolveIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID));
        verify(eventPublisher).publishEvent((Object) any(DisputeResolvedEvent.class));
    }

    @Test
    @DisplayName("resolve writes an admin_audit_actions row with action_type=DISPUTE_RESOLVED")
    void resolveWritesAudit() {
        when(disputeRepository.resolveIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID))).thenReturn(1);

        service.resolve(DISPUTE_ID, ADMIN_ID, objectMapper.createObjectNode());

        verify(auditRepo).save(argThat(a ->
                DISPUTE_RESOLVED.equals(a.getActionType()) && DISPUTE_ID.equals(a.getTargetId())));
    }

    @Test
    @DisplayName("resolve emits a DisputeResolvedEvent with the raising user id")
    void resolveEmitsEvent() {
        when(disputeRepository.resolveIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID))).thenReturn(1);

        service.resolve(DISPUTE_ID, ADMIN_ID, objectMapper.createObjectNode());

        verify(eventPublisher).publishEvent((Object) argThat(e ->
                e instanceof DisputeResolvedEvent evt
                        && evt.disputeId().equals(DISPUTE_ID)
                        && evt.raisedByUserId().equals(RAISING_USER_ID)
                        && evt.resolvedByAdminId().equals(ADMIN_ID)));
    }

    // ── full lifecycle: OPEN -> IN_REVIEW -> CLOSED_NO_ACTION ───────────

    @Test
    @DisplayName("full lifecycle: assign then close-no-action succeeds end to end")
    void fullLifecycleCloseNoAction() {
        when(disputeRepository.assignIfAssignable(eq(DISPUTE_ID), eq(ADMIN_ID), any())).thenReturn(1);
        when(disputeRepository.closeNoActionIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID)))
                .thenReturn(1);

        service.assign(DISPUTE_ID, ADMIN_ID);
        service.closeNoAction(DISPUTE_ID, ADMIN_ID, "Investigated — transaction was correctly charged.");

        verify(eventPublisher).publishEvent((Object) any(DisputeClosedEvent.class));
    }

    @Test
    @DisplayName("close-no-action writes an admin_audit_actions row with action_type=DISPUTE_CLOSED_NO_ACTION")
    void closeNoActionWritesAudit() {
        when(disputeRepository.closeNoActionIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID)))
                .thenReturn(1);

        service.closeNoAction(DISPUTE_ID, ADMIN_ID, "No evidence of error.");

        verify(auditRepo).save(argThat(a ->
                DISPUTE_CLOSED_NO_ACTION.equals(a.getActionType()) && DISPUTE_ID.equals(a.getTargetId())));
    }

    @Test
    @DisplayName("close-no-action resolution payload contains outcome=NO_ACTION and the reason text")
    void closeNoActionPayloadShape() {
        when(disputeRepository.closeNoActionIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID)))
                .thenReturn(1);

        service.closeNoAction(DISPUTE_ID, ADMIN_ID, "Reason text");

        verify(disputeRepository).closeNoActionIfInReview(eq(DISPUTE_ID),
                argThat(json -> json.contains("\"outcome\":\"NO_ACTION\"") && json.contains("\"reason\":\"Reason text\"")),
                any(), eq(ADMIN_ID));
    }

    @Test
    @DisplayName("close-no-action emits a DisputeClosedEvent with the reason")
    void closeNoActionEmitsEvent() {
        when(disputeRepository.closeNoActionIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID)))
                .thenReturn(1);

        service.closeNoAction(DISPUTE_ID, ADMIN_ID, "No evidence of error.");

        verify(eventPublisher).publishEvent((Object) argThat(e ->
                e instanceof DisputeClosedEvent evt
                        && evt.disputeId().equals(DISPUTE_ID)
                        && evt.raisedByUserId().equals(RAISING_USER_ID)
                        && evt.reason().equals("No evidence of error.")));
    }

    // ── assign: 409 from OPEN ─────────────────────────────────────────────

    @Test
    @DisplayName("assign when dispute is already RESOLVED returns 409 DISPUTE_TERMINAL")
    void assignTerminalDisputeReturns409() {
        DisputeEntity resolved = sampleDisputeWithStatus("RESOLVED");
        when(disputeRepository.assignIfAssignable(eq(DISPUTE_ID), eq(ADMIN_ID), any())).thenReturn(0);
        when(disputeRepository.findById(DISPUTE_ID)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> service.assign(DISPUTE_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("DISPUTE_TERMINAL");
                });

        verifyNoInteractions(auditRepo, eventPublisher);
    }

    @Test
    @DisplayName("assign when dispute does not exist returns 404 DISPUTE_NOT_FOUND")
    void assignNotFoundReturns404() {
        when(disputeRepository.assignIfAssignable(eq(DISPUTE_ID), eq(ADMIN_ID), any())).thenReturn(0);
        when(disputeRepository.findById(DISPUTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(DISPUTE_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── resolve: 409 from non-IN_REVIEW status ────────────────────────────

    @Test
    @DisplayName("resolve when dispute is OPEN (not yet assigned) returns 409 DISPUTE_NOT_IN_REVIEW")
    void resolveFromOpenReturns409() {
        DisputeEntity open = sampleDisputeWithStatus("OPEN");
        when(disputeRepository.resolveIfInReview(eq(DISPUTE_ID), anyString(), any(), eq(ADMIN_ID))).thenReturn(0);
        when(disputeRepository.findById(DISPUTE_ID)).thenReturn(Optional.of(open));

        assertThatThrownBy(() ->
                service.resolve(DISPUTE_ID, ADMIN_ID, objectMapper.createObjectNode()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("DISPUTE_NOT_IN_REVIEW");
                });
    }

    // ── assign: writes audit ──────────────────────────────────────────────

    @Test
    @DisplayName("assign writes an admin_audit_actions row with action_type=DISPUTE_ASSIGNED")
    void assignWritesAudit() {
        when(disputeRepository.assignIfAssignable(eq(DISPUTE_ID), eq(ADMIN_ID), any())).thenReturn(1);

        service.assign(DISPUTE_ID, ADMIN_ID);

        verify(auditRepo).save(argThat(a ->
                DISPUTE_ASSIGNED.equals(a.getActionType()) && DISPUTE_ID.equals(a.getTargetId())));
    }

    @Test
    @DisplayName("assign does not emit any event (no event name specified in AC)")
    void assignDoesNotEmitEvent() {
        when(disputeRepository.assignIfAssignable(eq(DISPUTE_ID), eq(ADMIN_ID), any())).thenReturn(1);

        service.assign(DISPUTE_ID, ADMIN_ID);

        verifyNoInteractions(eventPublisher);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private DisputeEntity sampleDispute() {
        return DisputeEntity.create(RAISING_USER_ID, "TRANSFER", "TRANSFER",
                UUID.randomUUID(), "Duplicate charge", "I was charged twice.", Instant.now(FIXED_CLOCK));
    }

    private DisputeEntity sampleDisputeWithStatus(String status) {
        DisputeEntity d = sampleDispute();
        Instant now = Instant.now(FIXED_CLOCK);
        return switch (status) {
            case "OPEN" -> d;
            case "IN_REVIEW" -> { d.assignTo(UUID.randomUUID(), now); yield d; }
            case "RESOLVED" -> { d.assignTo(UUID.randomUUID(), now); d.resolve("{}", UUID.randomUUID(), now); yield d; }
            case "CLOSED_NO_ACTION" -> { d.assignTo(UUID.randomUUID(), now); d.closeNoAction("{}", UUID.randomUUID(), now); yield d; }
            default -> throw new IllegalArgumentException("Unknown status: " + status);
        };
    }
}
