package com.stash.payments.ledger.api;

import com.stash.payments.ledger.api.dto.StatementResponse;
import com.stash.payments.ledger.service.StatementService;
import com.stash.payments.shared.security.CallerContext;
import com.stash.payments.shared.security.CallerContextFilter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    /**
     * @param cursor   opaque cursor from a previous response's {@code next_cursor}
     * @param limit    entries per page; max 50, default 20
     * @param fromDate ISO-8601 instant; inclusive lower bound
     * @param toDate   ISO-8601 instant; inclusive upper bound
     */
    @GetMapping("/{accountId}/statement")
    public StatementResponse getStatement(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant toDate,
            @RequestAttribute(CallerContextFilter.ATTR) CallerContext callerContext) {

        return statementService.getStatement(
                accountId, cursor, limit, fromDate, toDate, callerContext);
    }
}
