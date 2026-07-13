package com.stash.payments.transaction.api;

import com.stash.payments.transaction.api.dto.DepositInitiateRequest;
import com.stash.payments.transaction.api.dto.DepositInitiateResponse;
import com.stash.payments.transaction.api.dto.DepositOtpRequest;
import com.stash.payments.transaction.service.DepositService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Internal deposit endpoint — called by the monolith, not directly by mobile.
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DepositInitiateResponse initiateDeposit(
            @Valid @RequestBody DepositInitiateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        return depositService.initiateDeposit(request, idempotencyKey);
    }

    @PostMapping("/deposits/{reference}/otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DepositInitiateResponse completeDepositOtp(
            @PathVariable String reference,
            @Valid @RequestBody DepositOtpRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // Idempotency-Key is enforced by IdempotencyFilter; kept on signature for clarity.
        return depositService.completeDepositOtp(reference, request);
    }
}
