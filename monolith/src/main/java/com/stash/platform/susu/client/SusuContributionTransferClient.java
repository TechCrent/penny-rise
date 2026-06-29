package com.stash.platform.susu.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Executes the USER_WALLET → SUSU_POT transfer for susu contributions.
 *
 * <p>Uses the internal transfer endpoint (POST /internal/v1/transactions/transfers)
 * built in v0.3-020. The monolith passes the internal service token — the Payments
 * Service trusts the monolith to have validated membership and round status.
 *
 * <p>USER_WALLET account ID is resolved via the ledger account provision endpoint
 * (idempotent — returns existing account if already provisioned).
 */
@Component
public class SusuContributionTransferClient {

    private static final Logger   log     = LoggerFactory.getLogger(SusuContributionTransferClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public SusuContributionTransferClient(
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
     * Looks up (or provisions) the USER_WALLET ledger account for a user.
     * Idempotent — safe to call multiple times; always returns the same account ID.
     */
    public UUID resolveUserWallet(UUID userId, String correlationId) {
        try {
            Map<?, ?> resp = webClient.post()
                    .uri("/internal/v1/ledger/accounts")
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(Map.of(
                            "owner_type",   "USER",
                            "owner_id",     userId.toString(),
                            "account_type", "USER_WALLET",
                            "description",  "USER_WALLET for user: " + userId
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            return UUID.fromString((String) resp.get("ledger_account_id"));
        } catch (Exception e) {
            throw new SusuPaymentsException(
                    "Could not resolve USER_WALLET for user=" + userId + ": " + e.getMessage());
        }
    }

    /**
     * Transfers contribution_amount from the member's USER_WALLET to the group's SUSU_POT.
     *
     * @param sourceAccountId      the member's USER_WALLET ledger account
     * @param destinationAccountId the group's SUSU_POT ledger account
     * @param amountPesewas        the contribution amount
     * @param contributionId       the susu_contributions row ID (business reference)
     * @param correlationId        trace ID
     * @param idempotencyKey       forwarded to Payments for deduplication
     * @return the Payments Service transaction reference (e.g. STSH-202606-CON001)
     */
    public String transfer(UUID   sourceAccountId,
                           UUID   destinationAccountId,
                           long   amountPesewas,
                           UUID   contributionId,
                           String correlationId,
                           String idempotencyKey) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("source_account_id",       sourceAccountId.toString());
            body.put("destination_account_id",  destinationAccountId.toString());
            body.put("amount",                  amountPesewas);
            body.put("transaction_type",        "SUSU_CONTRIBUTION");
            body.put("business_reference_id",   contributionId.toString());
            body.put("business_reference_type", "SUSU_CONTRIBUTION");
            body.put("correlation_id",          correlationId);
            body.put("narrative",               "Susu contribution payment");

            Map<?, ?> resp = webClient.post()
                    .uri("/internal/v1/transactions/transfers")
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            return (String) resp.get("transaction_reference");

        } catch (WebClientResponseException e) {
            throw new SusuPaymentsException(
                    e.getStatusCode().value() + ":" + e.getResponseBodyAsString());
        } catch (SusuPaymentsException e) {
            throw e;
        } catch (Exception e) {
            throw new SusuPaymentsException(
                    "Payments Service unavailable during contribution transfer: " + e.getMessage());
        }
    }
}
