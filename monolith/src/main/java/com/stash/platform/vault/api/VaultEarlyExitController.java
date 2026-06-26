package com.stash.platform.vault.api;

import com.stash.platform.vault.api.dto.EarlyExitRequest;
import com.stash.platform.vault.api.dto.EarlyExitResponse;
import com.stash.platform.vault.service.VaultEarlyExitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vaults")
public class VaultEarlyExitController {

    private final VaultEarlyExitService earlyExitService;

    public VaultEarlyExitController(VaultEarlyExitService earlyExitService) {
        this.earlyExitService = earlyExitService;
    }

    @PostMapping("/{vaultId}/early-exit")
    @ResponseStatus(HttpStatus.CREATED)     // 201
    public EarlyExitResponse requestEarlyExit(
            @PathVariable UUID vaultId,
            @Valid @RequestBody EarlyExitRequest request,
            @AuthenticationPrincipal UUID userId,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        return earlyExitService.requestEarlyExit(
                vaultId, userId, request,
                correlationId != null ? correlationId : "early-exit-" + UUID.randomUUID());
    }
}
