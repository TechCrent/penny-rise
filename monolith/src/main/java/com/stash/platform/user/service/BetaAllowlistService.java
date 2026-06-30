package com.stash.platform.user.service;

import com.stash.platform.user.domain.BetaAllowlistEntry;
import com.stash.platform.user.repository.BetaAllowlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

/**
 * Manages the beta allowlist gate.
 *
 * <p><strong>Gate logic:</strong> when {@code stash.beta.allowlist-enabled = true},
 * calls to {@link #assertAllowed(String)} throw a 403 for any email not in the
 * {@code user_module.beta_allowlist} table. When the flag is false, the method
 * returns immediately — everyone is let through.
 *
 * <p><strong>Case-insensitive:</strong> the check uses {@code lower(email)} at both
 * the application and database layer. An attacker cannot bypass the gate by using
 * {@code User@stash.test} instead of {@code user@stash.test}.
 *
 * <p><strong>Gate enforcement points:</strong> this service is called from
 * {@code SignupService.signup()} and {@code LoginService.login()} — both check
 * before any user creation or token issuance. The gate cannot be bypassed by
 * calling the endpoints directly because it is enforced in the service layer,
 * not as a filter or UI restriction.
 */
@Service
public class BetaAllowlistService {

    private static final Logger log = LoggerFactory.getLogger(BetaAllowlistService.class);

    private final BetaAllowlistRepository repo;
    private final Clock                   clock;
    private final boolean                 gateEnabled;

    public BetaAllowlistService(
            BetaAllowlistRepository repo,
            Clock clock,
            @Value("${stash.beta.allowlist-enabled:true}") boolean gateEnabled) {
        this.repo        = repo;
        this.clock       = clock;
        this.gateEnabled = gateEnabled;
    }

    /**
     * Throws {@code 403 BETA_ACCESS_REQUIRED} if the gate is enabled and the email
     * is not on the allowlist. Safe to call before any user creation.
     */
    public void assertAllowed(String email) {
        if (!gateEnabled) {
            return;
        }
        String normalised = email.toLowerCase().trim();
        if (!repo.existsByEmailIgnoreCase(normalised)) {
            log.info("BetaGate: rejected email={} (not on allowlist).",
                    maskEmail(normalised));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "BETA_ACCESS_REQUIRED: Stash is currently in closed beta. " +
                    "Request an invitation at stash.app/beta.");
        }
    }

    /** Adds an email to the allowlist. No-op if already present. */
    @Transactional
    public void addEmail(String email, String addedBy) {
        String normalised = email.toLowerCase().trim();
        if (repo.existsByEmailIgnoreCase(normalised)) {
            log.info("BetaAllowlist: {} already in list, skipping add.", maskEmail(normalised));
            return;
        }
        repo.save(BetaAllowlistEntry.create(normalised, addedBy, Instant.now(clock)));
        log.info("BetaAllowlist: added email={} by={}.", maskEmail(normalised), addedBy);
    }

    /** Removes an email from the allowlist. No-op if not present. */
    @Transactional
    public void removeEmail(String email) {
        String normalised = email.toLowerCase().trim();
        repo.findByEmailIgnoreCase(normalised).ifPresent(e -> {
            repo.delete(e);
            log.info("BetaAllowlist: removed email={}.", maskEmail(normalised));
        });
    }

    public boolean isGateEnabled() { return gateEnabled; }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local  = email.substring(0, at);
        String domain = email.substring(at);
        return (local.length() <= 2 ? "***" : local.charAt(0) + "***") + domain;
    }
}
