package com.stash.platform.transaction.api.dto;

import java.util.List;

public record UnifiedTransactionHistoryResponse(
        List<UnifiedTransactionItem> transactions,
        String nextCursor,
        boolean hasMore
) {}
