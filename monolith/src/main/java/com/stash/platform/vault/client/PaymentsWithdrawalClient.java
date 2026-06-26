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
 * Delegates withdrawal initiation to the Payments Service.
 *
 * <p>The monolith has already validated vault ownership, vault type
 * (STANDARD only), and vault status. This client passes the vault's
 * {@code ledger_account_id} as the source account.
 *
 * <p>Uses the internal service token — Payments trusts the monolith
 * to have enforced business rules before calling.
 */
@Component
public class PaymentsWithdrawalClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentsWithdrawalClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;

    public PaymentsWithdrawalClient(
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
     * Initiates a withdrawal from a vault's ledger account.
     *
     * @param userId               the initiating user
     * @param userEmail            used by Paystack for recipient metadata
     * @param userFullName         used by Paystack as the recipient name
     * @param ledgerAccountId      the vault's ledger account (source of funds)
     * @param amountPesewas        amount to withdraw
     * @param destinationMomoNumber the MoMo number to send funds to
     * @param momoProvider         mtn, vodafone, or airteltigo
     * @param vaultId              business reference for the transaction
     * @param correlationId        trace ID
     * @param idempotencyKey       forwarded to Payments for deduplication
     * @return the withdrawal result
     * @throws PaymentsServiceException on HTTP error or timeout
     */
    public WithdrawalResult initiateWithdrawal(
            UUID   userId,
            String userEmail,
            String userFullName,
            UUID   ledgerAccountId,
            long   amountPesewas,
            String destinationMomoNumber,
            String momoProvider,
            UUID   vaultId,
            String correlationId,
            String idempotencyKey) {

        log.debug("PaymentsWithdrawalClient: initiating withdrawal vault={} amount={}p user={}",
                vaultId, amountPesewas, userId);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("ledger_account_id",       ledgerAccountId.toString());
            body.put("user_id",                 userId.toString());
            body.put("amount",                  amountPesewas);
            body.put("destination_momo_number", destinationMomoNumber);
            body.put("momo_provider",           momoProvider);
            body.put("recipient_name",          userFullName);
            body.put("customer_email",          userEmail);
            body.put("correlation_id",          correlationId);
            body.put("business_reference_id",   vaultId.toString());
            body.put("business_reference_type", "VAULT_WITHDRAWAL");

            Map<?, ?> response = webClient.post()
                    .uri("/api/v1/transactions/withdrawals")
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            return new WithdrawalResult(
                    (String) response.get("transaction_reference"),
                    (String) response.get("paystack_transfer_code"),
                    (String) response.get("status")
            );

        } catch (WebClientResponseException e) {
            throw new PaymentsServiceException(
                    e.getResponseBodyAsString(),
                    e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new PaymentsServiceException(
                    "Payments Service unavailable during withdrawal: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY.value(), e);
        }
    }

    public record WithdrawalResult(
            String transactionReference,
            String paystackTransferCode,
            String status
    ) {}
}
