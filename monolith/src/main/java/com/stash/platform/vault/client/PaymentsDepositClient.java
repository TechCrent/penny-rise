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
 * Delegates deposit initiation to the Payments Service.
 *
 * <p>The monolith has already validated vault ownership and status.
 * This client passes the vault's {@code ledger_account_id} as the
 * destination and the authenticated user's ID as the initiating user.
 *
 * <p>Uses the internal service token — Payments trusts the monolith
 * to have authenticated the user and resolved the correct ledger account.
 */
@Component
public class PaymentsDepositClient {

    private static final Logger   log     = LoggerFactory.getLogger(PaymentsDepositClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public PaymentsDepositClient(
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
     * Initiates a deposit against a vault's ledger account.
     *
     * @return the deposit result from the Payments Service
     * @throws PaymentsServiceException on HTTP error or timeout
     */
    public DepositResult initiateDeposit(
            UUID   userId,
            String userEmail,
            UUID   ledgerAccountId,
            long   amountPesewas,
            String paymentMethod,
            String mobileNumber,
            String mobileProvider,
            UUID   vaultId,
            String correlationId,
            String idempotencyKey) {

        log.debug("PaymentsDepositClient: initiating deposit for vault={} amount={}p user={}",
                vaultId, amountPesewas, userId);

        Map<String, Object> body = buildBody(userId, userEmail, ledgerAccountId,
                amountPesewas, paymentMethod, mobileNumber, mobileProvider,
                vaultId, correlationId);
        try {
            Map<?, ?> response = webClient.post()
                    .uri("/api/v1/transactions/deposits")
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            return new DepositResult(
                    (String) response.get("transaction_reference"),
                    (String) response.get("authorisation_url"),
                    (String) response.get("paystack_reference"),
                    (String) response.get("status")
            );

        } catch (WebClientResponseException e) {
            throw new PaymentsServiceException(
                    "Payments Service returned " + e.getStatusCode().value() +
                    " on deposit initiation: " + e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new PaymentsServiceException(
                    "Payments Service unavailable during deposit: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY.value(), e);
        }
    }

    private Map<String, Object> buildBody(UUID userId, String userEmail,
                                           UUID ledgerAccountId, long amount,
                                           String paymentMethod, String mobileNumber,
                                           String mobileProvider, UUID vaultId,
                                           String correlationId) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("ledger_account_id",       ledgerAccountId.toString());
        body.put("user_id",                 userId.toString());
        body.put("amount",                  amount);
        body.put("payment_method",          paymentMethod);
        body.put("customer_email",          userEmail);
        body.put("correlation_id",          correlationId);
        body.put("business_reference_id",   vaultId.toString());
        body.put("business_reference_type", "VAULT_DEPOSIT");
        if (mobileNumber  != null) body.put("mobile_number",   mobileNumber);
        if (mobileProvider != null) body.put("mobile_provider", mobileProvider);
        return body;
    }

    public record DepositResult(
            String transactionReference,
            String authorisationUrl,
            String paystackReference,
            String status
    ) {}
}
