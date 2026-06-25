package com.stash.payments.transaction.api;

import com.stash.payments.ledger.exception.InsufficientBalanceException;
import com.stash.payments.transaction.api.dto.WithdrawalInitiateRequest;
import com.stash.payments.transaction.api.dto.WithdrawalInitiateResponse;
import com.stash.payments.transaction.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WithdrawalInitiateResponse initiateWithdrawal(
            @Valid @RequestBody WithdrawalInitiateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return withdrawalService.initiateWithdrawal(request, idempotencyKey);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(
            InsufficientBalanceException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "code",              "PAYMENTS_INSUFFICIENT_BALANCE",
                        "message",           ex.getMessage(),
                        "available_pesewas", ex.getAvailable(),
                        "requested_pesewas", ex.getRequested()
                ));
    }
}
