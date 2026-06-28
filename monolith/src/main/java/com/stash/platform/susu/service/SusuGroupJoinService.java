package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.JoinSusuGroupRequest;
import com.stash.platform.susu.api.dto.JoinSusuGroupResponse;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Processes a susu group join request via a join code.
 *
 * <p><strong>Concurrency safety:</strong> the group row is loaded with
 * {@code FOR UPDATE} before the member count check. This serialises concurrent
 * join attempts for the same group — the second joiner blocks until the first
 * transaction commits, then re-reads the (now-updated) count and correctly
 * sees the group as full if the last slot was taken.
 *
 * <p><strong>Duplicate-join safety:</strong> the {@code UNIQUE (susu_group_id, user_id)}
 * constraint on {@code susu_memberships} is the DB-level safety net. The application
 * check ({@code existsByGroupIdAndUserId}) provides a clean 409 before the constraint
 * fires — the constraint catches the rare race where two requests for the same user
 * and group both pass the application check simultaneously.
 *
 * <p><strong>Join order:</strong> {@code joined_at} is set to {@code now()} at insert
 * time. The activation service uses {@code joined_at ASC} to assign rotation positions
 * (organiser first, then members in join order). Millisecond precision is sufficient
 * because concurrent joins for the same group are serialised by the {@code FOR UPDATE}
 * lock, ensuring distinct {@code joined_at} timestamps.
 */
@Service
public class SusuGroupJoinService {

    private static final Logger log = LoggerFactory.getLogger(SusuGroupJoinService.class);

    private static final Set<String> NOT_JOINABLE_STATUSES =
            Set.of("ACTIVE", "COMPLETED", "CANCELLED");

    private final SusuGroupRepository      groupRepo;
    private final SusuMembershipRepository membershipRepo;
    private final UserRepository           userRepo;
    private final Clock                    clock;

    public SusuGroupJoinService(SusuGroupRepository groupRepo,
                                 SusuMembershipRepository membershipRepo,
                                 UserRepository userRepo,
                                 Clock clock) {
        this.groupRepo      = groupRepo;
        this.membershipRepo = membershipRepo;
        this.userRepo       = userRepo;
        this.clock          = clock;
    }

    /**
     * Joins the authenticated user to the susu group identified by the join code.
     *
     * @throws ResponseStatusException 403 if KYC not approved;
     *                                 404 if join code not found;
     *                                 409 if group is not joinable, already a member, or full
     */
    @Transactional
    public JoinSusuGroupResponse joinGroup(UUID userId, JoinSusuGroupRequest request,
                                            String correlationId, String idempotencyKey) {
        // ── Load and validate user ────────────────────────────────────────
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Authenticated user not found."));

        if (!user.isKycApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "KYC_NOT_APPROVED: Your identity verification must be approved " +
                    "before joining a susu group.");
        }

        // ── Load group with row-level lock ────────────────────────────────
        // FOR UPDATE ensures concurrent joins for the same group are serialised.
        // The lock is held until this transaction commits.
        String normalised = request.joinCode().toUpperCase().trim();
        SusuGroupEntity group = groupRepo.findByJoinCodeForUpdate(normalised)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No susu group found with that join code."));

        // ── Status check ──────────────────────────────────────────────────
        if (NOT_JOINABLE_STATUSES.contains(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_GROUP_NOT_JOINABLE: This group is " + group.getStatus().toLowerCase() +
                    " and is no longer accepting new members.");
        }

        // ── Duplicate membership check ─────────────────────────────────────
        if (membershipRepo.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_ALREADY_A_MEMBER: You are already a member of this group.");
        }

        // ── Member count check ────────────────────────────────────────────
        // Read AFTER the FOR UPDATE lock is acquired to get an accurate count.
        long currentCount = membershipRepo.countActiveMembers(group.getId());
        if (currentCount >= group.getTargetMemberCount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_GROUP_FULL: This group has reached its maximum member count " +
                    "of " + group.getTargetMemberCount() + ".");
        }

        // ── Insert membership row ─────────────────────────────────────────
        Instant now = Instant.now(clock);
        SusuMembershipEntity membership =
                SusuMembershipEntity.create(group.getId(), userId, now);
        membership = membershipRepo.save(membership);

        long updatedCount = currentCount + 1;

        log.info("SusuGroupJoin: user={} joined group={} joinCode={} " +
                 "memberCount={}/{} idempotencyKey={} correlation={}",
                userId, group.getId(), normalised,
                updatedCount, group.getTargetMemberCount(), idempotencyKey, correlationId);

        return JoinSusuGroupResponse.from(group, membership, updatedCount);
    }
}
