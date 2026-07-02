package com.stash.platform.transaction.api;

import com.stash.platform.transaction.api.dto.UnifiedTransactionHistoryResponse;
import com.stash.platform.transaction.service.TransactionHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/transactions")
public class TransactionHistoryController {

    private final TransactionHistoryService historyService;

    public TransactionHistoryController(TransactionHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public UnifiedTransactionHistoryResponse list(
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal UUID userId) {
        return historyService.list(userId, transactionType, fromDate, toDate, cursor, limit);
    }
}
