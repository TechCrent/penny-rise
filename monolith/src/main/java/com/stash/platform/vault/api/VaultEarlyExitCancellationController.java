package com.stash.platform.vault.api;

import com.stash.platform.vault.service.VaultEarlyExitCancellationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vaults")
public class VaultEarlyExitCancellationController {

    private final VaultEarlyExitCancellationService cancellationService;

    public VaultEarlyExitCancellationController(
            VaultEarlyExitCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @DeleteMapping("/{vaultId}/early-exit")
    @ResponseStatus(HttpStatus.NO_CONTENT)      // 204
    public void cancelEarlyExit(
            @PathVariable UUID vaultId,
            @AuthenticationPrincipal UUID userId,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        cancellationService.cancelEarlyExit(
                vaultId, userId,
                correlationId != null ? correlationId : "cancel-exit-" + UUID.randomUUID());
    }
}
