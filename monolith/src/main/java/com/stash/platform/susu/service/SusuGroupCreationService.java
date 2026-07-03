package com.stash.platform.susu.service;

import com.stash.platform.subscription.service.SubscriptionLimitChecker;
import com.stash.platform.susu.api.dto.CreateSusuGroupRequest;
import com.stash.platform.susu.api.dto.SusuGroupResponse;
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
 * <p><strong>Tier limit:</strong> enforced via {@code SubscriptionLimitChecker}
 * (v0.5-030), reading numbers from {@code SubscriptionPolicy} (v0.5-029) —
 * FREE may organise at most 1 PENDING/ACTIVE group, PREMIUM at most 3.
 *
 * <p><strong>Concurrency:</strong> {@code userRepo.lockUserRow(userId)}
 * (SELECT FOR UPDATE) is acquired before the count-then-check, the same
 * pattern {@code VaultCreationService} uses — added in v0.5-030 to close a
 * real gap: before this, the count-check here had no lock, so two
 * concurrent requests from the same user could both read the same
 * under-limit count and both succeed.
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

    static final Set<String> VALID_FREQUENCIES =
            Set.of("WEEKLY", "BIWEEKLY", "MONTHLY");

    private final SusuGroupRepository      groupRepo;
    private final SusuMembershipRepository membershipRepo;
    private final UserRepository           userRepo;
    private final JoinCodeGenerator        joinCodeGenerator;
    private final SubscriptionLimitChecker subscriptionLimitChecker;
    private final Clock                    clock;

    public SusuGroupCreationService(SusuGroupRepository groupRepo,
                                     SusuMembershipRepository membershipRepo,
                                     UserRepository userRepo,
                                     JoinCodeGenerator joinCodeGenerator,
                                     SubscriptionLimitChecker subscriptionLimitChecker,
                                     Clock clock) {
        this.groupRepo         = groupRepo;
        this.membershipRepo    = membershipRepo;
        this.userRepo          = userRepo;
        this.joinCodeGenerator = joinCodeGenerator;
        this.subscriptionLimitChecker = subscriptionLimitChecker;
        this.clock             = clock;
    }

    @Transactional
    public SusuGroupResponse createGroup(UUID userId, CreateSusuGroupRequest request,
                                          String correlationId, String idempotencyKey) {
        // ── Lock user row (v0.5-030: closes a pre-existing concurrency gap) ─
        userRepo.lockUserRow(userId);

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

        // ── Tier limit check (v0.5-030: centralized in SubscriptionLimitChecker) ──
        long activeGroupsAsOrganiser = groupRepo.countActiveGroupsByOrganiser(userId);
        subscriptionLimitChecker.assertSusuOrganiserWithinLimit(
                user.getSubscriptionTier(), activeGroupsAsOrganiser);

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
