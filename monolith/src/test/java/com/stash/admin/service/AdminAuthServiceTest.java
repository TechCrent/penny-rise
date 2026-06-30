package com.stash.admin.service;

import com.stash.admin.domain.AdminAccountEntity;
import com.stash.admin.domain.AdminLoginAttemptEntity;
import com.stash.admin.domain.AdminRefreshTokenEntity;
import com.stash.admin.repository.AdminAccountRepository;
import com.stash.admin.repository.AdminAuditActionRepository;
import com.stash.admin.repository.AdminLoginAttemptRepository;
import com.stash.admin.repository.AdminRefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class AdminAuthServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-29T10:00:00Z"), ZoneOffset.UTC);

    // Cost 4 for fast tests — service is instantiated with cost 4 via constructor param
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private final AdminAccountRepository      accountRepo      = mock(AdminAccountRepository.class);
    private final AdminRefreshTokenRepository refreshTokenRepo = mock(AdminRefreshTokenRepository.class);
    private final AdminLoginAttemptRepository attemptRepo      = mock(AdminLoginAttemptRepository.class);
    private final AdminAuditActionRepository  auditRepo        = mock(AdminAuditActionRepository.class);
    private final AdminLockoutPolicy          lockoutPolicy    = mock(AdminLockoutPolicy.class);
    private final AdminJwtService             jwtService       = mock(AdminJwtService.class);

    private final AdminAuthService service = new AdminAuthService(
            accountRepo, refreshTokenRepo, attemptRepo, auditRepo,
            lockoutPolicy, jwtService, FIXED_CLOCK, 4); // bcrypt cost 4 for fast tests

    private static final String EMAIL    = "admin@stash.app";
    private static final String PASSWORD = "CorrectHorseBattery123!";
    private static final String IP       = "203.0.113.42";

    @BeforeEach
    void setUp() {
        when(lockoutPolicy.lockedUntil(anyString())).thenReturn(null);
        when(attemptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issueAccessToken(any(), anyString(), any()))
                .thenReturn("fake.jwt.token");
    }

    // ── Happy login ───────────────────────────────────────────────────────

    @Test
    @DisplayName("happy login returns access and refresh tokens")
    void happy_login() {
        AdminAccountEntity account = activeAccount();
        when(accountRepo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));

        var result = service.login(EMAIL, PASSWORD, IP);

        assertThat(result.accessToken()).isEqualTo("fake.jwt.token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.accountType()).isEqualTo("SUPER");
    }

    @Test
    @DisplayName("happy login records a successful attempt and audit row")
    void happy_login_records_attempt_and_audit() {
        AdminAccountEntity account = activeAccount();
        when(accountRepo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));

        service.login(EMAIL, PASSWORD, IP);

        verify(attemptRepo).save(argThat(AdminLoginAttemptEntity::isSucceeded));
        verify(auditRepo).save(argThat(a -> "ADMIN_LOGIN_SUCCESS".equals(a.getActionType())));
    }

    @Test
    @DisplayName("happy login persists refresh token bound to source IP")
    void happy_login_binds_ip() {
        AdminAccountEntity account = activeAccount();
        when(accountRepo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));

        service.login(EMAIL, PASSWORD, IP);

        verify(refreshTokenRepo).save(argThat(t -> IP.equals(t.getIpAddress())));
    }

    // ── Wrong password ───────────────────────────────────────────────────

    @Test
    @DisplayName("wrong password returns 401 AUTH_INVALID_CREDENTIALS")
    void wrong_password_returns_401() {
        AdminAccountEntity account = activeAccount();
        when(accountRepo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login(EMAIL, "wrong-password", IP))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNAUTHORIZED);
                    assertThat(e.getReason()).contains("AUTH_INVALID_CREDENTIALS");
                });
    }

    @Test
    @DisplayName("wrong password records a failed attempt")
    void wrong_password_records_failure() {
        AdminAccountEntity account = activeAccount();
        when(accountRepo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));

        try { service.login(EMAIL, "wrong", IP); } catch (Exception ignored) {}

        verify(attemptRepo).save(argThat(a -> !a.isSucceeded()));
    }

    @Test
    @DisplayName("unknown email still records a failed attempt (enumeration protection)")
    void unknown_email_records_failure() {
        when(accountRepo.findByEmailIgnoreCase("nobody@stash.app")).thenReturn(Optional.empty());

        try { service.login("nobody@stash.app", "anything", IP); } catch (Exception ignored) {}

        verify(attemptRepo).save(argThat(a -> !a.isSucceeded()));
        verify(auditRepo, never()).save(any());
    }

    @Test
    @DisplayName("inactive (deactivated) admin cannot log in even with correct password")
    void deactivated_admin_cannot_login() {
        AdminAccountEntity deactivated = activeAccount();
        setField(deactivated, "isActive", false);
        when(accountRepo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, IP))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNAUTHORIZED));
    }

    // ── Lockout ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("locked-out account returns 423 ADMIN_ACCOUNT_LOCKED before checking password")
    void locked_account_returns_423() {
        Instant lockExpiry = Instant.parse("2026-06-29T10:15:00Z");
        when(lockoutPolicy.lockedUntil(EMAIL)).thenReturn(lockExpiry);

        assertThatThrownBy(() -> service.login(EMAIL, PASSWORD, IP))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(LOCKED);
                    assertThat(e.getReason()).contains("ADMIN_ACCOUNT_LOCKED");
                });

        verifyNoInteractions(accountRepo);
    }

    // ── Refresh: happy path ──────────────────────────────────────────────

    @Test
    @DisplayName("happy refresh rotates the token and returns new pair")
    void happy_refresh_rotates() {
        AdminAccountEntity account = activeAccount();
        AdminRefreshTokenEntity existingToken = activeToken(account.getId(), IP);

        when(refreshTokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));
        when(accountRepo.findById(account.getId())).thenReturn(Optional.of(account));

        var result = service.refresh("some-opaque-token", IP);

        assertThat(result.accessToken()).isEqualTo("fake.jwt.token");
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("happy refresh revokes old token with reason ROTATION")
    void happy_refresh_revokes_old_with_rotation_reason() {
        AdminAccountEntity account = activeAccount();
        AdminRefreshTokenEntity existingToken = activeToken(account.getId(), IP);

        when(refreshTokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));
        when(accountRepo.findById(account.getId())).thenReturn(Optional.of(account));

        service.refresh("some-opaque-token", IP);

        assertThat(existingToken.getRevokedReason()).isEqualTo("ROTATION");
    }

    // ── IP mismatch ───────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh from a different IP returns 401 ADMIN_IP_MISMATCH")
    void ip_mismatch_returns_401() {
        AdminAccountEntity account = activeAccount();
        AdminRefreshTokenEntity existingToken = activeToken(account.getId(), "198.51.100.1");

        when(refreshTokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));

        assertThatThrownBy(() -> service.refresh("some-opaque-token", "203.0.113.99"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNAUTHORIZED);
                    assertThat(e.getReason()).contains("ADMIN_IP_MISMATCH");
                });
    }

    @Test
    @DisplayName("IP mismatch revokes the token with reason IP_MISMATCH")
    void ip_mismatch_revokes_token() {
        AdminAccountEntity account = activeAccount();
        AdminRefreshTokenEntity existingToken = activeToken(account.getId(), "198.51.100.1");

        when(refreshTokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));

        try { service.refresh("token", "203.0.113.99"); } catch (Exception ignored) {}

        assertThat(existingToken.getRevokedReason()).isEqualTo("IP_MISMATCH");
    }

    @Test
    @DisplayName("IP mismatch writes an audit row")
    void ip_mismatch_writes_audit() {
        AdminAccountEntity account = activeAccount();
        AdminRefreshTokenEntity existingToken = activeToken(account.getId(), "198.51.100.1");
        when(refreshTokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));

        try { service.refresh("token", "203.0.113.99"); } catch (Exception ignored) {}

        verify(auditRepo).save(argThat(a -> "ADMIN_REFRESH_IP_MISMATCH".equals(a.getActionType())));
    }

    @Test
    @DisplayName("same IP on refresh succeeds")
    void same_ip_refresh_succeeds() {
        AdminAccountEntity account = activeAccount();
        AdminRefreshTokenEntity existingToken = activeToken(account.getId(), IP);

        when(refreshTokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));
        when(accountRepo.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatCode(() -> service.refresh("token", IP)).doesNotThrowAnyException();
    }

    // ── Replay detection ──────────────────────────────────────────────────

    @Test
    @DisplayName("presenting an already-rotated token triggers replay detection")
    void replayed_token_detected() {
        AdminAccountEntity account = activeAccount();
        AdminRefreshTokenEntity rotatedToken = activeToken(account.getId(), IP);
        rotatedToken.revoke("ROTATION");

        when(refreshTokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(rotatedToken));

        assertThatThrownBy(() -> service.refresh("old-token", IP))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNAUTHORIZED));
    }

    @Test
    @DisplayName("expired (but not revoked) token returns 401")
    void expired_token_returns_401() {
        AdminAccountEntity account = activeAccount();
        AdminRefreshTokenEntity expiredToken = AdminRefreshTokenEntity.issue(
                account.getId(), "hash", IP,
                Instant.parse("2026-06-29T00:00:00Z"), Duration.ofHours(8));
        // expires_at = 2026-06-29T08:00:00Z, now (FIXED_CLOCK) = 10:00:00Z -> expired

        when(refreshTokenRepo.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> service.refresh("token", IP))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNAUTHORIZED));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private AdminAccountEntity activeAccount() {
        // Use the factory method — protected constructor is not accessible cross-package
        AdminAccountEntity a = AdminAccountEntity.create(
                EMAIL, ENCODER.encode(PASSWORD), "Test Admin", null, "SUPER", null,
                Instant.now(FIXED_CLOCK));
        setField(a, "id", UUID.randomUUID());
        return a;
    }

    private AdminRefreshTokenEntity activeToken(UUID adminId, String ip) {
        return AdminRefreshTokenEntity.issue(
                adminId, "existing-hash", ip,
                Instant.now(FIXED_CLOCK), Duration.ofHours(8));
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name)
            throws NoSuchFieldException {
        try { return c.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (c.getSuperclass() != null) return findField(c.getSuperclass(), name);
            throw e;
        }
    }
}
