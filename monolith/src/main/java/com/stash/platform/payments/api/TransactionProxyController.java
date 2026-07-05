package com.stash.platform.payments.api;

import com.stash.platform.payments.api.dto.MobileTransactionDetailResponse;
import com.stash.platform.payments.service.TransactionQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionProxyController {

    private final TransactionQueryService transactionQueryService;

    public TransactionProxyController(TransactionQueryService transactionQueryService) {
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping("/{reference}")
    public MobileTransactionDetailResponse getTransaction(
            @PathVariable String reference,
            @AuthenticationPrincipal UUID userId) {
        return transactionQueryService.getForUser(reference, userId);
    }
}
