package com.stash.payments.ledger.api;

import com.stash.payments.ledger.exception.AccountBalanceNotZeroException;
import com.stash.payments.ledger.service.AccountCloseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoint for closing ledger accounts — used by the monolith's
 * account-deletion saga (v0.5-019, step 3/4).
 *
 * <p>Protected by {@link com.stash.payments.shared.security.InternalServiceAuthFilter}.
 */
@RestController
@RequestMapping("/internal/v1/ledger")
public class LedgerAccountCloseController {

    private final AccountCloseService accountCloseService;

    public LedgerAccountCloseController(AccountCloseService accountCloseService) {
        this.accountCloseService = accountCloseService;
    }

    @PostMapping("/accounts/{accountId}/close")
    public ResponseEntity<Void> close(@PathVariable UUID accountId) {
        accountCloseService.close(accountId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AccountBalanceNotZeroException.class)
    public ResponseEntity<Map<String, Object>> handleNonZeroBalance(AccountBalanceNotZeroException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "code", "PAYMENTS_ACCOUNT_BALANCE_NOT_ZERO",
                        "message", ex.getMessage(),
                        "account_id", ex.getAccountId().toString(),
                        "balance_pesewas", ex.getBalance()
                ));
    }
}
