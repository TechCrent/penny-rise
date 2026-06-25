package com.stash.payments.transaction.api;

import com.stash.payments.ledger.exception.InsufficientBalanceException;
import com.stash.payments.transaction.api.dto.TransferRequest;
import com.stash.payments.transaction.api.dto.TransferResponse;
import com.stash.payments.transaction.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal ledger transfer endpoint.
 *
 * <p>Accessible only at {@code /internal/v1/transactions/transfers}.
 * The {@code /internal/**} prefix is:
 * <ol>
 *   <li>Not routed by the API gateway (nginx / Traefik config drops it).</li>
 *   <li>Protected by {@link com.stash.payments.shared.security.InternalServiceAuthFilter}
 *       which requires {@code X-Internal-Service-Token}.</li>
 * </ol>
 */
@RestController
@RequestMapping("/internal/v1/transactions")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return transferService.transfer(request, idempotencyKey);
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
