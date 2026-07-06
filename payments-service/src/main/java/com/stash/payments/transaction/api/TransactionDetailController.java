package com.stash.payments.transaction.api;

import com.stash.payments.shared.security.CallerContext;
import com.stash.payments.shared.security.CallerContextFilter;
import com.stash.payments.transaction.api.dto.TransactionDetailResponse;
import com.stash.payments.transaction.api.dto.UnifiedTransactionHistoryResponse;
import com.stash.payments.transaction.service.TransactionDetailService;
import com.stash.payments.transaction.service.TransactionHistoryQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionDetailController {

    private final TransactionDetailService transactionDetailService;
    private final TransactionHistoryQueryService transactionHistoryQueryService;

    public TransactionDetailController(TransactionDetailService transactionDetailService,
                                       TransactionHistoryQueryService transactionHistoryQueryService) {
        this.transactionDetailService = transactionDetailService;
        this.transactionHistoryQueryService = transactionHistoryQueryService;
    }

    @GetMapping("/{reference}")
    public TransactionDetailResponse getTransaction(
            @PathVariable String reference,
            @RequestAttribute(CallerContextFilter.ATTR) CallerContext callerContext) {

        return transactionDetailService.getDetail(reference, callerContext);
    }

    /**
     * Internal-only unified transaction history — see
     * docs/hands-on-testing-findings.md Finding 8. Called by monolith's
     * {@code IntegrationPaymentsClient} on behalf of an arbitrary end user,
     * so it requires internal-service auth rather than a user JWT.
     */
    @GetMapping
    public UnifiedTransactionHistoryResponse getHistory(
            @RequestParam("user_id") UUID userId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "from_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(value = "to_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestAttribute(CallerContextFilter.ATTR) CallerContext callerContext) {

        if (!callerContext.isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This endpoint requires internal-service authentication");
        }

        return transactionHistoryQueryService.getHistory(userId, type, fromDate, toDate, cursor, limit);
    }
}
