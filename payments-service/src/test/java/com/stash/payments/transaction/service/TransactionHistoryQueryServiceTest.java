package com.stash.payments.transaction.service;

import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionHistoryQueryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LEDGER_TXN_WITH_REF = UUID.randomUUID();
    private static final UUID LEDGER_TXN_NO_REF = UUID.randomUUID();
    private static final UUID VAULT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-11T09:00:00Z");

    private final TransactionRepository txnRepo = Mockito.mock(TransactionRepository.class);
    private final LedgerService ledgerService = Mockito.mock(LedgerService.class);

    private final TransactionHistoryQueryService service =
            new TransactionHistoryQueryService(txnRepo, ledgerService);

    @Test
    @DisplayName("vault deposit row is enriched with business_reference_type/id and narrative")
    void vault_deposit_row_enriched_with_business_reference() {
        when(txnRepo.findHistoryForUser(eq(USER_ID), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(historyRow("STSH-1", LEDGER_TXN_WITH_REF)));
        when(ledgerService.getBusinessReferencesForTransactions(List.of(LEDGER_TXN_WITH_REF)))
                .thenReturn(List.<Object[]>of(new Object[]{
                        LEDGER_TXN_WITH_REF, "VAULT_DEPOSIT", VAULT_ID, "Vault deposit narrative"
                }));

        var result = service.getHistory(USER_ID, null, null, null, null, null, null);

        var row = result.transactions().get(0);
        assertThat(row.businessReferenceType()).isEqualTo("VAULT_DEPOSIT");
        assertThat(row.businessReferenceId()).isEqualTo(VAULT_ID);
        assertThat(row.narrative()).isEqualTo("Vault deposit narrative");
    }

    @Test
    @DisplayName("row with no ledger_transaction_id (still PENDING) is not enriched, not an error")
    void pending_row_with_no_ledger_transaction_is_not_enriched() {
        when(txnRepo.findHistoryForUser(eq(USER_ID), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(historyRow("STSH-2", null)));

        var result = service.getHistory(USER_ID, null, null, null, null, null, null);

        var row = result.transactions().get(0);
        assertThat(row.businessReferenceType()).isNull();
        assertThat(row.businessReferenceId()).isNull();
        assertThat(row.narrative()).isNull();
        verify(ledgerService).getBusinessReferencesForTransactions(List.of());
    }

    @Test
    @DisplayName("row whose ledger_transaction_id has no matching business reference stays null")
    void row_with_unmatched_ledger_transaction_stays_null() {
        when(txnRepo.findHistoryForUser(eq(USER_ID), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.<Object[]>of(historyRow("STSH-3", LEDGER_TXN_NO_REF)));
        when(ledgerService.getBusinessReferencesForTransactions(List.of(LEDGER_TXN_NO_REF)))
                .thenReturn(List.of());

        var result = service.getHistory(USER_ID, null, null, null, null, null, null);

        var row = result.transactions().get(0);
        assertThat(row.businessReferenceType()).isNull();
        assertThat(row.narrative()).isNull();
    }

    private static Object[] historyRow(String reference, UUID ledgerTransactionId) {
        List<Object> row = new ArrayList<>();
        row.add(UUID.randomUUID());          // 0 id
        row.add(reference);                  // 1 reference
        row.add("DEPOSIT");                  // 2 transaction_type
        row.add(USER_ID);                    // 3 initiating_user_id
        row.add(null);                       // 4 counterparty_user_id
        row.add(20000L);                     // 5 gross_amount
        row.add(0L);                         // 6 fee_amount
        row.add(20000L);                     // 7 net_amount
        row.add("COMPLETED");                // 8 status
        row.add(ledgerTransactionId);         // 9 ledger_transaction_id
        row.add(NOW);                        // 10 created_at
        return row.toArray();
    }
}
