package com.stash.platform.vault.api;

import com.stash.platform.vault.api.dto.VaultWithdrawalRequest;
import com.stash.platform.vault.api.dto.VaultWithdrawalResponse;
import com.stash.platform.vault.service.VaultWithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vaults")
public class VaultWithdrawalController {

    private final VaultWithdrawalService withdrawalService;

    public VaultWithdrawalController(VaultWithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping("/{vaultId}/withdrawals")
    @ResponseStatus(HttpStatus.ACCEPTED)    // 202
    public VaultWithdrawalResponse withdraw(
            @PathVariable UUID vaultId,
            @Valid @RequestBody VaultWithdrawalRequest request,
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        return withdrawalService.initiateWithdrawal(
                vaultId, userId, request,
                correlationId != null ? correlationId : "vault-withdrawal-" + UUID.randomUUID(),
                idempotencyKey);
    }
}
