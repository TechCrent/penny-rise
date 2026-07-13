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
 * Delegates deposit initiation / OTP completion to the Payments Service.
 */
@Component
public class PaymentsDepositClient {

    private static final Logger   log     = LoggerFactory.getLogger(PaymentsDepositClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

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
                vaultId, "VAULT_DEPOSIT", correlationId);
        return postDeposit("/api/v1/transactions/deposits", body, idempotencyKey, correlationId);
    }

    public DepositResult initiateWalletDeposit(
            UUID   userId,
            String userEmail,
            UUID   walletAccountId,
            long   amountPesewas,
            String paymentMethod,
            String mobileNumber,
            String mobileProvider,
            String correlationId,
            String idempotencyKey) {

        log.debug("PaymentsDepositClient: initiating wallet deposit amount={}p user={}",
                amountPesewas, userId);

        Map<String, Object> body = buildBody(userId, userEmail, walletAccountId,
                amountPesewas, paymentMethod, mobileNumber, mobileProvider,
                userId, "WALLET_DEPOSIT", correlationId);
        return postDeposit("/api/v1/transactions/deposits", body, idempotencyKey, correlationId);
    }

    public DepositResult completeDepositOtp(
            UUID   userId,
            String transactionReference,
            String otpCode,
            String mobileNumber,
            String mobileProvider,
            String correlationId,
            String idempotencyKey) {

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId.toString());
        body.put("otp_code", otpCode);
        body.put("mobile_number", mobileNumber);
        body.put("mobile_provider", mobileProvider);
        body.put("correlation_id", correlationId);

        return postDeposit(
                "/api/v1/transactions/deposits/" + transactionReference + "/otp",
                body, idempotencyKey, correlationId);
    }

    private DepositResult postDeposit(String uri,
                                      Map<String, Object> body,
                                      String idempotencyKey,
                                      String correlationId) {
        try {
            Map<?, ?> response = webClient.post()
                    .uri(uri)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            boolean otpRequired = Boolean.TRUE.equals(response.get("otp_required"));
            Object otpFlag = response.get("otp_required");
            if (otpFlag instanceof Boolean b) {
                otpRequired = b;
            }

            return new DepositResult(
                    (String) response.get("transaction_reference"),
                    (String) response.get("authorisation_url"),
                    firstNonNull(
                            (String) response.get("provider_reference"),
                            (String) response.get("paystack_reference")),
                    (String) response.get("status"),
                    otpRequired
            );

        } catch (WebClientResponseException e) {
            throw new PaymentsServiceException(
                    "Payments Service returned " + e.getStatusCode().value() +
                    " on deposit: " + e.getResponseBodyAsString(),
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
                                           String mobileProvider, UUID businessRefId,
                                           String businessRefType,
                                           String correlationId) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("ledger_account_id",       ledgerAccountId.toString());
        body.put("user_id",                 userId.toString());
        body.put("amount",                  amount);
        body.put("payment_method",          paymentMethod);
        body.put("customer_email",          userEmail);
        body.put("correlation_id",          correlationId);
        body.put("business_reference_id",   businessRefId.toString());
        body.put("business_reference_type", businessRefType);
        if (mobileNumber  != null) body.put("mobile_number",   mobileNumber);
        if (mobileProvider != null) body.put("mobile_provider", mobileProvider);
        return body;
    }

    public record DepositResult(
            String transactionReference,
            String authorisationUrl,
            String providerReference,
            String status,
            boolean otpRequired
    ) {}

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
