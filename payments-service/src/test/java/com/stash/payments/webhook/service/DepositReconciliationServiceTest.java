package com.stash.payments.webhook.service;

import com.stash.payments.moolre.client.MoolreClient;
import com.stash.payments.moolre.dto.StatusResult;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DepositReconciliationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-12T10:00:00Z"), ZoneOffset.UTC);
    private static final String REF = "STSH-202607-TEST01";

    private final TransactionRepository txnRepo = Mockito.mock(TransactionRepository.class);
    private final MoolreClient moolreClient = Mockito.mock(MoolreClient.class);
    private final ChargeSuccessHandler chargeSuccessHandler = Mockito.mock(ChargeSuccessHandler.class);

    private final DepositReconciliationService service = new DepositReconciliationService(
            txnRepo, moolreClient, chargeSuccessHandler, FIXED_CLOCK, 60L);

    @Test
    @DisplayName("findStaleDepositReferences delegates to the repository with a threshold 60s in the past")
    void find_stale_deposit_references_uses_threshold() {
        when(txnRepo.findStalePendingDepositReferences(any())).thenReturn(List.of(REF));

        var result = service.findStaleDepositReferences();

        assertThat(result).containsExactly(REF);
        verify(txnRepo).findStalePendingDepositReferences(Instant.parse("2026-07-12T09:59:00Z"));
    }

    @Test
    @DisplayName("Moolre reports txstatus=1 — completes the deposit via ChargeSuccessHandler")
    void reconcile_completes_on_moolre_success() {
        var txn = pendingTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(moolreClient.queryStatus(REF, true)).thenReturn(
                new StatusResult("SS01", "ok", true, 1, 2, "50.00", "50",
                        "31772290", REF, null, null, null, null));

        service.reconcileOne(REF);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(chargeSuccessHandler).handle(captor.capture(), org.mockito.ArgumentMatchers.eq("corr-1"));
        assertThat(captor.getValue().get("externalref")).isEqualTo(REF);
        assertThat(captor.getValue().get("amount")).isEqualTo("50.00");
    }

    @Test
    @DisplayName("Moolre reports txstatus=2 — marks the deposit FAILED")
    void reconcile_marks_failed_on_moolre_failed() {
        var txn = pendingTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(moolreClient.queryStatus(REF, true)).thenReturn(
                new StatusResult("SS02", "failed", true, 2, 2, "50.00", "50",
                        null, REF, null, null, null, null));

        service.reconcileOne(REF);

        assertThat(txn.getStatus()).isEqualTo("FAILED");
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    @DisplayName("Moolre still reports pending — leaves the transaction untouched")
    void reconcile_leaves_pending_untouched() {
        var txn = pendingTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(moolreClient.queryStatus(REF, true)).thenReturn(
                new StatusResult("SS00", "pending", true, 0, 2, "50.00", "50",
                        null, REF, null, null, null, null));

        service.reconcileOne(REF);

        assertThat(txn.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    @DisplayName("already-completed transaction is a no-op")
    void reconcile_noop_if_already_completed() {
        var txn = pendingTxn();
        txn.markCompleted(UUID.randomUUID(), Instant.now(FIXED_CLOCK));
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));

        service.reconcileOne(REF);

        verify(moolreClient, never()).queryStatus(any(), any(Boolean.class));
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    @DisplayName("Moolre call throws — leaves the transaction untouched")
    void reconcile_leaves_untouched_on_moolre_error() {
        var txn = pendingTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(moolreClient.queryStatus(REF, true)).thenThrow(new RuntimeException("timeout"));

        service.reconcileOne(REF);

        assertThat(txn.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    @DisplayName("unknown reference is a no-op")
    void reconcile_noop_if_reference_not_found() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.empty());

        service.reconcileOne(REF);

        verifyNoInteractions(moolreClient);
        verifyNoInteractions(chargeSuccessHandler);
    }

    private TransactionEntity pendingTxn() {
        var txn = TransactionEntity.pendingDeposit(
                REF, UUID.randomUUID(), 5000L, UUID.randomUUID(),
                "corr-1", "idem-1", Instant.now(FIXED_CLOCK));
        txn.setExternalReference("session-1");
        return txn;
    }
}
