package com.stash.platform.vault.api;

import com.stash.platform.vault.api.dto.CreateVaultRequest;
import com.stash.platform.vault.api.dto.VaultListResponse;
import com.stash.platform.vault.api.dto.VaultResponse;
import com.stash.platform.vault.service.VaultCreationService;
import com.stash.platform.vault.service.VaultListService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vaults")
public class VaultController {

    private final VaultCreationService creationService;
    private final VaultListService     listService;

    public VaultController(VaultCreationService creationService,
                            VaultListService listService) {
        this.creationService = creationService;
        this.listService     = listService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VaultResponse createVault(
            @Valid @RequestBody CreateVaultRequest request,
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("Idempotency-Key")        String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id",
                           required = false)          String correlationId) {

        return creationService.createVault(
                userId, request,
                correlationId != null ? correlationId : "vault-create-" + UUID.randomUUID(),
                idempotencyKey);
    }

    @GetMapping
    public VaultListResponse listVaults(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(name = "include_closed", defaultValue = "false")
            boolean includeClosed,
            @RequestHeader(value = "X-Correlation-Id", required = false)
            String correlationId) {

        return listService.listVaults(
                userId, includeClosed,
                correlationId != null ? correlationId : "vault-list-" + UUID.randomUUID());
    }
}
