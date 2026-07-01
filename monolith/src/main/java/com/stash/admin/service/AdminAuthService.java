package com.stash.admin.service;

import com.stash.admin.domain.AdminAccountEntity;
import com.stash.admin.domain.AdminAuditActionEntity;
import com.stash.admin.domain.AdminLoginAttemptEntity;
import com.stash.admin.domain.AdminRefreshTokenEntity;
import com.stash.admin.repository.AdminAccountRepository;
import com.stash.admin.repository.AdminAuditActionRepository;
import com.stash.admin.repository.AdminLoginAttemptRepository;
import com.stash.admin.repository.AdminRefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin login and token refresh.
 *
 * <p><strong>Lockout-vs-enumeration tradeoff:</strong> a locked-out account
 * and a wrong-password attempt return the same generic 401 to avoid revealing
 * whether the account is merely locked vs the password is wrong vs the email
 * doesn't exist. The only exception is after 5+ failures are already recorded
 * from this client, at which point 423 ADMIN_ACCOUNT_LOCKED is acceptable
 * to reveal — the caller already knows they've been failing.
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    static final Duration REFRESH_TOKEN_INACTIVITY_WINDOW = Duration.ofHours(8);

    private final AdminAccountRepository      accountRepo;
    private final AdminRefreshTokenRepository refreshTokenRepo;
    private final AdminLoginAttemptRepository attemptRepo;
    private final AdminAuditActionRepository  auditRepo;
    private final AdminLockoutPolicy          lockoutPolicy;
    private final AdminJwtService             jwtService;
    private final BCryptPasswordEncoder       passwordEncoder;
    private final Clock                       clock;
    private final SecureRandom                secureRandom = new SecureRandom();

    public AdminAuthService(AdminAccountRepository accountRepo,
                            AdminRefreshTokenRepository refreshTokenRepo,
                            AdminLoginAttemptRepository attemptRepo,
                            AdminAuditActionRepository auditRepo,
                            AdminLockoutPolicy lockoutPolicy,
                            AdminJwtService jwtService,
                            Clock clock,
                            @Value("${stash.security.bcrypt-cost:12}") int bcryptCost) {
        this.accountRepo      = accountRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.attemptRepo      = attemptRepo;
        this.auditRepo        = auditRepo;
        this.lockoutPolicy    = lockoutPolicy;
        this.jwtService       = jwtService;
        this.passwordEncoder  = new BCryptPasswordEncoder(bcryptCost);
        this.clock            = clock;
    }

    @Transactional
    public LoginResult login(String email, String password, String sourceIp) {
        String normalisedEmail = email.toLowerCase().trim();
        Instant now = Instant.now(clock);

        // ── Lockout check (before touching password at all) ──────────────
        Instant lockedUntil = lockoutPolicy.lockedUntil(normalisedEmail);
        if (lockedUntil != null) {
            recordAttempt(normalisedEmail, false, sourceIp, now);
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "ADMIN_ACCOUNT_LOCKED: Too many failed attempts. " +
                    "Try again after " + lockedUntil + ".");
        }

        // ── Credential check ──────────────────────────────────────────────
        Optional<AdminAccountEntity> accountOpt = accountRepo.findByEmailIgnoreCase(normalisedEmail);

        boolean credentialsValid = accountOpt.isPresent()
                && accountOpt.get().isActive()
                && passwordEncoder.matches(password, accountOpt.get().getPasswordHash());

        recordAttempt(normalisedEmail, credentialsValid, sourceIp, now);

        if (!credentialsValid) {
            if (accountOpt.isPresent()) {
                writeAuditRow(accountOpt.get().getId(), "ADMIN_LOGIN_FAILED",
                        "ADMIN_ACCOUNT", accountOpt.get().getId(), sourceIp, now,
                        "{\"reason\": \"invalid_credentials\"}");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTH_INVALID_CREDENTIALS: Invalid email or password.");
        }

        AdminAccountEntity account = accountOpt.get();

        // ── Issue tokens ────────────────────────────────────────────────
        String accessToken = jwtService.issueAccessToken(
                account.getId(), account.getAccountType(), account.getRoleName());

        String refreshTokenPlain = generateOpaqueToken();
        String refreshTokenHash  = sha256Hex(refreshTokenPlain);

        AdminRefreshTokenEntity tokenEntity = AdminRefreshTokenEntity.issue(
                account.getId(), refreshTokenHash, sourceIp, now, REFRESH_TOKEN_INACTIVITY_WINDOW);
        refreshTokenRepo.save(tokenEntity);

        writeAuditRow(account.getId(), "ADMIN_LOGIN_SUCCESS",
                "ADMIN_ACCOUNT", account.getId(), sourceIp, now, null);

        log.info("AdminAuth: login success admin={} ip={}", account.getId(), sourceIp);

        return new LoginResult(accessToken, refreshTokenPlain, account.getAccountType());
    }

    @Transactional
    public LoginResult refresh(String refreshTokenPlain, String sourceIp) {
        String hash = sha256Hex(refreshTokenPlain);
        Instant now = Instant.now(clock);

        AdminRefreshTokenEntity token = refreshTokenRepo.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "AUTH_INVALID_REFRESH_TOKEN: Refresh token not recognised."));

        // ── Replay detection ──────────────────────────────────────────────
        if (token.getRevokedAt() != null) {
            if ("ROTATION".equals(token.getRevokedReason())) {
                revokeChain(token, "ROTATION_REPLAY");
                log.error("[P0_ALERT] AdminAuth: refresh token replay detected for admin={}",
                        token.getAdminAccountId());
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTH_INVALID_REFRESH_TOKEN: This session has been revoked. Please log in again.");
        }

        if (!token.isActive(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTH_INVALID_REFRESH_TOKEN: Session expired. Please log in again.");
        }

        // ── IP binding check ───────────────────────────────────────────────
        if (!token.getIpAddress().equals(sourceIp)) {
            token.revoke("IP_MISMATCH");
            refreshTokenRepo.save(token);

            writeAuditRow(token.getAdminAccountId(), "ADMIN_REFRESH_IP_MISMATCH",
                    "ADMIN_ACCOUNT", token.getAdminAccountId(), sourceIp, now,
                    "{\"original_ip\": \"" + token.getIpAddress() + "\", \"new_ip\": \"" + sourceIp + "\"}");

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "ADMIN_IP_MISMATCH: Session was issued from a different network. " +
                    "Please log in again.");
        }

        AdminAccountEntity account = accountRepo.findById(token.getAdminAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Admin account no longer exists."));

        if (!account.isActive()) {
            token.revoke("DEACTIVATED");
            refreshTokenRepo.save(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Admin account has been deactivated.");
        }

        // ── Rotate ───────────────────────────────────────────────────────
        String newAccessToken = jwtService.issueAccessToken(
                account.getId(), account.getAccountType(), account.getRoleName());

        String newRefreshTokenPlain = generateOpaqueToken();
        String newRefreshTokenHash  = sha256Hex(newRefreshTokenPlain);

        AdminRefreshTokenEntity newToken = AdminRefreshTokenEntity.issue(
                account.getId(), newRefreshTokenHash, sourceIp, now, REFRESH_TOKEN_INACTIVITY_WINDOW);
        newToken = refreshTokenRepo.save(newToken);

        token.revoke("ROTATION");
        token.linkReplacement(newToken.getId());
        refreshTokenRepo.save(token);

        writeAuditRow(account.getId(), "ADMIN_TOKEN_REFRESH",
                "ADMIN_ACCOUNT", account.getId(), sourceIp, now, null);

        return new LoginResult(newAccessToken, newRefreshTokenPlain, account.getAccountType());
    }

    private void revokeChain(AdminRefreshTokenEntity start, String reason) {
        AdminRefreshTokenEntity current = start;
        while (current != null) {
            if (current.getRevokedAt() == null
                    || "ROTATION".equals(current.getRevokedReason())) {
                current.revoke(reason);
                refreshTokenRepo.save(current);
            }
            current = current.getReplacedById() != null
                    ? refreshTokenRepo.findById(current.getReplacedById()).orElse(null)
                    : null;
        }
    }

    private void recordAttempt(String email, boolean succeeded, String ip, Instant now) {
        attemptRepo.save(AdminLoginAttemptEntity.record(email, succeeded, ip, now));
    }

    private void writeAuditRow(UUID adminId, String actionType, String targetType,
                                UUID targetId, String ip, Instant now, String payloadJson) {
        auditRepo.save(AdminAuditActionEntity.create(
                adminId, actionType, targetType, targetId, payloadJson, ip, now));
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record LoginResult(String accessToken, String refreshToken, String accountType) {}
}
