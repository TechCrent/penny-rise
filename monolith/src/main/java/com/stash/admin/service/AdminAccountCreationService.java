package com.stash.admin.service;

import com.stash.admin.api.dto.AdminStaffSummaryResponse;
import com.stash.admin.api.dto.CreateAdminAccountRequest;
import com.stash.admin.domain.AdminAccountEntity;
import com.stash.admin.rbac.AdminAccountType;
import com.stash.admin.rbac.AdminRoleMapping;
import com.stash.admin.repository.AdminAccountRepository;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Enforces the admin-account creation hierarchy (SUPER -> VICE_SUPER|TAB;
 * VICE_SUPER -> TAB; TAB -> nothing) and persists new/deactivated admin
 * accounts. Error convention matches AdminAuthService: ResponseStatusException
 * with a "CODE: message" reason.
 *
 * <p>Gap-analysis fix: this previously only validated the creation hierarchy
 * and threw {@code UnsupportedOperationException} for the actual persistence
 * (v0.5-004 scope note) — meaning there was no working invite/deactivate flow
 * for admin operators anywhere. New staff accounts are created with an
 * initial password supplied directly by the creating admin (relayed
 * out-of-band) — no email/invite-token flow, matching how the bootstrap
 * SUPER account is already seeded via migration.
 */
@Service
public class AdminAccountCreationService {

    private final AdminRoleMapping        roleMapping;
    private final AdminAccountRepository  accountRepo;
    private final BCryptPasswordEncoder   passwordEncoder;
    private final Clock                   clock;

    public AdminAccountCreationService(AdminRoleMapping roleMapping,
                                       AdminAccountRepository accountRepo,
                                       Clock clock,
                                       @Value("${stash.security.bcrypt-cost:12}") int bcryptCost) {
        this.roleMapping     = roleMapping;
        this.accountRepo     = accountRepo;
        this.passwordEncoder = new BCryptPasswordEncoder(bcryptCost);
        this.clock           = clock;
    }

    public void assertCanCreate(AdminTokenClaims creator, String targetAccountTypeRaw) {
        AdminAccountType creatorType = AdminAccountType.parse(creator.accountType());
        AdminAccountType targetType  = AdminAccountType.parse(targetAccountTypeRaw);

        if (creatorType == null || targetType == null || !roleMapping.canCreate(creatorType, targetType)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ADMIN_INSUFFICIENT_ROLE: " + creator.accountType() +
                    " cannot create an admin account of type " + targetAccountTypeRaw);
        }
    }

    @Transactional
    public AdminStaffSummaryResponse create(AdminTokenClaims creator, CreateAdminAccountRequest request) {
        assertCanCreate(creator, request.accountType());

        if (accountRepo.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ADMIN_EMAIL_ALREADY_REGISTERED: An admin account with this email already exists.");
        }

        AdminAccountEntity entity = AdminAccountEntity.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.roleName(),
                request.accountType(),
                creator.adminAccountId(),
                Instant.now(clock)
        );

        accountRepo.save(entity);
        return toSummary(entity);
    }

    @Transactional(readOnly = true)
    public Page<AdminStaffSummaryResponse> list(Pageable pageable) {
        return accountRepo.findAll(pageable).map(this::toSummary);
    }

    @Transactional
    public void deactivate(UUID targetId) {
        AdminAccountEntity entity = accountRepo.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ADMIN_ACCOUNT_NOT_FOUND: No admin account with that id."));
        entity.deactivate(Instant.now(clock));
        accountRepo.save(entity);
    }

    private AdminStaffSummaryResponse toSummary(AdminAccountEntity entity) {
        return new AdminStaffSummaryResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getAccountType(),
                entity.getRoleName(),
                entity.isActive(),
                entity.getDeactivatedAt(),
                entity.getCreatedAt()
        );
    }
}
