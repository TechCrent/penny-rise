package com.stash.payments.transaction.api;

import com.stash.payments.transaction.api.dto.DepositInitiateRequest;
import com.stash.payments.transaction.api.dto.DepositInitiateResponse;
import com.stash.payments.transaction.service.DepositService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Internal deposit endpoint — called by the monolith, not directly by mobile.
 *
 * <p>The monolith's vault module calls this after its own validation:
 * <ul>
 *   <li>JWT verified + user authenticated (monolith)</li>
 *   <li>Vault belongs to user (monolith)</li>
 *   <li>Vault is ACTIVE (monolith)</li>
 *   <li>Vault → ledger_account_id resolved (monolith)</li>
 * </ul>
 *
 * <p>The Payments Service adds its own validation layer on top.
 * Both layers validate independently — the Payments Service does not
 * blindly trust the monolith.
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)   // 202
    public DepositInitiateResponse initiateDeposit(
            @Valid @RequestBody DepositInitiateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        return depositService.initiateDeposit(request, idempotencyKey);
    }
}
