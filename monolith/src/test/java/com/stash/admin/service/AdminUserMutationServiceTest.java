package com.stash.admin.service;

import com.stash.admin.event.UserForceLogoutEvent;
import com.stash.admin.event.UserRestoredEvent;
import com.stash.admin.event.UserSuspendedEvent;
import com.stash.admin.repository.AdminAuditActionRepository;
import com.stash.platform.user.api.dto.AdminUserView;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.stash.admin.rbac.AdminAuditActionType.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AdminUserMutationService")
class AdminUserMutationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

    private final UserService               userService            = mock(UserService.class);
    private final RefreshTokenRepository    refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final AdminAuditActionRepository auditRepo             = mock(AdminAuditActionRepository.class);
    private final ApplicationEventPublisher  eventPublisher        = mock(ApplicationEventPublisher.class);

    private final AdminUserMutationService service = new AdminUserMutationService(
            userService, refreshTokenRepository, auditRepo, eventPublisher, FIXED_CLOCK);

    private static final UUID   USER_ID  = UUID.randomUUID();
    private static final UUID   ADMIN_ID = UUID.randomUUID();
    private static final String REASON   = "Repeated terms-of-service violation";

    @BeforeEach
    void setUp() {
        when(userService.getAdminViewById(USER_ID)).thenReturn(Optional.of(mock(AdminUserView.class)));
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── suspend ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy suspend transitions the account and returns normally")
    void happySuspend() {
        when(userService.suspend(USER_ID)).thenReturn(true);

        assertThatCode(() -> service.suspend(USER_ID, ADMIN_ID, REASON)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("suspend writes an admin_audit_actions row with action_type=USER_SUSPENDED")
    void suspendWritesAudit() {
        when(userService.suspend(USER_ID)).thenReturn(true);

        service.suspend(USER_ID, ADMIN_ID, REASON);

        verify(auditRepo).save(argThat(a ->
                USER_SUSPENDED.equals(a.getActionType()) && USER_ID.equals(a.getTargetId())));
    }

    @Test
    @DisplayName("suspend emits a UserSuspendedEvent")
    void suspendEmitsEvent() {
        when(userService.suspend(USER_ID)).thenReturn(true);

        service.suspend(USER_ID, ADMIN_ID, REASON);

        verify(eventPublisher).publishEvent((Object) argThat(e ->
                e instanceof UserSuspendedEvent evt
                        && evt.userId().equals(USER_ID)
                        && evt.reason().equals(REASON)));
    }

    @Test
    @DisplayName("suspending an already-suspended user returns 409 ADMIN_USER_ALREADY_SUSPENDED")
    void suspendAlreadySuspended() {
        when(userService.suspend(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.suspend(USER_ID, ADMIN_ID, REASON))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("ADMIN_USER_ALREADY_SUSPENDED");
                });

        verifyNoInteractions(auditRepo, eventPublisher);
    }

    @Test
    @DisplayName("suspend throws 404 USER_NOT_FOUND for a nonexistent user")
    void suspendUserNotFound() {
        when(userService.getAdminViewById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(USER_ID, ADMIN_ID, REASON))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── restore ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy restore transitions the account and returns normally")
    void happyRestore() {
        when(userService.restore(USER_ID)).thenReturn(true);

        assertThatCode(() -> service.restore(USER_ID, ADMIN_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("restore writes an admin_audit_actions row with action_type=USER_RESTORED")
    void restoreWritesAudit() {
        when(userService.restore(USER_ID)).thenReturn(true);

        service.restore(USER_ID, ADMIN_ID);

        verify(auditRepo).save(argThat(a ->
                USER_RESTORED.equals(a.getActionType()) && USER_ID.equals(a.getTargetId())));
    }

    @Test
    @DisplayName("restore emits a UserRestoredEvent")
    void restoreEmitsEvent() {
        when(userService.restore(USER_ID)).thenReturn(true);

        service.restore(USER_ID, ADMIN_ID);

        verify(eventPublisher).publishEvent((Object) argThat(e ->
                e instanceof UserRestoredEvent evt && evt.userId().equals(USER_ID)));
    }

    @Test
    @DisplayName("restoring a non-suspended user returns 409 ADMIN_USER_NOT_SUSPENDED")
    void restoreNotSuspended() {
        when(userService.restore(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.restore(USER_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("ADMIN_USER_NOT_SUSPENDED");
                });

        verifyNoInteractions(auditRepo, eventPublisher);
    }

    @Test
    @DisplayName("restore throws 404 USER_NOT_FOUND for a nonexistent user")
    void restoreUserNotFound() {
        when(userService.getAdminViewById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(USER_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── forceLogout ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("forceLogout revokes all active sessions and returns normally")
    void happyForceLogout() {
        when(refreshTokenRepository.revokeAllActiveForUser(eq(USER_ID), any())).thenReturn(3);

        assertThatCode(() -> service.forceLogout(USER_ID, ADMIN_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("forceLogout writes audit row with sessions_revoked count")
    void forceLogoutWritesAuditWithSessionCount() {
        when(refreshTokenRepository.revokeAllActiveForUser(eq(USER_ID), any())).thenReturn(2);

        service.forceLogout(USER_ID, ADMIN_ID);

        verify(auditRepo).save(argThat(a ->
                USER_FORCE_LOGOUT.equals(a.getActionType())
                        && USER_ID.equals(a.getTargetId())
                        && a.getPayloadJson() != null
                        && a.getPayloadJson().contains("2")));
    }

    @Test
    @DisplayName("forceLogout emits a UserForceLogoutEvent with the revoked count")
    void forceLogoutEmitsEvent() {
        when(refreshTokenRepository.revokeAllActiveForUser(eq(USER_ID), any())).thenReturn(3);

        service.forceLogout(USER_ID, ADMIN_ID);

        verify(eventPublisher).publishEvent((Object) argThat(e ->
                e instanceof UserForceLogoutEvent evt
                        && evt.userId().equals(USER_ID)
                        && evt.sessionsRevoked() == 3));
    }

    @Test
    @DisplayName("forceLogout with zero active sessions still writes audit and emits event")
    void forceLogoutNoSessions() {
        when(refreshTokenRepository.revokeAllActiveForUser(eq(USER_ID), any())).thenReturn(0);

        assertThatCode(() -> service.forceLogout(USER_ID, ADMIN_ID)).doesNotThrowAnyException();

        verify(auditRepo).save(any());
        verify(eventPublisher).publishEvent((Object) any());
    }

    @Test
    @DisplayName("forceLogout throws 404 USER_NOT_FOUND for a nonexistent user")
    void forceLogoutUserNotFound() {
        when(userService.getAdminViewById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forceLogout(USER_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
