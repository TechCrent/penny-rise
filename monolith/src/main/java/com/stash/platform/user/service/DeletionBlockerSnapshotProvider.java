package com.stash.platform.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Collects a snapshot of deletion blockers for a user at submission time.
 *
 * <p>Per System Design §13.13 and Schema doc §1.4, blockers are
 * informational at v0.2 — they do not prevent submission, only get
 * recorded for ops visibility. Full blocker-based rejection is a v1.0
 * concern once susu, vault, and split modules exist.
 *
 * <p>This class is the single integration point for future blocker
 * sources. Each {@link BlockerCheck} is independently pluggable — when
 * susu groups ship (v0.3+), a new {@code SusuBlockerCheck} bean is added
 * to the {@code checks} list with no change to this class or to
 * {@link DeletionRequestService}.
 *
 * <p>At v0.2, no blocker sources exist yet (no susu, vault, or KYC
 * submission-count entities are wired into the monolith), so the
 * snapshot is structurally correct but always an empty array.
 */
@Component
public class DeletionBlockerSnapshotProvider {

    private final ObjectMapper objectMapper;
    private final List<BlockerCheck> checks;

    public DeletionBlockerSnapshotProvider(ObjectMapper objectMapper,
                                           List<BlockerCheck> checks) {
        this.objectMapper = objectMapper;
        this.checks       = checks;
    }

    /**
     * Builds the blocker snapshot for a user.
     *
     * @param userId the user requesting deletion
     * @return a JSON array node — empty at v0.2, populated by future blocker checks
     */
    public JsonNode buildSnapshot(UUID userId) {
        ArrayNode blockers = objectMapper.createArrayNode();

        for (BlockerCheck check : checks) {
            blockers.addAll(check.findBlockers(userId));
        }

        return blockers;
    }

    /**
     * Implemented by future modules (susu, vault, split) to contribute
     * blocker entries to the deletion snapshot.
     */
    public interface BlockerCheck {
        List<ObjectNode> findBlockers(UUID userId);
    }
}
