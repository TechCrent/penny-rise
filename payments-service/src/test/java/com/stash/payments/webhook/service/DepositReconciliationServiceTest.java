package com.stash.payments.webhook.service;

import com.stash.payments.paystack.client.PaystackClient;
import com.stash.payments.paystack.dto.TransactionVerifyResponse;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DepositReconciliationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-12T10:00:00Z"), ZoneOffset.UTC);
    private static final String REF = "STSH-202607-TEST01";
    private static final String PAYSTACK_REF = "paystack-ref-1";

    private final TransactionRepository txnRepo = Mockito.mock(TransactionRepository.class);
    private final PaystackClient paystackClient = Mockito.mock(PaystackClient.class);
    private final ChargeSuccessHandler chargeSuccessHandler = Mockito.mock(ChargeSuccessHandler.class);

    private final DepositReconciliationService service = new DepositReconciliationService(
            txnRepo, paystackClient, chargeSuccessHandler, FIXED_CLOCK, 60L);

    @Test
    @DisplayName("findStaleDepositReferences delegates to the repository with a threshold 60s in the past")
    void find_stale_deposit_references_uses_threshold() {
        when(txnRepo.findStalePendingDepositReferences(any())).thenReturn(List.of(REF));

        var result = service.findStaleDepositReferences();

        assertThat(result).containsExactly(REF);
        verify(txnRepo).findStalePendingDepositReferences(Instant.parse("2026-07-12T09:59:00Z"));
    }

    @Test
    @DisplayName("Paystack reports success — completes the deposit via ChargeSuccessHandler")
    void reconcile_completes_on_paystack_success() {
        var txn = pendingTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(paystackClient.verifyTransaction(PAYSTACK_REF)).thenReturn(
                new TransactionVerifyResponse(true, "ok",
                        new TransactionVerifyResponse.TransactionData(
                                PAYSTACK_REF, "success", 5000L, "Approved", "2026-07-12T09:59:00Z")));

        service.reconcileOne(REF);

        verify(chargeSuccessHandler).handle(
                Map.of("reference", PAYSTACK_REF, "amount", 5000L), "corr-1");
    }

    @Test
    @DisplayName("Paystack reports abandoned — marks the deposit FAILED, does not call ChargeSuccessHandler")
    void reconcile_marks_failed_on_paystack_abandoned() {
        var txn = pendingTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(paystackClient.verifyTransaction(PAYSTACK_REF)).thenReturn(
                new TransactionVerifyResponse(true, "ok",
                        new TransactionVerifyResponse.TransactionData(
                                PAYSTACK_REF, "abandoned", 5000L, "Abandoned", null)));

        service.reconcileOne(REF);

        assertThat(txn.getStatus()).isEqualTo("FAILED");
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    @DisplayName("Paystack still reports pending — leaves the transaction untouched for the next run")
    void reconcile_leaves_pending_untouched() {
        var txn = pendingTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(paystackClient.verifyTransaction(PAYSTACK_REF)).thenReturn(
                new TransactionVerifyResponse(true, "ok",
                        new TransactionVerifyResponse.TransactionData(
                                PAYSTACK_REF, "pending", 5000L, "Pending", null)));

        service.reconcileOne(REF);

        assertThat(txn.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    @DisplayName("already-completed transaction (webhook won the race) is a no-op")
    void reconcile_noop_if_already_completed() {
        var txn = pendingTxn();
        txn.markCompleted(UUID.randomUUID(), Instant.now(FIXED_CLOCK));
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));

        service.reconcileOne(REF);

        verifyNoInteractions(paystackClient);
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    @DisplayName("Paystack call throws — leaves the transaction untouched, retried next run")
    void reconcile_leaves_untouched_on_paystack_error() {
        var txn = pendingTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(paystackClient.verifyTransaction(PAYSTACK_REF)).thenThrow(new RuntimeException("timeout"));

        service.reconcileOne(REF);

        assertThat(txn.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    @DisplayName("unknown reference is a no-op, not an error")
    void reconcile_noop_if_reference_not_found() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.empty());

        service.reconcileOne(REF);

        verifyNoInteractions(paystackClient);
        verifyNoInteractions(chargeSuccessHandler);
    }

    private TransactionEntity pendingTxn() {
        var txn = TransactionEntity.pendingDeposit(
                REF, UUID.randomUUID(), 5000L, UUID.randomUUID(),
                "corr-1", "idem-1", Instant.now(FIXED_CLOCK));
        txn.setExternalReference(PAYSTACK_REF);
        return txn;
    }
}
