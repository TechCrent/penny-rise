package com.stash.platform.user.api;

import com.stash.platform.user.service.WalletDepositService;
import com.stash.platform.vault.api.dto.VaultDepositRequest;
import com.stash.platform.vault.api.dto.VaultDepositResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class WalletDepositController {

    private final WalletDepositService walletDepositService;

    public WalletDepositController(WalletDepositService walletDepositService) {
        this.walletDepositService = walletDepositService;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VaultDepositResponse deposit(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody VaultDepositRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        return walletDepositService.initiateDeposit(
                userId,
                request,
                correlationId != null ? correlationId : "wallet-deposit-" + UUID.randomUUID(),
                idempotencyKey);
    }
}
