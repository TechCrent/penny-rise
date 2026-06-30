package com.stash.platform.user.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.user.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class WalletBalanceController {

    private final RestClient paymentsClient;

    public WalletBalanceController(
            RestClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}") String serviceToken) {
        this.paymentsClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .build();
    }

    @GetMapping("/wallet-balance")
    @SuppressWarnings("unchecked")
    public WalletBalanceResponse getWalletBalance(Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        UUID callerId = authUser.getUserId();

        Map<String, Object> accountResp = (Map<String, Object>) paymentsClient.post()
                .uri("/internal/v1/ledger/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "owner_type",   "USER",
                        "owner_id",     callerId.toString(),
                        "account_type", "USER_WALLET",
                        "description",  "USER_WALLET for user: " + callerId
                ))
                .retrieve()
                .body(Map.class);

        String accountId = (String) accountResp.get("ledger_account_id");

        Map<String, Object> balanceResp = (Map<String, Object>) paymentsClient.get()
                .uri("/api/v1/accounts/{id}/balance", accountId)
                .retrieve()
                .body(Map.class);

        long balancePesewas = ((Number) balanceResp.get("balance")).longValue();
        return new WalletBalanceResponse(
                UUID.fromString(accountId),
                balancePesewas,
                formatCedis(balancePesewas)
        );
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
