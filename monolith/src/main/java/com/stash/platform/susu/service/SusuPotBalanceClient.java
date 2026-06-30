package com.stash.platform.susu.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class SusuPotBalanceClient {

    private static final Logger   log     = LoggerFactory.getLogger(SusuPotBalanceClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final WebClient webClient;

    public SusuPotBalanceClient(
            WebClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}")     String serviceToken) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public long getBalance(UUID ledgerAccountId) {
        try {
            Map<?, ?> resp = webClient.get()
                    .uri("/api/v1/accounts/{id}/balance", ledgerAccountId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            Number balance = (Number) resp.get("balance");
            if (balance == null) {
                throw new IllegalStateException(
                        "Payments Service returned null balance for account=" + ledgerAccountId);
            }
            return balance.longValue();

        } catch (Exception e) {
            log.error("SusuPotBalanceClient: failed to fetch balance for account={}: {}",
                    ledgerAccountId, e.getMessage());
            throw new SusuIntegrityCheckException(
                    "Cannot read SUSU_POT balance for account=" + ledgerAccountId +
                    ": " + e.getMessage());
        }
    }
}
