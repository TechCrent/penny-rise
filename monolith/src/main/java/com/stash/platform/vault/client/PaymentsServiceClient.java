package com.stash.platform.vault.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Internal HTTP client for the Payments Service.
 *
 * <p>Calls the internal Payments Service API using the shared service token.
 * All calls use the {@code /internal/v1/} prefix which is guarded by
 * {@code InternalServiceAuthFilter} on the Payments Service.
 *
 * <p>No circuit breaker here — the Payments Service is on the same Docker
 * network and failures are expected to be fast. A 5-second timeout prevents
 * hanging indefinitely on a crashed Payments Service.
 */
@Component
public class PaymentsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentsServiceClient.class);

    private final WebClient webClient;

    public PaymentsServiceClient(
            WebClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}") String serviceToken) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Provisions a VAULT ledger account in the Payments Service.
     *
     * @param ownerUserId   the user who owns the vault
     * @param vaultId       the vault UUID (used as owner_id on the ledger account)
     * @param correlationId trace ID for the request
     * @param idempotencyKey prevents duplicate provisioning on retry
     * @return the UUID of the provisioned ledger account
     * @throws PaymentsServiceException if the Payments Service returns an error
     */
    public UUID provisionVaultLedgerAccount(UUID ownerUserId, UUID vaultId,
                                             String correlationId, String idempotencyKey) {
        log.debug("PaymentsServiceClient: provisioning VAULT ledger account " +
                  "for vault={} user={}", vaultId, ownerUserId);

        try {
            // LinkedHashMap, not Map.of() — see docs/hands-on-testing-findings.md
            // Finding 9: a retry that lands after a monolith restart would hash
            // this body differently under Map.of()'s per-JVM-run randomized
            // iteration order, even though it's logically identical.
            Map<String, String> body = new LinkedHashMap<>();
            body.put("account_type", "VAULT");
            body.put("owner_type", "VAULT");
            body.put("owner_id", vaultId.toString());
            body.put("description", "Vault ledger account for vault " + vaultId);

            Map<?, ?> response = webClient.post()
                    .uri("/internal/v1/ledger/accounts")
                    .header("Idempotency-Key", idempotencyKey + ":ledger-provision")
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            String ledgerAccountId = (String) response.get("ledger_account_id");
            log.info("PaymentsServiceClient: VAULT ledger account provisioned: " +
                     "account={} vault={} correlation={}", ledgerAccountId, vaultId, correlationId);
            return UUID.fromString(ledgerAccountId);

        } catch (WebClientResponseException e) {
            throw new PaymentsServiceException(
                    "Payments Service returned " + e.getStatusCode() +
                    " when provisioning vault ledger account for vault " + vaultId,
                    e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new PaymentsServiceException(
                    "Payments Service call failed when provisioning vault ledger account: " +
                    e.getMessage(), 503, e);
        }
    }
}
