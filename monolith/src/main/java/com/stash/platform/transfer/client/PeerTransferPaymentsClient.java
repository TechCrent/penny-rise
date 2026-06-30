package com.stash.platform.transfer.client;

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
 * Executes the two legs of a peer transfer against the Payments Service:
 * 1. sender USER_WALLET → recipient USER_WALLET (principal amount)
 * 2. sender USER_WALLET → FEE_REVENUE (fee, if applicable)
 *
 * Both legs use idempotency keys derived from the transfer ID to ensure
 * they are independently retryable without double-charging.
 */
@Component
public class PeerTransferPaymentsClient {

    private static final Logger   log     = LoggerFactory.getLogger(PeerTransferPaymentsClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final WebClient webClient;
    private final UUID      feeRevenueAccountId;

    public PeerTransferPaymentsClient(
            WebClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}")     String serviceToken,
            @Value("${stash.ledger.fee-revenue-account-id}") String feeRevenueAccountId) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.feeRevenueAccountId = UUID.fromString(feeRevenueAccountId);
    }

    /** Resolves (or provisions) a user's USER_WALLET ledger account ID. */
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
            throw new TransferPaymentsException(
                    "Cannot resolve USER_WALLET for user=" + userId + ": " + e.getMessage());
        }
    }

    /**
     * Executes the principal transfer: sender → recipient.
     *
     * @return the Payments Service transaction reference
     */
    public String transferPrincipal(UUID senderWalletId, UUID recipientWalletId,
                                     long amount, UUID transferId, String note,
                                     String correlationId, String idempotencyKey) {
        return internalTransfer(
                senderWalletId, recipientWalletId, amount,
                "PEER_TRANSFER", transferId, "PEER_TRANSFER",
                note != null ? note : "Peer transfer",
                correlationId, idempotencyKey);
    }

    /**
     * Executes the fee transfer: sender → FEE_REVENUE.
     * Only called when the sender has exhausted their free quota.
     */
    public String transferFee(UUID senderWalletId, long feeAmount, UUID transferId,
                               String correlationId, String idempotencyKey) {
        return internalTransfer(
                senderWalletId, feeRevenueAccountId, feeAmount,
                "PEER_TRANSFER_FEE", transferId, "PEER_TRANSFER_FEE",
                "Peer transfer fee",
                correlationId, idempotencyKey);
    }

    private String internalTransfer(UUID source, UUID destination, long amount,
                                     String transactionType, UUID businessRefId,
                                     String businessRefType, String narrative,
                                     String correlationId, String idempotencyKey) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("source_account_id",       source.toString());
            body.put("destination_account_id",  destination.toString());
            body.put("amount",                  amount);
            body.put("transaction_type",        transactionType);
            body.put("business_reference_id",   businessRefId.toString());
            body.put("business_reference_type", businessRefType);
            body.put("correlation_id",          correlationId);
            body.put("narrative",               narrative);

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
            throw new TransferPaymentsException(
                    e.getStatusCode().value() + ":" + e.getResponseBodyAsString());
        } catch (TransferPaymentsException e) {
            throw e;
        } catch (Exception e) {
            throw new TransferPaymentsException(
                    "Payments Service unavailable: " + e.getMessage());
        }
    }
}
