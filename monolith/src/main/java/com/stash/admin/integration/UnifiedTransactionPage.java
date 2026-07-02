package com.stash.admin.integration;

import java.util.List;

/**
 * Cursor-paginated page of transaction rows from Payments Service's unified
 * transaction endpoint (v0.5-020).
 *
 * <p>Cursor-based pagination: nextCursor is the reference value to pass as the
 * {@code cursor} query parameter on the next request. hasMore indicates whether
 * there are further rows beyond this page.
 */
public record UnifiedTransactionPage(
        List<PaymentsTransactionRow> transactions,
        String nextCursor,
        boolean hasMore
) {}
