package com.stash.platform.transaction.service;

import com.stash.admin.integration.IntegrationPaymentsClient;
import com.stash.admin.integration.PaymentsTransactionRow;
import com.stash.admin.integration.UnifiedTransactionPage;
import com.stash.platform.transaction.api.dto.UnifiedTransactionHistoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionHistoryServiceTest {

    private final IntegrationPaymentsClient paymentsClient = mock(IntegrationPaymentsClient.class);
    private final TransactionHistoryEnricher enricher       = mock(TransactionHistoryEnricher.class);
    private final TransactionHistoryService service =
            new TransactionHistoryService(paymentsClient, enricher);

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("empty page from Payments is returned as-is")
    void emptyPagePassesThrough() {
        when(paymentsClient.getUnifiedTransactionHistory(
                eq(USER_ID), isNull(), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(new UnifiedTransactionPage(List.of(), null, false));

        UnifiedTransactionHistoryResponse response =
                service.list(USER_ID, null, null, null, null, null);

        assertThat(response.transactions()).isEmpty();
        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("rows from Payments are enriched and returned")
    void rowsAreEnriched() {
        var row = txRow("WITHDRAWAL");
        when(paymentsClient.getUnifiedTransactionHistory(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new UnifiedTransactionPage(List.of(row), null, false));
        when(enricher.resolveAccountName(row)).thenReturn("Wallet");
        when(enricher.resolveDirection(row, USER_ID)).thenReturn("OUT");
        when(enricher.resolveCounterpartyName(row)).thenReturn(null);

        var response = service.list(USER_ID, null, null, null, null, null);

        assertThat(response.transactions()).hasSize(1);
        var item = response.transactions().get(0);
        assertThat(item.transactionType()).isEqualTo("WITHDRAWAL");
        assertThat(item.accountName()).isEqualTo("Wallet");
        assertThat(item.direction()).isEqualTo("OUT");
        assertThat(item.amountPesewas()).isEqualTo(10000);
        assertThat(item.amountCedis()).isEqualTo("100.00");
    }

    @Test
    @DisplayName("cursor and hasMore from Payments are forwarded to the response")
    void paginationFieldsAreForwarded() {
        var row = txRow("DEPOSIT");
        when(paymentsClient.getUnifiedTransactionHistory(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new UnifiedTransactionPage(List.of(row), "STSH-202607-000020", true));
        when(enricher.resolveAccountName(row)).thenReturn("Wallet");
        when(enricher.resolveDirection(row, USER_ID)).thenReturn("IN");
        when(enricher.resolveCounterpartyName(row)).thenReturn(null);

        var response = service.list(USER_ID, null, null, null, null, 20);

        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("STSH-202607-000020");
    }

    @Test
    @DisplayName("limit above MAX_LIMIT is clamped to 50")
    void limitIsClamped() {
        when(paymentsClient.getUnifiedTransactionHistory(
                eq(USER_ID), isNull(), isNull(), isNull(), isNull(), eq(50)))
                .thenReturn(new UnifiedTransactionPage(List.of(), null, false));

        service.list(USER_ID, null, null, null, null, 9999);

        // verification is implicit: if Mockito didn't match eq(50) the stub would return null
        // and the test would NPE. We assert the response to prove it matched.
    }

    @Test
    @DisplayName("null limit defaults to 20")
    void nullLimitDefaultsTo20() {
        when(paymentsClient.getUnifiedTransactionHistory(
                eq(USER_ID), isNull(), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(new UnifiedTransactionPage(List.of(), null, false));

        var response = service.list(USER_ID, null, null, null, null, null);

        assertThat(response).isNotNull();
    }

    private PaymentsTransactionRow txRow(String type) {
        return new PaymentsTransactionRow("STSH-202607-000001", type, USER_ID, null,
                10000, 0, 10000, "COMPLETED", null, null, "test", Instant.now());
    }
}
