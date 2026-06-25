package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.exception.InsufficientBalanceException;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.BalanceService;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.ledger.service.LedgerWriteCommand;
import com.stash.payments.ledger.service.LedgerWriteResult;
import com.stash.payments.ledger.service.TransactionReferenceGenerator;
import com.stash.payments.outbox.domain.OutboxEvent;
import com.stash.payments.outbox.service.OutboxPublisher;
import com.stash.payments.transaction.api.dto.TransferRequest;
import com.stash.payments.transaction.api.dto.TransferResponse;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.event.TransferPostedEvent;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class TransferServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerAccountRepository       accountRepo    = Mockito.mock(LedgerAccountRepository.class);
    private final BalanceService                balanceService = Mockito.mock(BalanceService.class);
    private final LedgerService                 ledgerService  = Mockito.mock(LedgerService.class);
    private final TransactionRepository         txnRepo        = Mockito.mock(TransactionRepository.class);
    private final OutboxPublisher               outbox         = Mockito.mock(OutboxPublisher.class);
    private final TransactionReferenceGenerator refGen         = Mockito.mock(TransactionReferenceGenerator.class);

    private final TransferService service = new TransferService(
            accountRepo, balanceService, ledgerService, txnRepo, outbox, refGen, FIXED_CLOCK);

    private static final UUID   SOURCE_ID = UUID.randomUUID();
    private static final UUID   DEST_ID   = UUID.randomUUID();
    private static final UUID   USER_A    = UUID.randomUUID();
    private static final UUID   USER_B    = UUID.randomUUID();
    private static final String REF       = "STSH-202606-TRF001";
    private static final String IDEM_KEY  = "idem-transfer-001";
    private static final UUID   LEDGER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(refGen.generate()).thenReturn(REF);
        when(txnRepo.findByReference(REF)).thenReturn(Optional.empty());
        when(txnRepo.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(txnRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerService.writeTransaction(any()))
                .thenReturn(new LedgerWriteResult(LEDGER_ID, REF, 10_000L));
        when(outbox.publish(any(), any())).thenReturn(null);
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid transfer posts immediately and returns 201 POSTED")
    void happy_path_returns_posted() {
        stubAccounts();
        when(balanceService.computeBalanceWithLock(SOURCE_ID)).thenReturn(50_000L);

        TransferResponse result = service.transfer(request(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("POSTED");
        assertThat(result.transactionReference()).isEqualTo(REF);
        assertThat(result.ledgerTransactionId()).isEqualTo(LEDGER_ID);
    }

    @Test
    @DisplayName("ledger entries: DEBIT source, CREDIT destination")
    void ledger_entries_correct_directions() {
        stubAccounts();
        when(balanceService.computeBalanceWithLock(SOURCE_ID)).thenReturn(50_000L);

        service.transfer(request(10_000L), IDEM_KEY);

        ArgumentCaptor<LedgerWriteCommand> captor =
                ArgumentCaptor.forClass(LedgerWriteCommand.class);
        verify(ledgerService).writeTransaction(captor.capture());

        LedgerWriteCommand cmd = captor.getValue();
        assertThat(cmd.entries()).hasSize(2);
        assertThat(cmd.entries().get(0).direction().name()).isEqualTo("DEBIT");
        assertThat(cmd.entries().get(0).accountId()).isEqualTo(SOURCE_ID);
        assertThat(cmd.entries().get(1).direction().name()).isEqualTo("CREDIT");
        assertThat(cmd.entries().get(1).accountId()).isEqualTo(DEST_ID);
    }

    @Test
    @DisplayName("transaction row created in COMPLETED status (no PENDING phase)")
    void transaction_row_created_completed() {
        stubAccounts();
        when(balanceService.computeBalanceWithLock(SOURCE_ID)).thenReturn(50_000L);

        service.transfer(request(10_000L), IDEM_KEY);

        ArgumentCaptor<TransactionEntity> txnCaptor =
                ArgumentCaptor.forClass(TransactionEntity.class);
        verify(txnRepo).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(txnCaptor.getValue().getLedgerTransactionId()).isEqualTo(LEDGER_ID);
    }

    @Test
    @DisplayName("outbox event emitted with correct fields")
    void outbox_event_emitted() {
        stubAccounts();
        when(balanceService.computeBalanceWithLock(SOURCE_ID)).thenReturn(50_000L);

        service.transfer(request(10_000L), IDEM_KEY);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).publish(eventCaptor.capture(), any());

        var event = (TransferPostedEvent) eventCaptor.getValue();
        assertThat(event.sourceAccountId()).isEqualTo(SOURCE_ID);
        assertThat(event.destinationAccountId()).isEqualTo(DEST_ID);
        assertThat(event.amountPesewas()).isEqualTo(10_000L);
        assertThat(event.transactionReference()).isEqualTo(REF);
    }

    // ── Idempotency ───────────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate idempotency key returns cached response without re-executing")
    void idempotent_retry_returns_cached_response() {
        TransactionEntity existing = TransactionEntity.completedTransfer(
                REF, USER_A, USER_B, 10_000L, LEDGER_ID,
                "corr-001", IDEM_KEY, Instant.now(FIXED_CLOCK));
        when(txnRepo.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(existing));

        TransferResponse result = service.transfer(request(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("POSTED");
        assertThat(result.transactionReference()).isEqualTo(REF);
        verifyNoInteractions(ledgerService, balanceService, outbox);
    }

    // ── Insufficient balance ──────────────────────────────────────────────

    @Test
    @DisplayName("insufficient balance throws InsufficientBalanceException")
    void insufficient_balance_throws() {
        stubAccounts();
        when(balanceService.computeBalanceWithLock(SOURCE_ID)).thenReturn(5_000L);

        assertThatThrownBy(() -> service.transfer(request(10_000L), IDEM_KEY))
                .isInstanceOf(InsufficientBalanceException.class)
                .satisfies(ex -> {
                    var e = (InsufficientBalanceException) ex;
                    assertThat(e.getAvailable()).isEqualTo(5_000L);
                    assertThat(e.getRequested()).isEqualTo(10_000L);
                });

        verifyNoInteractions(ledgerService);
    }

    @Test
    @DisplayName("exact balance equals amount: transfer succeeds")
    void exact_balance_matches_amount_succeeds() {
        stubAccounts();
        when(balanceService.computeBalanceWithLock(SOURCE_ID)).thenReturn(10_000L);

        assertThatCode(() -> service.transfer(request(10_000L), IDEM_KEY))
                .doesNotThrowAnyException();
    }

    // ── Account validation failures ───────────────────────────────────────

    @Test
    @DisplayName("source account not found returns 404")
    void source_not_found_returns_404() {
        when(accountRepo.findById(SOURCE_ID)).thenReturn(Optional.empty());
        when(accountRepo.findById(DEST_ID)).thenReturn(Optional.of(activeAccount(DEST_ID, USER_B)));

        assertThatThrownBy(() -> service.transfer(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("destination account not found returns 404")
    void destination_not_found_returns_404() {
        when(accountRepo.findById(SOURCE_ID)).thenReturn(Optional.of(activeAccount(SOURCE_ID, USER_A)));
        when(accountRepo.findById(DEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("inactive source account returns 409")
    void inactive_source_returns_409() {
        setStatus(accountRepo, SOURCE_ID, "FROZEN");
        when(accountRepo.findById(DEST_ID)).thenReturn(Optional.of(activeAccount(DEST_ID, USER_B)));

        assertThatThrownBy(() -> service.transfer(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("inactive destination account returns 409")
    void inactive_destination_returns_409() {
        when(accountRepo.findById(SOURCE_ID)).thenReturn(Optional.of(activeAccount(SOURCE_ID, USER_A)));
        setStatus(accountRepo, DEST_ID, "CLOSED");

        assertThatThrownBy(() -> service.transfer(request(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("self-transfer (same account) returns 422")
    void self_transfer_returns_422() {
        TransferRequest selfTransfer = new TransferRequest(
                SOURCE_ID, SOURCE_ID, 10_000L,
                "TRANSFER", null, null, "corr-001", "self");

        assertThatThrownBy(() -> service.transfer(selfTransfer, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    // ── Concurrent double-spend ───────────────────────────────────────────

    @Test
    @DisplayName("double-spend: second concurrent transfer sees reduced balance and fails")
    void concurrent_double_spend_prevented() {
        stubAccounts();
        when(balanceService.computeBalanceWithLock(SOURCE_ID))
                .thenReturn(10_000L)
                .thenReturn(0L);
        when(txnRepo.findByIdempotencyKey("idem-002")).thenReturn(Optional.empty());

        assertThatCode(() -> service.transfer(request(10_000L), IDEM_KEY))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.transfer(request(10_000L), "idem-002"))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(ledgerService, times(1)).writeTransaction(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void stubAccounts() {
        when(accountRepo.findById(SOURCE_ID))
                .thenReturn(Optional.of(activeAccount(SOURCE_ID, USER_A)));
        when(accountRepo.findById(DEST_ID))
                .thenReturn(Optional.of(activeAccount(DEST_ID, USER_B)));
    }

    private LedgerAccountEntity activeAccount(UUID id, UUID ownerId) {
        return new LedgerAccountEntity("USER_WALLET", "USER", ownerId, "desc",
                Instant.now(FIXED_CLOCK));
    }

    private void setStatus(LedgerAccountRepository repo, UUID accountId, String status) {
        when(repo.findById(accountId)).thenAnswer(inv -> {
            LedgerAccountEntity acc = new LedgerAccountEntity(
                    "USER_WALLET", "USER", USER_A, "desc", Instant.now(FIXED_CLOCK));
            try {
                var f = LedgerAccountEntity.class.getDeclaredField("status");
                f.setAccessible(true);
                f.set(acc, status);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return Optional.of(acc);
        });
    }

    private TransferRequest request(long amount) {
        return new TransferRequest(
                SOURCE_ID, DEST_ID, amount,
                "TRANSFER", UUID.randomUUID(), "PEER_TRANSFER",
                "corr-001", "Test transfer");
    }
}
