package com.stash.admin.dispute;

import java.util.UUID;

/**
 * One implementation per RelatedEntityType. Each implementation throws
 * ResponseStatusException(404) if the entity doesn't exist, or (403) if it
 * exists but isn't owned by the supplied user.
 */
public interface DisputeEntityValidator {
    RelatedEntityType supportedType();
    void validateOwnership(UUID relatedEntityId, UUID userId);
}
