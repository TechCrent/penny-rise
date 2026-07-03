package com.stash.platform.subscription.service;

import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VaultFreezingServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final VaultRepository vaultRepository = mock(VaultRepository.class);
    private final VaultFreezingService service = new VaultFreezingService(vaultRepository);

    private VaultEntity vault(String id, String name, Instant createdAt) {
        VaultEntity v = mock(VaultEntity.class);
        when(v.getId()).thenReturn(UUID.nameUUIDFromBytes(id.getBytes()));
        when(v.getName()).thenReturn(name);
        when(v.getVaultType()).thenReturn("STANDARD");
        when(v.getCreatedAt()).thenReturn(createdAt);
        return v;
    }

    @Test
    @DisplayName("under the limit: nothing previewed or frozen")
    void underLimitNoFreezing() {
        when(vaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(USER_ID, "STANDARD"))
                .thenReturn(List.of(vault("a", "Vault A", Instant.parse("2026-01-01T00:00:00Z"))));
        when(vaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(USER_ID, "LOCKED"))
                .thenReturn(List.of());

        assertThat(service.previewExcessVaults(USER_ID, 2, 1)).isEmpty();

        service.freezeExcessVaults(USER_ID, 2, 1);
        verify(vaultRepository, never()).freezeIfActive(any());
    }

    @Test
    @DisplayName("3 STANDARD vaults, limit 2: the OLDEST one freezes, newest two stay active")
    void excessFreezesOldestFirst() {
        UUID oldestId = UUID.nameUUIDFromBytes("oldest".getBytes());
        var oldest = vault("oldest", "Oldest Vault", Instant.parse("2026-01-01T00:00:00Z"));
        var middle = vault("middle", "Middle Vault", Instant.parse("2026-02-01T00:00:00Z"));
        var newest = vault("newest", "Newest Vault", Instant.parse("2026-03-01T00:00:00Z"));

        when(vaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(USER_ID, "STANDARD"))
                .thenReturn(List.of(oldest, middle, newest)); // repository returns oldest-first
        when(vaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(USER_ID, "LOCKED"))
                .thenReturn(List.of());

        var preview = service.previewExcessVaults(USER_ID, 2, 1);
        assertThat(preview).hasSize(1);
        assertThat(preview.get(0).vaultName()).isEqualTo("Oldest Vault");

        service.freezeExcessVaults(USER_ID, 2, 1);
        verify(vaultRepository).freezeIfActive(oldestId);
        verify(vaultRepository, times(1)).freezeIfActive(any());
    }

    @Test
    @DisplayName("STANDARD and LOCKED excess are both included independently")
    void bothVaultTypesConsidered() {
        when(vaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(USER_ID, "STANDARD"))
                .thenReturn(List.of(
                        vault("s1", "Standard 1", Instant.parse("2026-01-01T00:00:00Z")),
                        vault("s2", "Standard 2", Instant.parse("2026-01-02T00:00:00Z")),
                        vault("s3", "Standard 3", Instant.parse("2026-01-03T00:00:00Z"))));
        when(vaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(USER_ID, "LOCKED"))
                .thenReturn(List.of(
                        vault("l1", "Locked 1", Instant.parse("2026-01-01T00:00:00Z")),
                        vault("l2", "Locked 2", Instant.parse("2026-01-02T00:00:00Z"))));

        var preview = service.previewExcessVaults(USER_ID, 2, 1);

        assertThat(preview).hasSize(2); // 1 excess STANDARD + 1 excess LOCKED
    }
}
