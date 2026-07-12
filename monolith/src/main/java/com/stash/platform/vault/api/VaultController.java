package com.stash.platform.vault.api;

import com.stash.platform.vault.api.dto.CreateVaultRequest;
import com.stash.platform.vault.api.dto.VaultDepositRequest;
import com.stash.platform.vault.api.dto.VaultDepositResponse;
import com.stash.platform.vault.api.dto.VaultListResponse;
import com.stash.platform.vault.api.dto.VaultResponse;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import com.stash.platform.vault.service.VaultCreationService;
import com.stash.platform.vault.service.VaultDepositService;
import com.stash.platform.vault.service.VaultListService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vaults")
public class VaultController {

    private final VaultCreationService creationService;
    private final VaultListService     listService;
    private final VaultDepositService  depositService;
    private final VaultRepository      vaultRepo;
    private final RestClient           paymentsClient;

    public VaultController(VaultCreationService creationService,
                            VaultListService listService,
                            VaultDepositService depositService,
                            VaultRepository vaultRepo,
                            RestClient.Builder restClientBuilder,
                            @Value("${stash.payments.internal-base-url}") String paymentsBaseUrl,
                            @Value("${stash.internal.service-token}") String serviceToken) {
        this.creationService = creationService;
        this.listService     = listService;
        this.depositService  = depositService;
        this.vaultRepo       = vaultRepo;
        this.paymentsClient  = restClientBuilder
                .baseUrl(paymentsBaseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .build();
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

    @PostMapping("/{vaultId}/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VaultDepositResponse deposit(
            @PathVariable UUID vaultId,
            @Valid @RequestBody VaultDepositRequest request,
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false)
            String correlationId) {

        return depositService.initiateDeposit(
                vaultId, userId, request,
                correlationId != null ? correlationId : "vault-deposit-" + UUID.randomUUID(),
                idempotencyKey);
    }

    /**
     * Proxies a vault's paginated statement from Payments Service.
     *
     * <p>Was entirely unreachable from the mobile app before this: the
     * client called {@code GET /api/v1/accounts/{ledgerAccountId}/statement}
     * directly against the monolith (:8080), but that route only ever
     * existed on Payments Service (:8081) — every call 404'd, and the retry
     * loop this triggered surfaced as "takes a while to load" on the Vault
     * screen. Scoped by vaultId (not a raw ledger account id) so ownership
     * can be verified here before proxying — mirrors the reasoning in
     * {@link com.stash.platform.user.api.WalletBalanceController#getWalletStatement}.
     */
    @GetMapping(value = "/{vaultId}/statement", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getVaultStatement(
            @PathVariable UUID vaultId,
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        VaultEntity vault = vaultRepo.findByIdAndOwnerUserIdAndDeletedAtIsNull(vaultId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vault not found: " + vaultId));

        return paymentsClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/accounts/{id}/statement")
                        .queryParamIfPresent("cursor", Optional.ofNullable(cursor))
                        .queryParamIfPresent("limit", Optional.ofNullable(limit))
                        .build(vault.getLedgerAccountId()))
                .retrieve()
                .body(String.class);
    }
}
