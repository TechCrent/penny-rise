package com.stash.admin.dispute;

import com.stash.admin.integration.IntegrationPaymentsClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * BLOCKING DEPENDENCY on a new payments-service endpoint. disputes.related_entity_id
 * is a UUID, but the customer-facing transaction lookup is keyed by string reference.
 * The stub IntegrationPaymentsClient.getTransactionById returns Optional.empty() until
 * the payments-service exposes a by-id lookup endpoint.
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
