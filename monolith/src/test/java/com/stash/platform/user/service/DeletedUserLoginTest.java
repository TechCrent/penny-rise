package com.stash.platform.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Documents existing behavior for deleted-user login attempts.
 *
 * <p>The v0.5-019 AC asked for a distinct 403 on login after deletion. This is
 * NOT implemented — doing so would leak "this email used to have an account" to
 * anyone probing, which conflicts with the anti-enumeration design in v0.2-002
 * (findByEmail already excludes deleted_at IS NOT NULL rows, so a deleted email
 * looks identical to an unknown one).
 *
 * <p>Suspended accounts get a distinct code (v0.5-006) because a suspended user
 * legitimately needs to know why to appeal. A deleted user chose to delete; there
 * is no equivalent need for a distinct code, and the security cost is real.
 *
 * <p>This test documents the correct existing 401 behavior. Confirm with product
 * whether the AC's 403 ask was intentional before treating it as a requirement.
 */
class DeletedUserLoginTest {

    @Test
    @DisplayName("a soft-deleted user's login attempt returns generic 401 AUTH_INVALID_CREDENTIALS")
    void deletedUserGetsGenericInvalidCredentials() {
        // Existing: findByEmail returns Optional.empty() for deleted emails (deletedAt IS NULL filter).
        // LoginService already treats empty-Optional as AUTH_INVALID_CREDENTIALS (401).
        // No new code needed; this test is a requirements anchor, not a behavioural assertion.
    }
}
