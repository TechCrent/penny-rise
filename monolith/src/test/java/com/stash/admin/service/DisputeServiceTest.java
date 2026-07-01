package com.stash.admin.service;

import com.stash.admin.api.dto.CreateDisputeRequest;
import com.stash.admin.dispute.*;
import com.stash.admin.event.DisputeRaisedEvent;
import com.stash.admin.repository.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisputeServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

    private final DisputeRepository      disputeRepository = mock(DisputeRepository.class);
    private final ApplicationEventPublisher eventPublisher  = mock(ApplicationEventPublisher.class);

    // Real registry with mock validators — dispatch logic exercised for real.
    private final DisputeEntityValidator transactionValidator = mock(DisputeEntityValidator.class);
    private final DisputeEntityValidator susuValidator        = mock(DisputeEntityValidator.class);
    private final DisputeEntityValidator transferValidator    = mock(DisputeEntityValidator.class);
    private final DisputeEntityValidator accountValidator     = mock(DisputeEntityValidator.class);

    private DisputeService service;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(transactionValidator.supportedType()).thenReturn(RelatedEntityType.TRANSACTION);
        when(susuValidator.supportedType()).thenReturn(RelatedEntityType.SUSU_GROUP);
        when(transferValidator.supportedType()).thenReturn(RelatedEntityType.TRANSFER);
        when(accountValidator.supportedType()).thenReturn(RelatedEntityType.ACCOUNT);

        var registry = new DisputeEntityValidatorRegistry(
                List.of(transactionValidator, susuValidator, transferValidator, accountValidator));

        service = new DisputeService(disputeRepository, registry, eventPublisher, FIXED_CLOCK);

        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── happy path, every dispute_type ─────────────────────────────────

    static Stream<Arguments> disputeTypeAndExpectedEntityType() {
        return Stream.of(
                Arguments.of("TRANSACTION", "TRANSACTION"),
                Arguments.of("SUSU",        "SUSU_GROUP"),
                Arguments.of("TRANSFER",    "TRANSFER"),
                Arguments.of("ACCOUNT",     "ACCOUNT"),
                Arguments.of("OTHER",       "ACCOUNT")
        );
    }

    @ParameterizedTest(name = "happy path: dispute_type={0}")
    @MethodSource("disputeTypeAndExpectedEntityType")
    @DisplayName("each dispute_type creates an OPEN/NORMAL dispute and emits the event")
    void happyPathForEachDisputeType(String disputeType, String expectedRelatedEntityType) {
        var request = new CreateDisputeRequest(disputeType, expectedRelatedEntityType, ENTITY_ID,
                "Something went wrong", "Full description of what happened.");
        when(disputeRepository.existsActiveForEntity(eq(USER_ID), eq(expectedRelatedEntityType), eq(ENTITY_ID)))
                .thenReturn(false);

        var response = service.raiseDispute(USER_ID, request);

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.priority()).isEqualTo("NORMAL");
        verify(eventPublisher).publishEvent((Object) any(DisputeRaisedEvent.class));
    }

    // ── type/entity mismatch ────────────────────────────────────────────

    @Test
    @DisplayName("dispute_type/related_entity_type mismatch returns 400 DISPUTE_TYPE_ENTITY_MISMATCH")
    void typeEntityMismatch() {
        var request = new CreateDisputeRequest("TRANSACTION", "SUSU_GROUP", ENTITY_ID, "x", "y");

        assertThatThrownBy(() -> service.raiseDispute(USER_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("DISPUTE_TYPE_ENTITY_MISMATCH");
                });
    }

    // ── duplicate active dispute ────────────────────────────────────────

    @Test
    @DisplayName("pre-check returning true returns 409 DISPUTE_ALREADY_ACTIVE")
    void duplicateActiveConflict() {
        var request = new CreateDisputeRequest("TRANSFER", "TRANSFER", ENTITY_ID, "x", "y");
        when(disputeRepository.existsActiveForEntity(USER_ID, "TRANSFER", ENTITY_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.raiseDispute(USER_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("DISPUTE_ALREADY_ACTIVE");
                });

        verify(disputeRepository, never()).save(any());
    }

    @Test
    @DisplayName("DataIntegrityViolationException from save maps to 409 DISPUTE_ALREADY_ACTIVE (race guard)")
    void dataIntegrityViolationMapsToConflict() {
        var request = new CreateDisputeRequest("ACCOUNT", "ACCOUNT", USER_ID, "x", "y");
        when(disputeRepository.existsActiveForEntity(USER_ID, "ACCOUNT", USER_ID)).thenReturn(false);
        when(disputeRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_violation"));

        assertThatThrownBy(() -> service.raiseDispute(USER_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── bad enum values ─────────────────────────────────────────────────

    @Test
    @DisplayName("unrecognised dispute_type returns 400 DISPUTE_TYPE_INVALID")
    void invalidDisputeTypeReturns400() {
        var request = new CreateDisputeRequest("REFUND", "ACCOUNT", ENTITY_ID, "x", "y");

        assertThatThrownBy(() -> service.raiseDispute(USER_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("DISPUTE_TYPE_INVALID");
                });
    }

    @Test
    @DisplayName("unrecognised related_entity_type returns 400 DISPUTE_RELATED_ENTITY_TYPE_INVALID")
    void invalidRelatedEntityTypeReturns400() {
        var request = new CreateDisputeRequest("ACCOUNT", "SAVINGS_ACCOUNT", ENTITY_ID, "x", "y");

        assertThatThrownBy(() -> service.raiseDispute(USER_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("DISPUTE_RELATED_ENTITY_TYPE_INVALID");
                });
    }

    // ── validator error propagation ─────────────────────────────────────

    @Test
    @DisplayName("404 from entity validator propagates out of raiseDispute")
    void validatorNotFoundPropagates() {
        var request = new CreateDisputeRequest("TRANSFER", "TRANSFER", ENTITY_ID, "x", "y");
        when(disputeRepository.existsActiveForEntity(any(), any(), any())).thenReturn(false);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "DISPUTE_ENTITY_NOT_FOUND"))
                .when(transferValidator).validateOwnership(ENTITY_ID, USER_ID);

        assertThatThrownBy(() -> service.raiseDispute(USER_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(disputeRepository, never()).save(any());
    }
}
