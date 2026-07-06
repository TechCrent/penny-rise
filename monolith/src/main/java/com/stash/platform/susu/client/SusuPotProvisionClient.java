package com.stash.platform.susu.client;

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
 * Provisions a SUSU_POT ledger account in the Payments Service at group activation.
 *
 * <p>Uses the internal service token — the same pattern as vault ledger account
 * provisioning from v0.3-027. The Payments Service's internal provision endpoint
 * is idempotent on (owner_type, owner_id, account_type), so retrying with the
 * same group ID is safe.
 */
@Component
public class SusuPotProvisionClient {

    private static final Logger   log     = LoggerFactory.getLogger(SusuPotProvisionClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public SusuPotProvisionClient(
            WebClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}")     String serviceToken) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Provisions a SUSU_POT ledger account for a group.
     *
     * @param groupId       the susu group's UUID — used as owner_id
     * @param groupName     human-readable label for the account
     * @param correlationId trace ID
     * @return the UUID of the newly-provisioned (or already-existing) ledger account
     * @throws SusuPaymentsException if the Payments Service call fails
     */
    public UUID provisionSusuPot(UUID groupId, String groupName, String correlationId) {
        log.debug("Provisioning SUSU_POT for group={} correlation={}", groupId, correlationId);
        try {
            // LinkedHashMap, not Map.of() — see docs/hands-on-testing-findings.md
            // Finding 9: Map.of()'s iteration order is randomized per JVM run, so
            // the same logical body serializes to different JSON key ordering
            // across a monolith restart, breaking this fixed, reused-by-design
            // idempotency key's hash for any group whose pot was already
            // provisioned before the restart.
            Map<String, String> body = new LinkedHashMap<>();
            body.put("owner_type", "SUSU_GROUP");
            body.put("owner_id", groupId.toString());
            body.put("account_type", "SUSU_POT");
            body.put("description", "SUSU_POT for group: " + groupName);

            Map<?, ?> response = webClient.post()
                    .uri("/internal/v1/ledger/accounts")
                    .header("Idempotency-Key", "provision-susu-pot:" + groupId)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .timeout(TIMEOUT)
                    .block();

            String accountId = (String) response.get("ledger_account_id");
            if (accountId == null) {
                throw new SusuPaymentsException(
                        "Payments Service returned no ledger_account_id for SUSU_POT provision");
            }
            log.info("SUSU_POT provisioned: group={} ledgerAccountId={} correlation={}",
                    groupId, accountId, correlationId);
            return UUID.fromString(accountId);

        } catch (WebClientResponseException e) {
            throw new SusuPaymentsException(
                    "Payments Service error provisioning SUSU_POT for group=" + groupId +
                    ": " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        } catch (SusuPaymentsException e) {
            throw e;
        } catch (Exception e) {
            throw new SusuPaymentsException(
                    "Payments Service unavailable during SUSU_POT provision for group=" +
                    groupId + ": " + e.getMessage());
        }
    }
}