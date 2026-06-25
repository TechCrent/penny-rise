package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.shared.security.CallerContext;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class TransactionDetailServiceTest {

    private final TransactionRepository   txnRepo       = Mockito.mock(TransactionRepository.class);
    private final LedgerService           ledgerService = Mockito.mock(LedgerService.class);
    private final LedgerAccountRepository accountRepo   = Mockito.mock(LedgerAccountRepository.class);

    private final TransactionDetailService service =
            new TransactionDetailService(txnRepo, ledgerService, accountRepo);

    private static final String  REF               = "STSH-202606-TEST01";
    private static final UUID    USER_ID           = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID    COUNTERPARTY_USER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID    THIRD_PARTY_USER  = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID    LEDGER_TXN        = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID    ACCOUNT_A         = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID    ACCOUNT_B         = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final Instant NOW               = Instant.parse("2026-06-25T09:00:00Z");

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("transaction not found returns 404")
    void transaction_not_found_returns_404() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.empty());

        var ex = assertThrows(ResponseStatusException.class,
                () -> service.getDetail(REF, CallerContext.user(USER_ID)));
        assertThat(ex.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    @DisplayName("pending transaction has empty entries and does not call ledger")
    void pending_transaction_has_empty_entries() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(pendingTxn()));

        var result = service.getDetail(REF, CallerContext.user(USER_ID));

        assertThat(result.entries()).isEmpty();
        verifyNoInteractions(ledgerService);
    }

    @Test
    @DisplayName("completed transfer exposes two ledger entries")
    void completed_transfer_has_two_entries() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(completedTxn()));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(twoEntryRows());

        var result = service.getDetail(REF, CallerContext.user(USER_ID));

        assertThat(result.entries()).hasSize(2);
    }

    // ── Entry mapping ─────────────────────────────────────────────────────

    @Test
    @DisplayName("entry direction, amounts, and cedis string are mapped correctly")
    void entry_direction_and_amounts_mapped_correctly() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(completedTxn()));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(twoEntryRows());

        var result = service.getDetail(REF, CallerContext.user(USER_ID));

        var debit = result.entries().stream()
                .filter(e -> "DEBIT".equals(e.direction())).findFirst().orElseThrow();
        assertThat(debit.amountPesewas()).isEqualTo(10_000L);
        assertThat(debit.amountCedis()).isEqualTo("100.00");

        var credit = result.entries().stream()
                .filter(e -> "CREDIT".equals(e.direction())).findFirst().orElseThrow();
        assertThat(credit.amountPesewas()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("failed transaction (null ledger id) returns empty entries")
    void null_ledger_transaction_produces_empty_entries() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(failedTxn()));

        var result = service.getDetail(REF, CallerContext.user(USER_ID));

        assertThat(result.entries()).isEmpty();
        verifyNoInteractions(ledgerService);
    }

    // ── Field mapping ─────────────────────────────────────────────────────

    @Test
    @DisplayName("fee amount zero for a transfer")
    void fee_amount_included_in_detail() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(completedTxn()));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(twoEntryRows());

        var result = service.getDetail(REF, CallerContext.user(USER_ID));

        assertThat(result.feeAmountPesewas()).isEqualTo(0L);
        assertThat(result.feeAmountCedis()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("completed_at is null for a pending transaction")
    void completed_at_null_for_pending() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(pendingTxn()));

        var result = service.getDetail(REF, CallerContext.user(USER_ID));

        assertThat(result.completedAt()).isNull();
    }

    @Test
    @DisplayName("external_reference is included for a deposit")
    void external_reference_included_for_deposit() {
        var deposit = pendingTxn();
        deposit.setExternalReference("PS-EXTERNAL-REF");
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(deposit));

        var result = service.getDetail(REF, CallerContext.user(USER_ID));

        assertThat(result.externalReference()).isEqualTo("PS-EXTERNAL-REF");
    }

    // ── Ownership enforcement ─────────────────────────────────────────────

    @Test
    @DisplayName("initiating user can read their own deposit")
    void deposit_transaction_accessible_by_initiator() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(completedDeposit()));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(List.of());

        var result = service.getDetail(REF, CallerContext.user(USER_ID));

        assertThat(result.transactionType()).isEqualTo("DEPOSIT");
    }

    @Test
    @DisplayName("initiating user can read their own withdrawal")
    void withdrawal_transaction_accessible_by_initiator() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(completedWithdrawal()));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(List.of());

        assertThatCode(() -> service.getDetail(REF, CallerContext.user(USER_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("counterparty user can read the transfer via counterpartyUserId field")
    void counterparty_can_see_transfer() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(completedTxn()));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(twoEntryRows());

        assertThatCode(() -> service.getDetail(REF, CallerContext.user(COUNTERPARTY_USER)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("third-party user gets same 404 as non-existent transaction")
    void third_party_and_nonexistent_return_same_404() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(completedTxn()));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(List.of());

        var ex = assertThrows(ResponseStatusException.class,
                () -> service.getDetail(REF, CallerContext.user(THIRD_PARTY_USER)));
        assertThat(ex.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    @DisplayName("internal caller bypasses ownership check and does not consult accountRepo")
    void internal_caller_bypasses_ownership() {
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(completedTxn()));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(twoEntryRows());

        var result = service.getDetail(REF, CallerContext.internal());

        assertThat(result.entries()).hasSize(2);
        verifyNoInteractions(accountRepo);
    }

    @Test
    @DisplayName("counterparty access granted via ledger account ownership when counterpartyUserId is null")
    void counterparty_access_via_ledger_ownership() {
        // Transfer where counterpartyUserId is null, but COUNTERPARTY_USER owns ACCOUNT_B
        var txn = TransactionEntity.completedTransfer(
                REF, USER_ID, null, 10_000L, LEDGER_TXN, "corr-001", "idem-001", NOW);
        when(txnRepo.findByReference(REF)).thenReturn(Optional.of(txn));
        when(ledgerService.getEntriesForTransaction(LEDGER_TXN)).thenReturn(twoEntryRows());
        when(accountRepo.findById(ACCOUNT_A))
                .thenReturn(Optional.of(accountOwning(USER_ID)));
        when(accountRepo.findById(ACCOUNT_B))
                .thenReturn(Optional.of(accountOwning(COUNTERPARTY_USER)));

        assertThatCode(() -> service.getDetail(REF, CallerContext.user(COUNTERPARTY_USER)))
                .doesNotThrowAnyException();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private TransactionEntity completedTxn() {
        return TransactionEntity.completedTransfer(
                REF, USER_ID, COUNTERPARTY_USER, 10_000L,
                LEDGER_TXN, "corr-001", "idem-001", NOW);
    }

    private TransactionEntity pendingTxn() {
        return TransactionEntity.pendingDeposit(
                REF, USER_ID, 10_000L, "corr-001", "idem-001", NOW);
    }

    private TransactionEntity completedDeposit() {
        var t = TransactionEntity.pendingDeposit(
                REF, USER_ID, 10_000L, "corr-001", "idem-001", NOW);
        t.markCompleted(LEDGER_TXN, NOW);
        return t;
    }

    private TransactionEntity completedWithdrawal() {
        var t = TransactionEntity.pendingWithdrawal(
                REF, USER_ID, ACCOUNT_A, 10_000L, "corr-001", "idem-001", NOW);
        t.markCompleted(LEDGER_TXN, NOW);
        return t;
    }

    private TransactionEntity failedTxn() {
        var t = TransactionEntity.pendingDeposit(
                REF, USER_ID, 10_000L, "corr-001", "idem-001", NOW);
        t.markFailed(NOW);
        return t;
    }

    private List<Object[]> twoEntryRows() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(entryRow(ACCOUNT_A, "DEBIT",  10_000L, "USER_WALLET", "Transfer debit"));
        rows.add(entryRow(ACCOUNT_B, "CREDIT", 10_000L, "USER_WALLET", "Transfer credit"));
        return rows;
    }

    private static Object[] entryRow(UUID accountId, String direction, long amount,
                                      String accountType, String narrative) {
        return new Object[]{ accountId, direction, amount, accountType, narrative };
    }

    private static LedgerAccountEntity accountOwning(UUID ownerId) {
        return new LedgerAccountEntity("USER_WALLET", "USER", ownerId, "wallet", NOW);
    }
}
