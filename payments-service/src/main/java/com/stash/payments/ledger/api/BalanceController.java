package com.stash.payments.ledger.api;

import com.stash.payments.ledger.api.dto.BalanceResponse;
import com.stash.payments.ledger.service.AccountBalanceService;
import com.stash.payments.shared.security.CallerContext;
import com.stash.payments.shared.security.CallerContextFilter;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Account balance endpoint.
 *
 * <p>The {@link CallerContext} is populated as a request attribute by
 * {@link com.stash.payments.shared.security.CallerContextFilter} (Order 15)
 * before this controller is reached.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class BalanceController {

    private final AccountBalanceService accountBalanceService;

    public BalanceController(AccountBalanceService accountBalanceService) {
        this.accountBalanceService = accountBalanceService;
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(
            @PathVariable UUID accountId,
            @RequestAttribute(CallerContextFilter.ATTR) CallerContext callerContext) {
        return accountBalanceService.getBalance(accountId, callerContext);
    }
}
