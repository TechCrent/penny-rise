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
import java.util.LinkedHashMap;
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
            // LinkedHashMap, not Map.of() — see docs/hands-on-testing-findings.md
            // Finding 9: Map.of()'s iteration order is randomized per JVM run, so
            // the same logical body serializes to different JSON key ordering
            // across a monolith restart, breaking this key's idempotency hash
            // (shared with PeerTransferPaymentsClient's identical key/body) for
            // every user whose wallet was already provisioned before the restart.
            Map<String, String> body = new LinkedHashMap<>();
            body.put("owner_type", "USER");
            body.put("owner_id", userId.toString());
            body.put("account_type", "USER_WALLET");
            body.put("description", "USER_WALLET:" + userId);

            Map<?, ?> resp = webClient.post()
                    .uri("/internal/v1/ledger/accounts")
                    .header("Idempotency-Key", "provision-user-wallet:v2:" + userId)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
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

    /**
     * Transfers the full pot from SUSU_POT to the recipient's USER_WALLET.
     * Same endpoint as {@link #transfer}, different transaction_type — disbursement
     * is semantically distinct from a member's contribution payment.
     *
     * @return the transfer result, including the real ledger_transaction_id from
     *         the Payments Service response (not a generated placeholder)
     */
    public DisbursementResult disburse(UUID   susuPotAccountId,
                                        UUID   recipientWalletId,
                                        long   potAmountPesewas,
                                        UUID   roundId,
                                        String correlationId,
                                        String idempotencyKey) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("source_account_id",       susuPotAccountId.toString());
            body.put("destination_account_id",  recipientWalletId.toString());
            body.put("amount",                  potAmountPesewas);
            body.put("transaction_type",        "SUSU_DISBURSEMENT");
            body.put("business_reference_id",   roundId.toString());
            body.put("business_reference_type", "SUSU_ROUND");
            body.put("correlation_id",          correlationId);
            body.put("narrative",               "Susu pot disbursement");

            Map<?, ?> resp = webClient.post()
                    .uri("/internal/v1/transactions/transfers")
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            String reference  = (String) resp.get("transaction_reference");
            String ledgerTxnId = (String) resp.get("ledger_transaction_id");
            return new DisbursementResult(reference,
                    ledgerTxnId != null ? UUID.fromString(ledgerTxnId) : null);

        } catch (WebClientResponseException e) {
            throw new SusuPaymentsException(
                    e.getStatusCode().value() + ":" + e.getResponseBodyAsString());
        } catch (SusuPaymentsException e) {
            throw e;
        } catch (Exception e) {
            throw new SusuPaymentsException(
                    "Disbursement transfer unavailable: " + e.getMessage());
        }
    }

    public record DisbursementResult(String transactionReference, UUID ledgerTransactionId) {}

    /**
     * Charges the late penalty split: half to SUSU_POT, half to PENALTY_REVENUE.
     * Both legs are separate internal transfers. If the first succeeds but the
     * second fails, the caller retries via the same idempotency key base — the
     * first leg is idempotent server-side, so no double-charge occurs.
     *
     * <p>Uses its own transfer type ({@code SUSU_LATE_PENALTY}) rather than
     * {@link #transfer}'s hardcoded {@code SUSU_CONTRIBUTION} — a penalty charge
     * is not a contribution payment, and labeling it as one would mislabel the
     * resulting ledger transactions.
     *
     * @return both transaction references, or {@code waived=true} with null
     *         references if the member has insufficient balance
     */
    public PenaltyChargeResult chargePenaltySplit(
            UUID   userWalletId,
            UUID   susuPotId,
            UUID   penaltyRevenueId,
            long   halfPenalty,
            UUID   contributionId,
            String correlationId,
            String idempotencyKeyBase) {
        try {
            String potRef = penaltyLegTransfer(userWalletId, susuPotId, halfPenalty,
                    contributionId, correlationId, idempotencyKeyBase + "-pot");
            String revenueRef = penaltyLegTransfer(userWalletId, penaltyRevenueId, halfPenalty,
                    contributionId, correlationId, idempotencyKeyBase + "-revenue");
            return new PenaltyChargeResult(potRef, revenueRef, false);

        } catch (SusuPaymentsException e) {
            String body = e.getMessage();
            if (body != null && body.contains("422") &&
                    body.contains("PAYMENTS_INSUFFICIENT_BALANCE")) {
                return new PenaltyChargeResult(null, null, true);
            }
            throw e;
        }
    }

    private String penaltyLegTransfer(UUID   sourceAccountId,
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
            body.put("transaction_type",        "SUSU_LATE_PENALTY");
            body.put("business_reference_id",   contributionId.toString());
            body.put("business_reference_type", "SUSU_CONTRIBUTION");
            body.put("correlation_id",          correlationId);
            body.put("narrative",               "Susu late-payment penalty");

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
                    "Penalty transfer unavailable: " + e.getMessage());
        }
    }

    public record PenaltyChargeResult(
            String potTransactionReference,
            String revenueTransactionReference,
            boolean waived
    ) {}
}
