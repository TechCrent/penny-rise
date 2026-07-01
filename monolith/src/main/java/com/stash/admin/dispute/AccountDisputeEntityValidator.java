package com.stash.admin.dispute;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Used for both ACCOUNT and OTHER dispute_type — OTHER is treated as a
 * self-referential dispute with no separate entity to validate against.
 * The only check is whether related_entity_id equals the caller's own user id.
 */
@Component
public class AccountDisputeEntityValidator implements DisputeEntityValidator {

    @Override
    public RelatedEntityType supportedType() { return RelatedEntityType.ACCOUNT; }

    @Override
    public void validateOwnership(UUID relatedEntityId, UUID userId) {
        if (!relatedEntityId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "DISPUTE_ENTITY_NOT_OWNED: related_entity_id must be the caller's own account id.");
        }
    }
}
