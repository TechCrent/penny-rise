package com.stash.platform.payments.service;

import com.stash.platform.payments.api.dto.MobileTransactionDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TransactionQueryService {

    private final RestClient paymentsClient;

    public TransactionQueryService(
            RestClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}") String serviceToken) {
        this.paymentsClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .build();
    }

    public MobileTransactionDetailResponse getForUser(String reference, UUID userId) {
        try {
            MobileTransactionDetailResponse.PaymentsTransactionDetail detail =
                    paymentsClient.get()
                            .uri("/api/v1/transactions/{reference}", reference)
                            .retrieve()
                            .body(MobileTransactionDetailResponse.PaymentsTransactionDetail.class);

            if (detail == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found: " + reference);
            }

            if (!userId.equals(detail.initiatingUserId())
                    && (detail.counterpartyUserId() == null
                            || !userId.equals(detail.counterpartyUserId()))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found: " + reference);
            }

            return MobileTransactionDetailResponse.from(detail);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Transaction not found: " + reference);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(e.getStatusCode().value()), e.getMessage());
        }
    }
}
