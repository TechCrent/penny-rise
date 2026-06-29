package com.stash.platform.transfer.api;

import com.stash.platform.transfer.api.dto.CreateTransferRequest;
import com.stash.platform.transfer.api.dto.CreateTransferResponse;
import com.stash.platform.transfer.service.PeerTransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class PeerTransferController {

    private final PeerTransferService transferService;

    public PeerTransferController(PeerTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTransferResponse createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @AuthenticationPrincipal UUID senderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        return transferService.transfer(
                senderId, request,
                correlationId != null ? correlationId : "transfer-" + UUID.randomUUID(),
                idempotencyKey);
    }
}
