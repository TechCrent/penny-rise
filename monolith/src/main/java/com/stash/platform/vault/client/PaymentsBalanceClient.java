package com.stash.platform.vault.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Fetches ledger account balances from the Payments Service.
 *
 * <p>Called by the vault list endpoint to enrich each vault with its current balance.
 * Uses the internal service token so the Payments Service bypasses ownership checks —
 * the monolith has already verified ownership.
 *
 * <p>Every call is independently failure-tolerant: a failed balance fetch returns
 * {@link Optional#empty()} rather than throwing, allowing the vault list to return
 * with {@code balance = null} for the affected vault rather than failing the entire request.
 */
@Component
public class PaymentsBalanceClient {

    private static final Logger   log     = LoggerFactory.getLogger(PaymentsBalanceClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;

    public PaymentsBalanceClient(
            WebClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}")     String serviceToken) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Fetches the balance for one ledger account.
     *
     * @param ledgerAccountId the UUID of the ledger account
     * @param correlationId   trace ID for logging
     * @return balance in pesewas, or empty if the call failed or timed out
     */
    public Optional<Long> fetchBalance(UUID ledgerAccountId, String correlationId) {
        try {
            BalancePayload payload = webClient.get()
                    .uri("/api/v1/accounts/{id}/balance", ledgerAccountId)
                    .header("X-Correlation-Id", correlationId)
                    .retrieve()
                    .bodyToMono(BalancePayload.class)
                    .timeout(TIMEOUT)
                    .block();

            if (payload == null) return Optional.empty();
            return Optional.of(payload.balance_pesewas());

        } catch (WebClientResponseException.NotFound e) {
            log.warn("Balance 404 for ledger account={} — provisioning may be in progress. " +
                     "correlation={}", ledgerAccountId, correlationId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Balance fetch failed for ledger account={} error={} correlation={}",
                     ledgerAccountId, e.getMessage(), correlationId);
            return Optional.empty();
        }
    }

    record BalancePayload(
            String account_id,
            String account_type,
            long   balance_pesewas,
            String balance_cedis,
            String status,
            String as_of
    ) {}
}
