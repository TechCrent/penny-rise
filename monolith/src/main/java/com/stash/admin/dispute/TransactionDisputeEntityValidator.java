package com.stash.admin.dispute;

import com.stash.admin.integration.IntegrationPaymentsClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * disputes.related_entity_id is a UUID, but the customer-facing transaction
 * lookup is keyed by string reference — IntegrationPaymentsClient.getTransactionById
 * calls payments-service's {@code GET /api/v1/transactions/by-id/{id}} endpoint,
 * which looks up by the transaction's UUID primary key instead.
 */
@Component
public class TransactionDisputeEntityValidator implements DisputeEntityValidator {

    private final IntegrationPaymentsClient paymentsClient;

    public TransactionDisputeEntityValidator(IntegrationPaymentsClient paymentsClient) {
        this.paymentsClient = paymentsClient;
    }

    @Override
    public RelatedEntityType supportedType() { return RelatedEntityType.TRANSACTION; }

    @Override
    public void validateOwnership(UUID relatedEntityId, UUID userId) {
        var transaction = paymentsClient.getTransactionById(relatedEntityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_ENTITY_NOT_FOUND: No transaction with id " + relatedEntityId));

        if (!userId.equals(transaction.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "DISPUTE_ENTITY_NOT_OWNED: Transaction " + relatedEntityId + " does not belong to this user.");
        }
    }
}
