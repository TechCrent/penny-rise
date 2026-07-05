package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.exception.InsufficientBalanceException;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.BalanceService;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.ledger.service.LedgerWriteCommand;
import com.stash.payments.ledger.service.LedgerWriteResult;
import com.stash.payments.ledger.service.TransactionReferenceGenerator;
import com.stash.payments.outbox.service.OutboxPublisher;
import com.stash.payments.paystack.client.PaystackClient;
import com.stash.payments.paystack.dto.TransferInitiateResponse;
import com.stash.payments.paystack.dto.TransferRecipientCreateResponse;
import com.stash.payments.paystack.exception.PaystackClientException;
import com.stash.payments.transaction.api.dto.WithdrawalInitiateRequest;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.event.WithdrawalCompletedEvent;
import com.stash.payments.transaction.event.WithdrawalFailedEvent;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class WithdrawalServiceTest {

    private static final Clock  FIXED_CLOCK    =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);
    private static final String SETTLEMENT_ID  = "00000000-0000-0000-0000-000000000001";

    private final LedgerAccountRepository       accountRepo    = Mockito.mock(LedgerAccountRepository.class);
    private final BalanceService                balanceService = Mockito.mock(BalanceService.class);
    private final LedgerService                 ledgerService  = Mockito.mock(LedgerService.class);
    private final TransactionRepository         txnRepo        = Mockito.mock(TransactionRepository.class);
    private final PaystackClient                paystack       = Mockito.mock(PaystackClient.class);
    private final OutboxPublisher               outbox         = Mockito.mock(OutboxPublisher.class);
    private final TransactionReferenceGenerator refGen         = Mockito.mock(TransactionReferenceGenerator.class);

    private final WithdrawalService service = new WithdrawalService(
            accountRepo, balanceService, ledgerService, txnRepo,
            paystack, outbox, refGen, FIXED_CLOCK, SETTLEMENT_ID, false);

    // Separate instance with the sandbox-simulation flag on, for the two tests below.
    private final WithdrawalService sandboxSimulatingService = new WithdrawalService(
            accountRepo, balanceService, ledgerService, txnRepo,
            paystack, outbox, refGen, FIXED_CLOCK, SETTLEMENT_ID, true);

    private static final String SANDBOX_STARTER_BUSINESS_MESSAGE =
            "{\"status\":false,\"message\":\"You cannot initiate third party payouts as a "
            + "starter business\",\"type\":\"api_error\"}";

    private static final UUID   USER_ID    = UUID.randomUUID();
    private static final UUID   ACCOUNT_ID = UUID.randomUUID();
    private static final String REF        = "STSH-202606-WD0001";
    private static final String IDEM_KEY   = "idem-wd-001";

    @BeforeEach
    void setUp() {
        when(refGen.generate()).thenReturn(REF);
        when(txnRepo.findByReference(REF)).thenReturn(Optional.empty());
        when(txnRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerService.writeTransaction(any())).thenReturn(
                new LedgerWriteResult(UUID.randomUUID(), REF, 10_000L));
        when(outbox.publish(any(), any())).thenReturn(null);
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("sufficient balance: reserve, call Paystack, return 202 PENDING")
    void happy_path_returns_202_pending() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubRecipientSuccess();
        stubTransferSuccess();

        var result = service.initiateWithdrawal(request(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.transactionReference()).isEqualTo(REF);
        assertThat(result.paystackTransferCode()).isEqualTo("TRF_test001");
    }

    @Test
    @DisplayName("ledger reservation entries posted before Paystack call")
    void reservation_posted_before_paystack_call() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubRecipientSuccess();
        stubTransferSuccess();

        service.initiateWithdrawal(request(10_000L), IDEM_KEY);

        InOrder order = Mockito.inOrder(ledgerService, paystack);
        order.verify(ledgerService).writeTransaction(any());
        order.verify(paystack).createTransferRecipient(any());
        order.verify(paystack).initiateTransfer(any());
    }

    // ── Insufficient balance ──────────────────────────────────────────────

    @Test
    @DisplayName("insufficient balance throws InsufficientBalanceException (controller → 422)")
    void insufficient_balance_throws_422() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(5_000L);

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(InsufficientBalanceException.class)
                .satisfies(ex -> {
                    var e = (InsufficientBalanceException) ex;
                    assertThat(e.getAvailable()).isEqualTo(5_000L);
                    assertThat(e.getRequested()).isEqualTo(10_000L);
                });

        verifyNoInteractions(paystack);
        verify(ledgerService, never()).writeTransaction(any());
    }

    @Test
    @DisplayName("zero balance throws InsufficientBalanceException")
    void zero_balance_throws() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    // ── Validation failures ───────────────────────────────────────────────

    @Test
    @DisplayName("account not found returns 404")
    void account_not_found_returns_404() {
        when(accountRepo.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("closed account returns 409")
    void closed_account_returns_409() {
        stubAccountWithStatus(USER_ID, "CLOSED");

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("account owned by different user returns 403")
    void wrong_user_returns_403() {
        stubActiveAccount(UUID.randomUUID());  // different owner

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    // ── Paystack failure with reversal ────────────────────────────────────

    @Test
    @DisplayName("Paystack recipient creation failure reverses reservation")
    void recipient_failure_reverses_reservation() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        when(paystack.createTransferRecipient(any()))
                .thenThrow(new RuntimeException("Paystack unreachable"));
        when(refGen.generate()).thenReturn(REF, "STSH-202606-REV001");

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class);

        // Both the reservation and its reversal were written
        verify(ledgerService, times(2)).writeTransaction(any());
    }

    @Test
    @DisplayName("Paystack transfer 4xx reverses reservation and marks FAILED")
    void transfer_4xx_reverses_and_marks_failed() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubRecipientSuccess();
        when(paystack.initiateTransfer(any()))
                .thenThrow(new PaystackClientException("Account blocked", 400));
        when(refGen.generate()).thenReturn(REF, "STSH-202606-REV002");

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));

        verify(ledgerService, times(2)).writeTransaction(any());
    }

    // ── Sandbox-simulated completion (stash.paystack.sandbox.simulate-blocked-transfers) ──

    @Test
    @DisplayName("flag disabled: sandbox 'starter business' rejection reverses like any other failure")
    void sandbox_marker_ignored_when_flag_disabled() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubRecipientSuccess();
        when(paystack.initiateTransfer(any()))
                .thenThrow(new PaystackClientException(SANDBOX_STARTER_BUSINESS_MESSAGE, 400));
        when(refGen.generate()).thenReturn(REF, "STSH-202606-REV003");

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));

        verify(ledgerService, times(2)).writeTransaction(any()); // reservation + reversal
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("flag enabled: sandbox 'starter business' rejection completes without reversal")
    void sandbox_marker_completes_when_flag_enabled() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubRecipientSuccess();
        when(paystack.initiateTransfer(any()))
                .thenThrow(new PaystackClientException(SANDBOX_STARTER_BUSINESS_MESSAGE, 400));

        var result = sandboxSimulatingService.initiateWithdrawal(request(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.transactionReference()).isEqualTo(REF);
        assertThat(result.paystackTransferCode()).isEqualTo("SANDBOX-SIMULATED");

        // Only the original reservation was written — no compensating reversal.
        verify(ledgerService, times(1)).writeTransaction(any());
        verify(outbox).publish(any(WithdrawalCompletedEvent.class), eq("corr-001"));
    }

    @Test
    @DisplayName("flag enabled: an unrelated Paystack rejection still reverses normally")
    void sandbox_flag_does_not_affect_other_paystack_errors() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubRecipientSuccess();
        when(paystack.initiateTransfer(any()))
                .thenThrow(new PaystackClientException("Account blocked", 400));
        when(refGen.generate()).thenReturn(REF, "STSH-202606-REV004");

        assertThatThrownBy(() -> sandboxSimulatingService.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));

        verify(ledgerService, times(2)).writeTransaction(any()); // reservation + reversal
    }

    // ── Concurrent withdrawal race condition ──────────────────────────────

    @Test
    @DisplayName("concurrent withdrawal: second request sees reduced balance after first commits")
    void concurrent_withdrawal_second_sees_reduced_balance() {
        stubActiveAccount(USER_ID);
        stubRecipientSuccess();
        stubTransferSuccess();

        when(balanceService.computeBalanceWithLock(ACCOUNT_ID))
                .thenReturn(10_000L)
                .thenReturn(0L);

        var result1 = service.initiateWithdrawal(request(10_000L), "idem-001");
        assertThat(result1.status()).isEqualTo("PENDING");

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), "idem-002"))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    // ── transfer webhook handlers ─────────────────────────────────────────

    @Test
    @DisplayName("handleTransferSuccess marks transaction COMPLETED and emits event")
    void handle_transfer_success() {
        TransactionEntity pending = pendingWithdrawalTxn();
        when(txnRepo.findByExternalReference("TRF_test001")).thenReturn(Optional.of(pending));

        UUID result = service.handleTransferSuccess("TRF_test001", 10_000L, "corr-001");

        assertThat(result).isEqualTo(pending.getId());
        assertThat(pending.getStatus()).isEqualTo("COMPLETED");
        verify(outbox).publish(any(WithdrawalCompletedEvent.class), eq("corr-001"));
    }

    @Test
    @DisplayName("handleTransferFailed reverses reservation and emits failure event")
    void handle_transfer_failed() {
        TransactionEntity pending = pendingWithdrawalTxn();
        when(txnRepo.findByExternalReference("TRF_test002")).thenReturn(Optional.of(pending));
        when(refGen.generate()).thenReturn(REF, "STSH-202606-REVWH01");

        service.handleTransferFailed("TRF_test002", "Insufficient provider balance", "corr-002");

        assertThat(pending.getStatus()).isEqualTo("FAILED");
        verify(ledgerService).writeTransaction(argThat(cmd ->
                "REVERSAL".equals(cmd.transactionType())));
        verify(outbox).publish(any(WithdrawalFailedEvent.class), eq("corr-002"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void stubActiveAccount(UUID ownerId) {
        when(accountRepo.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(new LedgerAccountEntity(
                        "USER_WALLET", "USER", ownerId, "desc", Instant.now(FIXED_CLOCK))));
    }

    private void stubAccountWithStatus(UUID ownerId, String status) {
        LedgerAccountEntity acc = new LedgerAccountEntity(
                "USER_WALLET", "USER", ownerId, "desc", Instant.now(FIXED_CLOCK));
        try {
            var f = LedgerAccountEntity.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(acc, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(accountRepo.findById(ACCOUNT_ID)).thenReturn(Optional.of(acc));
    }

    private void stubRecipientSuccess() {
        var data = new TransferRecipientCreateResponse.RecipientData(
                "RCP_test001", "Akua Mensah", "0241234567");
        when(paystack.createTransferRecipient(any()))
                .thenReturn(new TransferRecipientCreateResponse(true, "Recipient created", data));
    }

    private void stubTransferSuccess() {
        var data = new TransferInitiateResponse.TransferData("TRF_test001", "pending", "TRF_test001");
        when(paystack.initiateTransfer(any()))
                .thenReturn(new TransferInitiateResponse(true, "Transfer queued", data));
    }

    private TransactionEntity pendingWithdrawalTxn() {
        return TransactionEntity.pendingWithdrawal(
                REF, USER_ID, ACCOUNT_ID, 10_000L, "corr-001", "idem-001",
                Instant.now(FIXED_CLOCK));
    }

    private WithdrawalInitiateRequest request(long amount) {
        return new WithdrawalInitiateRequest(
                ACCOUNT_ID, USER_ID, amount,
                "0241234567", "mtn", "Akua Mensah",
                "akua@stash.test", "corr-001",
                UUID.randomUUID(), "VAULT_WITHDRAWAL");
    }
}
