package com.stash.platform.user.service;

import com.stash.platform.user.domain.BetaAllowlistEntry;
import com.stash.platform.user.repository.BetaAllowlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BetaAllowlistServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);

    private final BetaAllowlistRepository repo = Mockito.mock(BetaAllowlistRepository.class);

    // Gate-enabled service (normal production behaviour)
    private final BetaAllowlistService gatedService =
            new BetaAllowlistService(repo, FIXED_CLOCK, true);

    // Gate-disabled service (test / dev profiles)
    private final BetaAllowlistService ungatedService =
            new BetaAllowlistService(repo, FIXED_CLOCK, false);

    // ── assertAllowed — gate ON ───────────────────────────────────────────────

    @Test
    @DisplayName("assertAllowed: email on allowlist passes without exception")
    void assertAllowed_emailOnList_passes() {
        when(repo.existsByEmailIgnoreCase("user@stash.test")).thenReturn(true);

        assertThatCode(() -> gatedService.assertAllowed("user@stash.test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertAllowed: email not on allowlist throws 403")
    void assertAllowed_emailNotOnList_throws403() {
        when(repo.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        assertThatThrownBy(() -> gatedService.assertAllowed("stranger@stash.test"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BETA_ACCESS_REQUIRED");
    }

    @Test
    @DisplayName("assertAllowed: check is case-insensitive — upper-case email matched")
    void assertAllowed_caseInsensitive() {
        // repo.existsByEmailIgnoreCase receives a normalised (lowercased) email
        when(repo.existsByEmailIgnoreCase("user@stash.test")).thenReturn(true);

        assertThatCode(() -> gatedService.assertAllowed("USER@STASH.TEST"))
                .doesNotThrowAnyException();

        verify(repo).existsByEmailIgnoreCase("user@stash.test");
    }

    @Test
    @DisplayName("assertAllowed: leading/trailing whitespace is stripped before lookup")
    void assertAllowed_whitespaceStripped() {
        when(repo.existsByEmailIgnoreCase("user@stash.test")).thenReturn(true);

        assertThatCode(() -> gatedService.assertAllowed("  user@stash.test  "))
                .doesNotThrowAnyException();

        verify(repo).existsByEmailIgnoreCase("user@stash.test");
    }

    // ── assertAllowed — gate OFF ──────────────────────────────────────────────

    @Test
    @DisplayName("assertAllowed: gate disabled — everyone passes regardless of DB")
    void assertAllowed_gateDisabled_alwaysPasses() {
        // even with a non-existent email, no exception
        assertThatCode(() -> ungatedService.assertAllowed("anyone@stash.test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertAllowed: gate disabled — no DB query is made")
    void assertAllowed_gateDisabled_noDbQuery() {
        ungatedService.assertAllowed("anyone@stash.test");

        verifyNoInteractions(repo);
    }

    // ── addEmail ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addEmail: saves a new entry with normalised email")
    void addEmail_newEmail_savesEntry() {
        when(repo.existsByEmailIgnoreCase("new@stash.test")).thenReturn(false);

        gatedService.addEmail("NEW@STASH.TEST", "admin");

        ArgumentCaptor<BetaAllowlistEntry> captor =
                ArgumentCaptor.forClass(BetaAllowlistEntry.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@stash.test");
        assertThat(captor.getValue().getAddedBy()).isEqualTo("admin");
        assertThat(captor.getValue().getAddedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    @Test
    @DisplayName("addEmail: no-op if email already in allowlist")
    void addEmail_alreadyExists_noSave() {
        when(repo.existsByEmailIgnoreCase("existing@stash.test")).thenReturn(true);

        gatedService.addEmail("existing@stash.test", "admin");

        verify(repo, never()).save(any());
    }

    // ── removeEmail ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeEmail: deletes entry when found")
    void removeEmail_found_deletes() {
        BetaAllowlistEntry entry = BetaAllowlistEntry.create(
                "user@stash.test", "admin", FIXED_CLOCK.instant());
        when(repo.findByEmailIgnoreCase("user@stash.test")).thenReturn(Optional.of(entry));

        gatedService.removeEmail("user@stash.test");

        verify(repo).delete(entry);
    }

    @Test
    @DisplayName("removeEmail: no-op if email not present")
    void removeEmail_notFound_noOp() {
        when(repo.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> gatedService.removeEmail("ghost@stash.test"))
                .doesNotThrowAnyException();

        verify(repo, never()).delete(any());
    }

    // ── isGateEnabled ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isGateEnabled reflects constructor argument")
    void isGateEnabled_returnsCorrectValue() {
        assertThat(gatedService.isGateEnabled()).isTrue();
        assertThat(ungatedService.isGateEnabled()).isFalse();
    }
}
