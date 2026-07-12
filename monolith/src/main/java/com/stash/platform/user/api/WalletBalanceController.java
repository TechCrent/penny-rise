package com.stash.platform.user.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.transfer.client.PeerTransferPaymentsClient;
import com.stash.platform.transfer.client.TransferPaymentsException;
import com.stash.platform.user.security.AuthenticatedUser;
import com.stash.shared.correlation.CorrelationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class WalletBalanceController {

    private final RestClient paymentsClient;
    private final PeerTransferPaymentsClient walletClient;

    public WalletBalanceController(
            RestClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}") String serviceToken,
            PeerTransferPaymentsClient walletClient) {
        this.paymentsClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .build();
        this.walletClient = walletClient;
    }

    @GetMapping("/wallet-balance")
    @SuppressWarnings("unchecked")
    public WalletBalanceResponse getWalletBalance(Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        UUID callerId = authUser.getUserId();

        // Delegate wallet resolution to PeerTransferPaymentsClient (WebClient) rather
        // than issuing our own provisioning POST here. Both this endpoint and the
        // transfer/deposit flows provision the SAME USER_WALLET account under the same
        // idempotency key ("provision-user-wallet:v2:" + userId) — when this class used
        // its own RestClient to build that request, RestClient and WebClient serialized
        // the identical Map body to different raw bytes, so the two code paths hashed
        // the same key differently and collided with 422 IDEMPOTENCY_KEY_REUSED.
        UUID accountId;
        try {
            accountId = walletClient.resolveUserWallet(callerId, CorrelationContext.get());
        } catch (TransferPaymentsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment processing temporarily unavailable. Please retry.");
        }

        Map<String, Object> balanceResp = (Map<String, Object>) paymentsClient.get()
                .uri("/api/v1/accounts/{id}/balance", accountId)
                .retrieve()
                .body(Map.class);

        long balancePesewas = ((Number) balanceResp.get("balance_pesewas")).longValue();
        return new WalletBalanceResponse(
                accountId,
                balancePesewas,
                formatCedis(balancePesewas)
        );
    }

    /**
     * Proxies the wallet's paginated statement from Payments Service.
     *
     * <p>Was entirely unreachable from the mobile app before this: the
     * client called {@code GET /api/v1/accounts/{accountId}/statement}
     * against the monolith (:8080), but that route only ever existed on
     * Payments Service (:8081) — every call 404'd, surfacing as "Could not
     * load transaction history" on the Wallet screen. Self-scoped (no
     * accountId path param) so the same trusted wallet-resolution as
     * {@link #getWalletBalance} is reused instead of trusting a
     * client-supplied account id — a generic {@code /accounts/{id}/statement}
     * proxy would need its own ownership check to stop one user reading
     * another's statement by guessing/enumerating account ids.
     */
    @GetMapping(value = "/wallet-statement", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public String getWalletStatement(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        UUID callerId = authUser.getUserId();

        UUID accountId;
        try {
            accountId = walletClient.resolveUserWallet(callerId, CorrelationContext.get());
        } catch (TransferPaymentsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment processing temporarily unavailable. Please retry.");
        }

        return paymentsClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/accounts/{id}/statement")
                        .queryParamIfPresent("cursor", java.util.Optional.ofNullable(cursor))
                        .queryParamIfPresent("limit", java.util.Optional.ofNullable(limit))
                        .build(accountId))
                .retrieve()
                .body(String.class);
    }

    private static String formatCedis(long pesewas) {
        return String.format("%.2f", pesewas / 100.0);
    }

    public record WalletBalanceResponse(
            @JsonProperty("account_id")      UUID accountId,
            @JsonProperty("balance_pesewas") long balancePesewas,
            @JsonProperty("balance_cedis")   String balanceCedis
    ) {}
}
