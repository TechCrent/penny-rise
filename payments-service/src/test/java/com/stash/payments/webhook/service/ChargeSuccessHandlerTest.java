package com.stash.payments.webhook.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.ledger.service.LedgerWriteCommand;
import com.stash.payments.ledger.service.LedgerWriteResult;
import com.stash.payments.outbox.domain.OutboxEvent;
import com.stash.payments.outbox.service.OutboxPublisher;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.event.DepositCompletedEvent;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChargeSuccessHandlerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final TransactionRepository   txnRepo     = Mockito.mock(TransactionRepository.class);
    private final LedgerAccountRepository accountRepo = Mockito.mock(LedgerAccountRepository.class);
    private final LedgerService           ledgerService = Mockito.mock(LedgerService.class);
    private final OutboxPublisher         outbox      = Mockito.mock(OutboxPublisher.class);

    private static final UUID   USER_ID                = UUID.randomUUID();
    private static final UUID   USER_WALLET_ID          = UUID.randomUUID();
    private static final UUID   VAULT_LEDGER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID   SETTLEMENT_ID           = UUID.randomUUID();
    private static final UUID   LEDGER_TXN_ID           = UUID.randomUUID();
    private static final String PAYSTACK_REF            = "pay_ref_001";
    private static final String INTERNAL_REF            = "STSH-202606-DEP001";
    private static final String CORR                    = "corr-webhook-001";

    private final ChargeSuccessHandler handler = new ChargeSuccessHandler(
            txnRepo, accountRepo, ledgerService, outbox, FIXED_CLOCK, SETTLEMENT_ID);

    @BeforeEach
    void setUp() {
        when(ledgerService.writeTransaction(any()))
                .thenReturn(new LedgerWriteResult(LEDGER_TXN_ID, INTERNAL_REF, 10_000L));
        when(txnRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outbox.publish(any(), any())).thenReturn(null);
    }

    // ── The bug fix: vault deposits credit the vault, not USER_WALLET ──────

    @Test
    @DisplayName("vault deposit credits the vault's ledger account, not USER_WALLET")
    void vault_deposit_credits_vault_account() {
        TransactionEntity txn = pendingDepositTo(VAULT_LEDGER_ACCOUNT_ID);
        when(txnRepo.findByExternalReference(PAYSTACK_REF)).thenReturn(Optional.of(txn));

        handler.handle(chargeSuccessPayload(), CORR);

        ArgumentCaptor<LedgerWriteCommand> captor = ArgumentCaptor.forClass(LedgerWriteCommand.class);
        verify(ledgerService).writeTransaction(captor.capture());
        assertThat(captor.getValue().entries().get(0).accountId()).isEqualTo(VAULT_LEDGER_ACCOUNT_ID);

        // USER_WALLET is never even looked up for a vault deposit.
        verifyNoInteractions(accountRepo);
    }

    @Test
    @DisplayName("direct USER_WALLET deposit credits the wallet (unchanged behaviour)")
    void direct_wallet_deposit_credits_wallet() {
        TransactionEntity txn = pendingDepositTo(USER_WALLET_ID);
        when(txnRepo.findByExternalReference(PAYSTACK_REF)).thenReturn(Optional.of(txn));

        handler.handle(chargeSuccessPayload(), CORR);

        ArgumentCaptor<LedgerWriteCommand> captor = ArgumentCaptor.forClass(LedgerWriteCommand.class);
        verify(ledgerService).writeTransaction(captor.capture());
        assertThat(captor.getValue().entries().get(0).accountId()).isEqualTo(USER_WALLET_ID);
    }

    @Test
    @DisplayName("outbox DepositCompletedEvent carries the actual credited account, not USER_WALLET")
    void outbox_event_carries_credited_account() {
        TransactionEntity txn = pendingDepositTo(VAULT_LEDGER_ACCOUNT_ID);
        when(txnRepo.findByExternalReference(PAYSTACK_REF)).thenReturn(Optional.of(txn));

        handler.handle(chargeSuccessPayload(), CORR);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).publish(eventCaptor.capture(), any());
        var event = (DepositCompletedEvent) eventCaptor.getValue();
        assertThat(event.ledgerAccountId()).isEqualTo(VAULT_LEDGER_ACCOUNT_ID);
    }

    // ── Legacy fallback (pre-V3 rows with no destination stored) ───────────

    @Test
    @DisplayName("null destination_ledger_account_id falls back to resolving USER_WALLET")
    void null_destination_falls_back_to_user_wallet() {
        TransactionEntity txn = pendingDepositTo(null);
        when(txnRepo.findByExternalReference(PAYSTACK_REF)).thenReturn(Optional.of(txn));
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.of(walletAccount()));

        handler.handle(chargeSuccessPayload(), CORR);

        ArgumentCaptor<LedgerWriteCommand> captor = ArgumentCaptor.forClass(LedgerWriteCommand.class);
        verify(ledgerService).writeTransaction(captor.capture());
        assertThat(captor.getValue().entries().get(0).accountId()).isEqualTo(USER_WALLET_ID);
    }

    @Test
    @DisplayName("null destination with no USER_WALLET on record throws")
    void null_destination_no_wallet_throws() {
        TransactionEntity txn = pendingDepositTo(null);
        when(txnRepo.findByExternalReference(PAYSTACK_REF)).thenReturn(Optional.of(txn));
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> handler.handle(chargeSuccessPayload(), CORR))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Idempotency / unknown reference ─────────────────────────────────────

    @Test
    @DisplayName("unknown Paystack reference returns null without writing anything")
    void unknown_reference_returns_null() {
        when(txnRepo.findByExternalReference(PAYSTACK_REF)).thenReturn(Optional.empty());

        UUID result = handler.handle(chargeSuccessPayload(), CORR);

        assertThat(result).isNull();
        verifyNoInteractions(ledgerService, outbox);
    }

    @Test
    @DisplayName("already-COMPLETED transaction is a no-op (duplicate webhook delivery)")
    void already_completed_is_noop() {
        TransactionEntity txn = pendingDepositTo(VAULT_LEDGER_ACCOUNT_ID);
        txn.markCompleted(LEDGER_TXN_ID, Instant.now(FIXED_CLOCK));
        when(txnRepo.findByExternalReference(PAYSTACK_REF)).thenReturn(Optional.of(txn));

        UUID result = handler.handle(chargeSuccessPayload(), CORR);

        assertThat(result).isEqualTo(txn.getId());
        verifyNoInteractions(ledgerService, outbox);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private TransactionEntity pendingDepositTo(UUID destinationLedgerAccountId) {
        TransactionEntity txn = TransactionEntity.pendingDeposit(
                INTERNAL_REF, USER_ID, 10_000L, destinationLedgerAccountId,
                CORR, "idem-001", Instant.now(FIXED_CLOCK));
        txn.setExternalReference(PAYSTACK_REF);
        return txn;
    }

    private LedgerAccountEntity walletAccount() {
        LedgerAccountEntity acc = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "desc", Instant.now(FIXED_CLOCK));
        try {
            var f = LedgerAccountEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(acc, USER_WALLET_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return acc;
    }

    private Map<String, Object> chargeSuccessPayload() {
        return Map.of("reference", PAYSTACK_REF, "amount", 10_000);
    }
}
