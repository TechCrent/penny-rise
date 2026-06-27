package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.EntryDirection;
import com.stash.payments.ledger.domain.EntryRequest;
import com.stash.payments.ledger.domain.LedgerEntryEntity;
import com.stash.payments.ledger.domain.LedgerTransactionEntity;
import com.stash.payments.ledger.event.LedgerTransactionPostedEvent;
import com.stash.payments.ledger.exception.LedgerImbalanceException;
import com.stash.payments.ledger.repository.LedgerEntryRepository;
import com.stash.payments.ledger.repository.LedgerTransactionRepository;
import com.stash.payments.outbox.domain.OutboxEvent;
import com.stash.payments.outbox.service.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LedgerServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerTransactionRepository txnRepo   = Mockito.mock(LedgerTransactionRepository.class);
    private final LedgerEntryRepository       entryRepo = Mockito.mock(LedgerEntryRepository.class);
    private final OutboxPublisher             outbox    = Mockito.mock(OutboxPublisher.class);
    private final LedgerService service =
            new LedgerService(txnRepo, entryRepo, outbox, FIXED_CLOCK);

    private static final UUID   WALLET_ACCOUNT = UUID.randomUUID();
    private static final UUID   VAULT_ACCOUNT  = UUID.randomUUID();
    private static final String REFERENCE      = "STSH-202606-ABC123";
    private static final String CORRELATION    = "corr-test-001";

    @BeforeEach
    void setUp() {
        when(txnRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outbox.publish(any(), any())).thenReturn(null);
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("balanced two-entry transaction posts successfully")
    void balanced_transaction_posts_successfully() {
        var command = balancedCommand(10_000L);

        LedgerWriteResult result = service.writeTransaction(command);

        assertThat(result.transactionReference()).isEqualTo(REFERENCE);
        assertThat(result.totalAmount()).isEqualTo(10_000L);
        verify(txnRepo).save(any(LedgerTransactionEntity.class));
        verify(entryRepo, times(2)).save(any(LedgerEntryEntity.class));
        verify(outbox).publish(any(), eq(CORRELATION));
    }

    @Test
    @DisplayName("balanced multi-entry transaction posts all entries")
    void balanced_multi_entry_posts_all_entries() {
        var command = command(List.of(
                EntryRequest.of(WALLET_ACCOUNT,   EntryDirection.DEBIT,  10_000L),
                EntryRequest.of(VAULT_ACCOUNT,    EntryDirection.CREDIT,  5_000L),
                EntryRequest.of(UUID.randomUUID(), EntryDirection.CREDIT,  5_000L)
        ));

        LedgerWriteResult result = service.writeTransaction(command);

        assertThat(result.totalAmount()).isEqualTo(10_000L);
        verify(entryRepo, times(3)).save(any(LedgerEntryEntity.class));
    }

    @Test
    @DisplayName("result contains the ledger transaction id from the saved entity")
    void result_contains_correct_transaction_id() {
        UUID expectedId = UUID.randomUUID();
        when(txnRepo.save(any())).thenAnswer(inv -> {
            LedgerTransactionEntity e = inv.getArgument(0);
            try {
                var f = LedgerTransactionEntity.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(e, expectedId);
            } catch (Exception ex) { throw new RuntimeException(ex); }
            return e;
        });

        var result = service.writeTransaction(balancedCommand(5_000L));

        assertThat(result.ledgerTransactionId()).isEqualTo(expectedId);
    }

    // ── Imbalance rejection ───────────────────────────────────────────────

    @Test
    @DisplayName("imbalanced transaction throws LedgerImbalanceException before any write")
    void imbalanced_transaction_rejected_before_write() {
        var command = command(List.of(
                EntryRequest.of(WALLET_ACCOUNT, EntryDirection.DEBIT,  10_000L),
                EntryRequest.of(VAULT_ACCOUNT,  EntryDirection.CREDIT,  9_000L)
        ));

        assertThatThrownBy(() -> service.writeTransaction(command))
                .isInstanceOf(LedgerImbalanceException.class)
                .hasMessageContaining("10000")
                .hasMessageContaining("9000");

        verifyNoInteractions(txnRepo);
        verifyNoInteractions(entryRepo);
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("all-debit transaction rejected (zero credits)")
    void all_debit_rejected() {
        var command = command(List.of(
                EntryRequest.of(WALLET_ACCOUNT, EntryDirection.DEBIT, 5_000L),
                EntryRequest.of(VAULT_ACCOUNT,  EntryDirection.DEBIT, 5_000L)
        ));

        assertThatThrownBy(() -> service.writeTransaction(command))
                .isInstanceOf(LedgerImbalanceException.class);

        verifyNoInteractions(txnRepo);
    }

    @Test
    @DisplayName("all-credit transaction rejected (zero debits)")
    void all_credit_rejected() {
        var command = command(List.of(
                EntryRequest.of(WALLET_ACCOUNT, EntryDirection.CREDIT, 5_000L),
                EntryRequest.of(VAULT_ACCOUNT,  EntryDirection.CREDIT, 5_000L)
        ));

        assertThatThrownBy(() -> service.writeTransaction(command))
                .isInstanceOf(LedgerImbalanceException.class);
    }

    // ── Single-entry rejection ────────────────────────────────────────────

    @Test
    @DisplayName("single-entry transaction rejected by LedgerWriteCommand constructor")
    void single_entry_rejected_at_command_construction() {
        assertThatThrownBy(() -> command(List.of(
                EntryRequest.of(WALLET_ACCOUNT, EntryDirection.DEBIT, 5_000L)
        ))).isInstanceOf(IllegalArgumentException.class)
           .hasMessageContaining("at least two entries");
    }

    @Test
    @DisplayName("null entries list rejected by LedgerWriteCommand constructor")
    void null_entries_rejected_at_construction() {
        assertThatThrownBy(() -> command(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Zero and negative amounts ─────────────────────────────────────────

    @Test
    @DisplayName("zero-amount entry rejected by EntryRequest constructor")
    void zero_amount_rejected() {
        assertThatThrownBy(() -> EntryRequest.of(WALLET_ACCOUNT, EntryDirection.DEBIT, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("negative-amount entry rejected by EntryRequest constructor")
    void negative_amount_rejected() {
        assertThatThrownBy(() -> EntryRequest.of(WALLET_ACCOUNT, EntryDirection.DEBIT, -100L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Outbox event content ──────────────────────────────────────────────

    @Test
    @DisplayName("outbox event carries correct transaction reference and amount")
    void outbox_event_carries_correct_fields() {
        service.writeTransaction(balancedCommand(7_500L));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).publish(eventCaptor.capture(), eq(CORRELATION));

        var event = (LedgerTransactionPostedEvent) eventCaptor.getValue();
        assertThat(event.transactionReference()).isEqualTo(REFERENCE);
        assertThat(event.totalAmountPesewas()).isEqualTo(7_500L);
        assertThat(event.transactionType()).isEqualTo("DEPOSIT");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private LedgerWriteCommand balancedCommand(long amount) {
        return command(List.of(
                EntryRequest.of(WALLET_ACCOUNT, EntryDirection.DEBIT,  amount),
                EntryRequest.of(VAULT_ACCOUNT,  EntryDirection.CREDIT, amount)
        ));
    }

    private LedgerWriteCommand command(List<EntryRequest> entries) {
        return new LedgerWriteCommand(
                "DEPOSIT", REFERENCE,
                UUID.randomUUID(), "VAULT_DEPOSIT",
                entries, CORRELATION, "Test deposit"
        );
    }
}
