package com.stash.kyc.submission.domain;

import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for kyc.manual_review_queue. See v0.2-020's migration notes —
 * this table is NOT in the canonical Schema doc §8; added per the Wireframes
 * doc A5 claim/lock behavior. Flagged for doc sign-off.
 */

@Entity
@Table(schema = "kyc", name = "manual_review_queue")
@Getter
@Setter
@NoArgsConstructor

public class ManualReviewQueueEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Column(name = "claimed_by_admin_id")
    private UUID claimedByAdminId;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_expires_at")
    private Instant claimExpiresAt;

    @Column(name = "flag_reason", length = 255)
    private String flagReason;

    @Column(name = "entered_queue_at", nullable = false, updatable = false)
    private Instant enteredQueueAt;

    @Column(name = "removed_from_queue_at")
    private Instant removedFromQueueAt;

    public ManualReviewQueueEntry(UUID submissionId, String flagReason) {
        this.id              = UuidV7Generator.generate();
        this.submissionId    = submissionId;
        this.flagReason      = flagReason;
        this.enteredQueueAt  = Instant.now();
    }
}