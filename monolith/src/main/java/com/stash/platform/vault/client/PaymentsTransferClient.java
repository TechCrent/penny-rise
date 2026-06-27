package com.stash.platform.vault.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Delegates internal ledger transfers to the Payments Service.
 *
 * <p>Used by the early-exit release worker to transfer the penalty amount
 * from the vault's ledger account to the FEE_REVENUE system account before
 * initiating the MoMo payout.
 */
@Component
public class PaymentsTransferClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentsTransferClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public PaymentsTransferClient(
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
     * Transfers amount from one ledger account to another within the Payments Service.
     *
     * @return the transaction reference for the transfer
     */
    public String transfer(UUID sourceAccountId,
                           UUID destinationAccountId,
                           long amountPesewas,
                           String transactionType,
                           String narrative,
                           UUID   businessReferenceId,
                           String correlationId,
                           String idempotencyKey) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("source_account_id",      sourceAccountId.toString());
            body.put("destination_account_id", destinationAccountId.toString());
            body.put("amount",                 amountPesewas);
            body.put("transaction_type",       transactionType);
            body.put("business_reference_id",  businessReferenceId.toString());
            body.put("business_reference_type","VAULT_EARLY_EXIT_PENALTY");
            body.put("correlation_id",         correlationId);
            body.put("narrative",              narrative);

            Map<?, ?> response = webClient.post()
                    .uri("/internal/v1/transactions/transfers")
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            return (String) response.get("transaction_reference");

        } catch (WebClientResponseException e) {
            throw new PaymentsServiceException(
                    "Transfer failed: " + e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new PaymentsServiceException(
                    "Payments Service unavailable during transfer: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY.value(), e);
        }
    }
}
