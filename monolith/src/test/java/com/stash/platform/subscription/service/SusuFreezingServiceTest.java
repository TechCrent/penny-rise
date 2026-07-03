package com.stash.platform.subscription.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SusuFreezingServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final SusuGroupRepository susuGroupRepository = mock(SusuGroupRepository.class);
    private final SusuFreezingService service = new SusuFreezingService(susuGroupRepository);

    private SusuGroupEntity group(String id, String name) {
        SusuGroupEntity g = mock(SusuGroupEntity.class);
        when(g.getId()).thenReturn(UUID.nameUUIDFromBytes(id.getBytes()));
        when(g.getName()).thenReturn(name);
        return g;
    }

    @Test
    @DisplayName("organiser at or under the limit (1): nothing previewed or frozen")
    void underLimitNoFreezing() {
        SusuGroupEntity onlyGroup = group("g1", "Only Group");
        when(susuGroupRepository.findActiveByOrganiserOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(onlyGroup));

        assertThat(service.previewExcessGroups(USER_ID, 1)).isEmpty();

        service.freezeExcessGroups(USER_ID, 1);
        verify(susuGroupRepository, never()).freezeIfActive(any());
    }

    @Test
    @DisplayName("2 organised groups, limit 1: the OLDEST one freezes, newest stays active")
    void excessFreezesOldestFirst() {
        UUID oldestId = UUID.nameUUIDFromBytes("oldest".getBytes());
        SusuGroupEntity oldest = group("oldest", "Oldest Group");
        SusuGroupEntity newest = group("newest", "Newest Group");
        when(susuGroupRepository.findActiveByOrganiserOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(oldest, newest));

        var preview = service.previewExcessGroups(USER_ID, 1);
        assertThat(preview).hasSize(1);
        assertThat(preview.get(0).susuGroupName()).isEqualTo("Oldest Group");

        service.freezeExcessGroups(USER_ID, 1);
        verify(susuGroupRepository).freezeIfActive(oldestId);
        verify(susuGroupRepository, times(1)).freezeIfActive(any());
    }
}
