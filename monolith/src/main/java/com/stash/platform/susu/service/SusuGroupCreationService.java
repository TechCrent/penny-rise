package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.CreateSusuGroupRequest;
import com.stash.platform.susu.api.dto.SusuGroupResponse;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.user.domain.SubscriptionTier;
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
 * Creates a susu group in PENDING status and adds the organiser as the first member.
 *
 * <p><strong>Atomicity:</strong> the susu_groups INSERT and the organiser's
 * susu_memberships INSERT are in the same {@code @Transactional} boundary.
 * If the membership INSERT fails (e.g. a unique-constraint race), the group
 * row is also rolled back. The client retries with the same idempotency key.
 *
 * <p><strong>Join code uniqueness:</strong> {@link JoinCodeGenerator} checks
 * uniqueness before returning a candidate. The final DB UNIQUE constraint on
 * join_code is the authoritative safety net for the rare race where two
 * concurrent requests generate the same code simultaneously.
 *
 * <p><strong>Free-tier limit:</strong> a FREE user may organise at most one
 * PENDING or ACTIVE group at a time. The formal {@code SubscriptionPolicy}
 * class ships in v0.5-030; for v0.4 the limit is enforced with an inline
 * count query. The limit check is done inside the transaction to be
 * consistent under concurrent creation attempts.
 *
 * <p><strong>KYC check:</strong> requires {@code kyc_status = APPROVED}.
 *
 * <p><strong>Idempotency:</strong> like {@code VaultCreationService}, the
 * Idempotency-Key header is required and logged for traceability, but
 * monolith has no dedicated idempotency filter (unlike payments-service) —
 * a duplicate key does not currently prevent a duplicate group from being
 * created. See JoinCodeGeneratorTest / SusuGroupCreationServiceTest for the
 * documented current behaviour.
 */
@Service
public class SusuGroupCreationService {

    private static final Logger log = LoggerFactory.getLogger(SusuGroupCreationService.class);

    static final int    FREE_TIER_ORGANISER_LIMIT = 1;
    static final int    PREMIUM_TIER_ORGANISER_LIMIT = 3;
    static final Set<String> VALID_FREQUENCIES =
            Set.of("WEEKLY", "BIWEEKLY", "MONTHLY");

    private final SusuGroupRepository      groupRepo;
    private final SusuMembershipRepository membershipRepo;
    private final UserRepository           userRepo;
    private final JoinCodeGenerator        joinCodeGenerator;
    private final Clock                    clock;

    public SusuGroupCreationService(SusuGroupRepository groupRepo,
                                     SusuMembershipRepository membershipRepo,
                                     UserRepository userRepo,
                                     JoinCodeGenerator joinCodeGenerator,
                                     Clock clock) {
        this.groupRepo         = groupRepo;
        this.membershipRepo    = membershipRepo;
        this.userRepo          = userRepo;
        this.joinCodeGenerator = joinCodeGenerator;
        this.clock             = clock;
    }

    @Transactional
    public SusuGroupResponse createGroup(UUID userId, CreateSusuGroupRequest request,
                                          String correlationId, String idempotencyKey) {
        // ── Load and validate user ────────────────────────────────────────
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));

        if (!user.isKycApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "KYC_NOT_APPROVED: Your identity verification must be approved " +
                    "before creating a susu group.");
        }

        // ── Validate frequency ────────────────────────────────────────────
        if (!VALID_FREQUENCIES.contains(request.frequency().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "frequency must be one of: WEEKLY, BIWEEKLY, MONTHLY.");
        }

        // ── Free-tier limit check (inside transaction) ────────────────────
        long activeGroupsAsOrganiser = groupRepo.countActiveGroupsByOrganiser(userId);
        boolean isFree = SubscriptionTier.FREE.equals(user.getSubscriptionTier());
        int     limit  = isFree ? FREE_TIER_ORGANISER_LIMIT : PREMIUM_TIER_ORGANISER_LIMIT;

        if (activeGroupsAsOrganiser >= limit) {
            log.info("SusuGroupCreation: {} user={} at organiser limit ({}/{})",
                    isFree ? "free" : "premium", userId, activeGroupsAsOrganiser, limit);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SUSU_FREE_TIER_LIMIT_REACHED: You have reached the maximum number " +
                    "of susu groups you can organise on your current plan. " +
                    "Complete or cancel an existing group to create a new one.");
        }

        // ── Generate join code ────────────────────────────────────────────
        String joinCode = joinCodeGenerator.generate();
        Instant now     = Instant.now(clock);

        // ── Create group row ──────────────────────────────────────────────
        SusuGroupEntity group = SusuGroupEntity.create(
                userId,
                request.name().trim(),
                request.contributionAmount(),
                request.frequency().toUpperCase(),
                request.targetMemberCount(),
                joinCode,
                now
        );
        group = groupRepo.save(group);

        // ── Create organiser membership row (same transaction) ────────────
        SusuMembershipEntity membership =
                SusuMembershipEntity.create(
                        group.getId(), userId, now);
        membershipRepo.save(membership);

        log.info("SusuGroup created: id={} organiser={} joinCode={} name='{}' " +
                 "contribution={}p frequency={} targetCount={} idempotencyKey={} correlation={}",
                group.getId(), userId, joinCode, group.getName(),
                request.contributionAmount(), request.frequency(),
                request.targetMemberCount(), idempotencyKey, correlationId);

        return SusuGroupResponse.from(group);
    }
}
