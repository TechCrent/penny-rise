package com.stash.admin.dispute;

import com.stash.platform.transfer.service.PeerTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * "Belongs to" means either party — a user should be able to dispute a
 * transfer they sent OR received.
 */
@Component
public class TransferDisputeEntityValidator implements DisputeEntityValidator {

    private final PeerTransferService peerTransferService;

    public TransferDisputeEntityValidator(PeerTransferService peerTransferService) {
        this.peerTransferService = peerTransferService;
    }

    @Override
    public RelatedEntityType supportedType() { return RelatedEntityType.TRANSFER; }

    @Override
    public void validateOwnership(UUID relatedEntityId, UUID userId) {
        var transfer = peerTransferService.findById(relatedEntityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "DISPUTE_ENTITY_NOT_FOUND: No transfer with id " + relatedEntityId));

        boolean isParty = userId.equals(transfer.getSenderUserId())
                || userId.equals(transfer.getRecipientUserId());
        if (!isParty) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "DISPUTE_ENTITY_NOT_OWNED: Transfer " + relatedEntityId + " does not involve this user.");
        }
    }
}
