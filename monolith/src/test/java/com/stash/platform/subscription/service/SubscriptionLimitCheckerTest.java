package com.stash.platform.subscription.service;

import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;

class SubscriptionLimitCheckerTest {

    private final SubscriptionLimitChecker checker = new SubscriptionLimitChecker(new SubscriptionPolicy());

    // ── Vault limits ─────────────────────────────────────────────────────

    @Test
    @DisplayName("FREE user AT the standard vault limit (2) is rejected with VAULT_TIER_LIMIT_EXCEEDED")
    void freeUserAtStandardVaultLimitRejected() {
        assertThatThrownBy(() -> checker.assertStandardVaultWithinLimit(SubscriptionTier.FREE, 2))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> {
                    var e = (StashApiException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VAULT_TIER_LIMIT_EXCEEDED);
                    assertThat(e.getDetails()).containsEntry("upgrade_url", "stash://subscription/upgrade");
                });
    }

    @Test
    @DisplayName("FREE user below the standard vault limit succeeds")
    void freeUserBelowStandardVaultLimitSucceeds() {
        assertThatCode(() -> checker.assertStandardVaultWithinLimit(SubscriptionTier.FREE, 1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PREMIUM user is unlimited on STANDARD vaults, regardless of count")
    void premiumUserUnlimitedStandardVaults() {
        assertThatCode(() -> checker.assertStandardVaultWithinLimit(SubscriptionTier.PREMIUM, 500))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FREE user AT the locked vault limit (1) is rejected")
    void freeUserAtLockedVaultLimitRejected() {
        assertThatThrownBy(() -> checker.assertLockedVaultWithinLimit(SubscriptionTier.FREE, 1))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> assertThat(((StashApiException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VAULT_TIER_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("PREMIUM user is unlimited on LOCKED vaults")
    void premiumUserUnlimitedLockedVaults() {
        assertThatCode(() -> checker.assertLockedVaultWithinLimit(SubscriptionTier.PREMIUM, 500))
                .doesNotThrowAnyException();
    }

    // ── Susu organiser limit ────────────────────────────────────────────

    @Test
    @DisplayName("FREE user AT the organiser limit (1) is rejected with SUSU_TIER_LIMIT_EXCEEDED")
    void freeUserAtSusuOrganiserLimitRejected() {
        assertThatThrownBy(() -> checker.assertSusuOrganiserWithinLimit(SubscriptionTier.FREE, 1))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> assertThat(((StashApiException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SUSU_TIER_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("PREMIUM user below the organiser limit (3) succeeds")
    void premiumUserBelowSusuOrganiserLimitSucceeds() {
        assertThatCode(() -> checker.assertSusuOrganiserWithinLimit(SubscriptionTier.PREMIUM, 2))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PREMIUM user AT the organiser limit (3) is rejected — PREMIUM susu is capped, not unlimited")
    void premiumUserAtSusuOrganiserLimitRejected() {
        assertThatThrownBy(() -> checker.assertSusuOrganiserWithinLimit(SubscriptionTier.PREMIUM, 3))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> assertThat(((StashApiException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SUSU_TIER_LIMIT_EXCEEDED));
    }

    // ── FROZEN blocks ────────────────────────────────────────────────────

    @Test
    @DisplayName("FROZEN vault status throws VAULT_FROZEN")
    void frozenVaultThrows() {
        assertThatThrownBy(() -> checker.assertVaultNotFrozen("FROZEN"))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> {
                    var e = (StashApiException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VAULT_FROZEN);
                });
    }

    @Test
    @DisplayName("ACTIVE vault status does not throw")
    void activeVaultDoesNotThrow() {
        assertThatCode(() -> checker.assertVaultNotFrozen("ACTIVE")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FROZEN susu group status throws SUSU_FROZEN")
    void frozenSusuGroupThrows() {
        assertThatThrownBy(() -> checker.assertSusuGroupNotFrozen("FROZEN"))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> assertThat(((StashApiException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SUSU_FROZEN));
    }

    @Test
    @DisplayName("ACTIVE susu group status does not throw")
    void activeSusuGroupDoesNotThrow() {
        assertThatCode(() -> checker.assertSusuGroupNotFrozen("ACTIVE")).doesNotThrowAnyException();
    }
}
