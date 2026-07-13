package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.exception.InsufficientBalanceException;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.BalanceService;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.ledger.service.LedgerWriteResult;
import com.stash.payments.ledger.service.TransactionReferenceGenerator;
import com.stash.payments.moolre.client.MoolreClient;
import com.stash.payments.moolre.dto.TransferResult;
import com.stash.payments.moolre.exception.MoolreClientException;
import com.stash.payments.outbox.service.OutboxPublisher;
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

    private static final Clock  FIXED_CLOCK   =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);
    private static final String SETTLEMENT_ID =
            "00000000-0000-0000-0000-000000000004";

    private final LedgerAccountRepository       accountRepo    = Mockito.mock(LedgerAccountRepository.class);
    private final BalanceService                balanceService = Mockito.mock(BalanceService.class);
    private final LedgerService                 ledgerService  = Mockito.mock(LedgerService.class);
    private final TransactionRepository         txnRepo        = Mockito.mock(TransactionRepository.class);
    private final MoolreClient                  moolre         = Mockito.mock(MoolreClient.class);
    private final OutboxPublisher               outbox         = Mockito.mock(OutboxPublisher.class);
    private final TransactionReferenceGenerator refGen         = Mockito.mock(TransactionReferenceGenerator.class);

    private final WithdrawalService service = new WithdrawalService(
            accountRepo, balanceService, ledgerService, txnRepo,
            moolre, outbox, refGen, FIXED_CLOCK, SETTLEMENT_ID);

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
        when(moolre.validateRecipient(any(), any())).thenReturn("Akua Mensah");
    }

    @Test
    @DisplayName("sufficient balance: reserve, call Moolre, return 202 PENDING")
    void happy_path_returns_202_pending() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubTransferPending();

        var result = service.initiateWithdrawal(request(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.transactionReference()).isEqualTo(REF);
        assertThat(result.providerTransferCode()).isEqualTo("32759150");
    }

    @Test
    @DisplayName("synchronous txstatus=1 completes immediately")
    void sync_success_completes() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubTransferSuccess();

        var result = service.initiateWithdrawal(request(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.providerTransferCode()).isEqualTo("32759150");
        verify(outbox).publish(any(WithdrawalCompletedEvent.class), eq("corr-001"));
    }

    @Test
    @DisplayName("ledger reservation entries posted before Moolre call")
    void reservation_posted_before_moolre_call() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        stubTransferPending();

        service.initiateWithdrawal(request(10_000L), IDEM_KEY);

        InOrder order = Mockito.inOrder(ledgerService, moolre);
        order.verify(ledgerService).writeTransaction(any());
        order.verify(moolre).initiateTransfer(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("insufficient balance throws InsufficientBalanceException")
    void insufficient_balance_throws_422() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(5_000L);

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(InsufficientBalanceException.class);

        verifyNoInteractions(moolre);
        verify(ledgerService, never()).writeTransaction(any());
    }

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
        stubActiveAccount(UUID.randomUUID());

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("Moolre transfer 4xx reverses reservation and marks FAILED")
    void transfer_4xx_reverses_and_marks_failed() {
        stubActiveAccount(USER_ID);
        when(balanceService.computeBalanceWithLock(ACCOUNT_ID)).thenReturn(50_000L);
        when(moolre.initiateTransfer(any(), any(), any(), any(), any()))
                .thenThrow(new MoolreClientException("Account blocked", 400));
        when(refGen.generate()).thenReturn(REF, "STSH-202606-REV002");

        assertThatThrownBy(() -> service.initiateWithdrawal(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));

        verify(ledgerService, times(2)).writeTransaction(any());
    }

    @Test
    @DisplayName("handleTransferSuccess looks up by STSH reference")
    void handle_transfer_success() {
        TransactionEntity pending = pendingWithdrawalTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(pending));

        UUID result = service.handleTransferSuccess(REF, 10_000L, "corr-001");

        assertThat(result).isEqualTo(pending.getId());
        assertThat(pending.getStatus()).isEqualTo("COMPLETED");
        verify(outbox).publish(any(WithdrawalCompletedEvent.class), eq("corr-001"));
    }

    @Test
    @DisplayName("handleTransferFailed reverses reservation and emits failure event")
    void handle_transfer_failed() {
        TransactionEntity pending = pendingWithdrawalTxn();
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(pending));
        when(refGen.generate()).thenReturn(REF, "STSH-202606-REVWH01");

        service.handleTransferFailed(REF, "Insufficient provider balance", "corr-002");

        assertThat(pending.getStatus()).isEqualTo("FAILED");
        verify(ledgerService).writeTransaction(argThat(cmd ->
                "REVERSAL".equals(cmd.transactionType())));
        verify(outbox).publish(any(WithdrawalFailedEvent.class), eq("corr-002"));
    }

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

    private void stubTransferPending() {
        when(moolre.initiateTransfer(any(), any(), any(), any(), any()))
                .thenReturn(new TransferResult(
                        "OBGH01", "queued", true, 0,
                        "233241234567", "32759150", REF, null, "Akua Mensah",
                        "100.00", null, null, null));
    }

    private void stubTransferSuccess() {
        when(moolre.initiateTransfer(any(), any(), any(), any(), any()))
                .thenReturn(new TransferResult(
                        "OBGH01", "Pay out Successful", true, 1,
                        "233241234567", "32759150", REF, null, "Akua Mensah",
                        "100.00", null, null, null));
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
