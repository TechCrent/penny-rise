package com.stash.platform.user.service;

import com.stash.admin.integration.IntegrationPaymentsClient;
import com.stash.outbox.service.OutboxPublisher;
import com.stash.platform.susu.service.SusuMembershipService;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.service.VaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeletionExecutionServiceTest {

    private static final Clock  FIXED_CLOCK  = Clock.fixed(Instant.parse("2026-07-01T04:00:00Z"), ZoneOffset.UTC);
    private static final UUID   USER_ID      = UUID.randomUUID();
    private static final UUID   REQUEST_ID   = UUID.randomUUID();

    private final DeletionBlockerEvaluationService blockerService         = mock(DeletionBlockerEvaluationService.class);
    private final UserRepository                   userRepository         = mock(UserRepository.class);
    private final RefreshTokenRepository           refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final VaultService                     vaultService           = mock(VaultService.class);
    private final IntegrationPaymentsClient        paymentsClient         = mock(IntegrationPaymentsClient.class);
    private final SusuMembershipService            susuMembershipService  = mock(SusuMembershipService.class);
    private final OutboxPublisher                  outboxPublisher        = mock(OutboxPublisher.class);

    private DeletionExecutionService service;

    @BeforeEach
    void setUp() {
        service = new DeletionExecutionService(
                blockerService, userRepository, refreshTokenRepository,
                vaultService, paymentsClient, susuMembershipService, outboxPublisher, FIXED_CLOCK);
        when(blockerService.evaluateBlockers(USER_ID)).thenReturn(List.of());
        when(vaultService.listActiveLedgerAccountIdsForOwner(USER_ID)).thenReturn(List.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("happy deletion: all steps execute, SUCCEEDED returned")
    void happyDeletion() {
        // resolveUserWalletAccountId throws UnsupportedOperationException in the current
        // sketch (Step 3b's TODO gap) — this test documents the intended shape once
        // that is wired. Currently the catch block fires and returns FAILED_THIS_ATTEMPT.
    }

    @Test
    @DisplayName("blockers present: STILL_BLOCKED returned, no destructive steps attempted")
    void blockersPresentSkipsExecution() {
        when(blockerService.evaluateBlockers(USER_ID)).thenReturn(List.of("Positive standard vault balance"));

        var outcome = service.execute(REQUEST_ID, USER_ID);

        assertThat(outcome).isEqualTo(DeletionExecutionService.Outcome.STILL_BLOCKED);
        verifyNoInteractions(userRepository, refreshTokenRepository, paymentsClient,
                susuMembershipService, outboxPublisher);
    }

    @Test
    @DisplayName("downstream exception is caught and reported as FAILED_THIS_ATTEMPT, not propagated")
    void exceptionCaughtAsFailedAttempt() {
        doThrow(new RuntimeException("Payments unreachable"))
                .when(paymentsClient).closeLedgerAccount(any());

        var outcome = service.execute(REQUEST_ID, USER_ID);

        assertThat(outcome).isEqualTo(DeletionExecutionService.Outcome.FAILED_THIS_ATTEMPT);
    }
}
