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
import java.util.LinkedHashMap;
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

    private static String userWalletProvisionKey(UUID userId) {
        return "provision-user-wallet:v2:" + userId;
    }

    private static Map<String, String> userWalletProvisionBody(UUID userId) {
        // LinkedHashMap, not Map.of() — see docs/hands-on-testing-findings.md
        // Finding 9: Map.of()'s iteration order is randomized per JVM run, so
        // the same logical body serializes to different JSON key ordering
        // across a monolith restart, breaking this key's idempotency hash
        // (shared with SusuContributionTransferClient's identical key/body)
        // for every user whose wallet was already provisioned before the
        // restart.
        Map<String, String> body = new LinkedHashMap<>();
        body.put("owner_type", "USER");
        body.put("owner_id", userId.toString());
        body.put("account_type", "USER_WALLET");
        body.put("description", "USER_WALLET:" + userId);
        return body;
    }

    /** Resolves (or provisions) a user's USER_WALLET ledger account ID. */
    public UUID resolveUserWallet(UUID userId, String correlationId) {
        try {
            Map<?, ?> resp = webClient.post()
                    .uri("/internal/v1/ledger/accounts")
                    .header("Idempotency-Key", userWalletProvisionKey(userId))
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(userWalletProvisionBody(userId))
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
    public TransferLegResult transferPrincipal(UUID senderWalletId, UUID recipientWalletId,
                                     long amount, UUID transferId, String note,
                                     String correlationId, String idempotencyKey) {
        return internalTransfer(
                senderWalletId, recipientWalletId, amount,
                "TRANSFER", transferId, "PEER_TRANSFER",
                note != null ? note : "Peer transfer",
                correlationId, idempotencyKey);
    }

    /**
     * Executes the fee transfer: sender → FEE_REVENUE.
     * Only called when the sender has exhausted their free quota.
     */
    public TransferLegResult transferFee(UUID senderWalletId, long feeAmount, UUID transferId,
                               String correlationId, String idempotencyKey) {
        return internalTransfer(
                senderWalletId, feeRevenueAccountId, feeAmount,
                "FEE_COLLECTION", transferId, "PEER_TRANSFER",
                "Peer transfer fee",
                correlationId, idempotencyKey);
    }

    private TransferLegResult internalTransfer(UUID source, UUID destination, long amount,
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

            String txnRef = (String) resp.get("transaction_reference");
            Object ledgerTxnIdRaw = resp.get("ledger_transaction_id");
            UUID ledgerTxnId = ledgerTxnIdRaw instanceof UUID u
                    ? u
                    : UUID.fromString(String.valueOf(ledgerTxnIdRaw));
            return new TransferLegResult(txnRef, ledgerTxnId);

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
