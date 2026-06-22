package com.stash.platform.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side counter for consecutive failed login attempts per email address.
 *
 * <p>Implements the System Design §10.5 lockout policy:
 * 5 consecutive failures within 15 minutes → 1-hour lockout.
 *
 * <p>Storage: in-memory ConcurrentHashMap. Correct for single-replica v0.2.
 * Multi-replica deployments (v1.0 onwards) should back this with Redis so
 * the counter is shared across instances. Do NOT bypass this by routing
 * login traffic to one replica — that breaks horizontal scaling.
 *
 * <p>The email address is used as the key. It is NEVER logged — the log
 * only records the userId after the user has been found. An attacker
 * cannot enumerate registered emails by probing lockout state.
 */
@Component
public class LoginAttemptTracker {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptTracker.class);

    static final int  MAX_FAILURES        = 5;
    static final long WINDOW_MINUTES      = 15L;
    static final long LOCKOUT_MINUTES     = 60L;

    private record AttemptRecord(int count, Instant windowStart, Instant lockedUntil) {
        boolean isLocked(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }
        long secondsUntilUnlock(Instant now) {
            if (lockedUntil == null) return 0;
            return Math.max(0, lockedUntil.getEpochSecond() - now.getEpochSecond());
        }
    }

    private final ConcurrentHashMap<String, AttemptRecord> store = new ConcurrentHashMap<>();

    /**
     * Returns true if the given email key is currently locked out.
     */
    public boolean isLocked(String emailKey) {
        AttemptRecord record = store.get(emailKey);
        if (record == null) return false;
        return record.isLocked(Instant.now());
    }

    /**
     * Returns the number of seconds until the lockout expires, or 0 if not locked.
     */
    public long secondsUntilUnlock(String emailKey) {
        AttemptRecord record = store.get(emailKey);
        if (record == null) return 0;
        return record.secondsUntilUnlock(Instant.now());
    }

    /**
     * Records a failed login attempt. Returns true if this failure triggered
     * a new lockout.
     *
     * @param emailKey the normalised (lowercased) email address
     * @return true if the account is now locked as a result of this failure
     */
    public boolean recordFailure(String emailKey) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_MINUTES * 60);

        AttemptRecord existing = store.get(emailKey);
        AttemptRecord updated;

        if (existing == null || existing.windowStart().isBefore(windowStart)) {
            // First failure or window has expired — start a new window
            updated = new AttemptRecord(1, now, null);
        } else {
            int newCount = existing.count() + 1;
            Instant lockedUntil = (newCount >= MAX_FAILURES)
                    ? now.plusSeconds(LOCKOUT_MINUTES * 60)
                    : null;
            updated = new AttemptRecord(newCount, existing.windowStart(), lockedUntil);
        }

        store.put(emailKey, updated);

        if (updated.isLocked(now)) {
            log.warn("Login attempt lockout triggered — {} consecutive failures", updated.count());
            return true;
        }
        return false;
    }

    /**
     * Resets the counter on successful login.
     *
     * @param emailKey the normalised email address
     */
    public void recordSuccess(String emailKey) {
        store.remove(emailKey);
    }
}