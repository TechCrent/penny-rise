package com.stash.admin.dispute;

import com.stash.platform.susu.service.SusuMembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * "Belongs to" is interpreted as "has or had a membership" at any status
 * (ACTIVE, LEFT, REMOVED) — a user who left a susu mid-cycle should still
 * be able to dispute something from when they were a member.
 */
@Component
public class SusuGroupDisputeEntityValidator implements DisputeEntityValidator {

    private final SusuMembershipService susuMembershipService;

    public SusuGroupDisputeEntityValidator(SusuMembershipService susuMembershipService) {
        this.susuMembershipService = susuMembershipService;
    }

    @Override
    public RelatedEntityType supportedType() { return RelatedEntityType.SUSU_GROUP; }

    @Override
    public void validateOwnership(UUID relatedEntityId, UUID userId) {
        if (!susuMembershipService.groupExists(relatedEntityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "DISPUTE_ENTITY_NOT_FOUND: No susu group with id " + relatedEntityId);
        }
        if (!susuMembershipService.hasOrHadMembership(relatedEntityId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "DISPUTE_ENTITY_NOT_OWNED: User is not a member of susu group " + relatedEntityId);
        }
    }
}
