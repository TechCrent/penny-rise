package com.stash.admin.service;

import com.stash.admin.domain.AdminAuditActionEntity;
import com.stash.admin.event.UserForceLogoutEvent;
import com.stash.admin.event.UserRestoredEvent;
import com.stash.admin.event.UserSuspendedEvent;
import com.stash.admin.repository.AdminAuditActionRepository;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.stash.admin.rbac.AdminAuditActionType.*;

/**
 * Admin-initiated user account mutations: suspend, restore, and force-logout.
 *
 * <p>Error convention (matches AdminAuthService/AdminAccountCreationService):
 * {@link ResponseStatusException} with a "CODE: message" reason string.
 *
 * <p>Events are published via {@link ApplicationEventPublisher}, matching the
 * monolith's {@code @TransactionalEventListener(AFTER_COMMIT)} pattern used by
 * UserCreatedEventPublisher, SusuEventPublisher, etc.
 *
 * <p>Existence check re-uses {@link UserService#getAdminViewById(UUID)} — cheap
 * and keeps "does this user exist" logic in one place.
 */
@Service
public class AdminUserMutationService {

    private final UserService               userService;
    private final RefreshTokenRepository    refreshTokenRepository;
    private final AdminAuditActionRepository auditRepo;
    private final ApplicationEventPublisher  eventPublisher;
    private final Clock                     clock;

    public AdminUserMutationService(UserService userService,
                                     RefreshTokenRepository refreshTokenRepository,
                                     AdminAuditActionRepository auditRepo,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.userService            = userService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditRepo              = auditRepo;
        this.eventPublisher         = eventPublisher;
        this.clock                  = clock;
    }

    @Transactional
    public void suspend(UUID userId, UUID adminAccountId, String reason) {
        assertUserExists(userId);
        Instant now = Instant.now(clock);

        boolean transitioned = userService.suspend(userId);
        if (!transitioned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ADMIN_USER_ALREADY_SUSPENDED: User " + userId + " is already suspended.");
        }

        auditRepo.save(AdminAuditActionEntity.create(
                adminAccountId, USER_SUSPENDED, "USER", userId,
                "{\"reason\": " + jsonQuote(reason) + "}", null, now));

        eventPublisher.publishEvent(new UserSuspendedEvent(userId, adminAccountId, reason, now));
    }

    @Transactional
    public void restore(UUID userId, UUID adminAccountId) {
        assertUserExists(userId);
        Instant now = Instant.now(clock);

        boolean transitioned = userService.restore(userId);
        if (!transitioned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "ADMIN_USER_NOT_SUSPENDED: User " + userId + " is not currently suspended.");
        }

        auditRepo.save(AdminAuditActionEntity.create(
                adminAccountId, USER_RESTORED, "USER", userId, null, null, now));

        eventPublisher.publishEvent(new UserRestoredEvent(userId, adminAccountId, now));
    }

    @Transactional
    public void forceLogout(UUID userId, UUID adminAccountId) {
        assertUserExists(userId);
        Instant now = Instant.now(clock);

        int revokedCount = refreshTokenRepository.revokeAllActiveForUser(userId, now);

        auditRepo.save(AdminAuditActionEntity.create(
                adminAccountId, USER_FORCE_LOGOUT, "USER", userId,
                "{\"sessions_revoked\": " + revokedCount + "}", null, now));

        eventPublisher.publishEvent(new UserForceLogoutEvent(userId, adminAccountId, revokedCount, now));
    }

    private void assertUserExists(UUID userId) {
        userService.getAdminViewById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND: No user with id " + userId));
    }

    private static String jsonQuote(String s) {
        return s == null ? "null" : "\"" + s.replace("\"", "\\\"") + "\"";
    }
}
