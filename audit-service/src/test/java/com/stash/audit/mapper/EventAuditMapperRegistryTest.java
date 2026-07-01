package com.stash.audit.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventAuditMapperRegistryTest {

    @Test
    @DisplayName("a registered mapper is found by its event type")
    void findsRegisteredMapper() {
        EventAuditMapper mapper = mock(EventAuditMapper.class);
        when(mapper.supportedEventType()).thenReturn("SomeEvent");

        var registry = new EventAuditMapperRegistry(List.of(mapper));

        assertThat(registry.find("SomeEvent")).contains(mapper);
    }

    @Test
    @DisplayName("an unregistered event type returns empty, not an exception")
    void unregisteredTypeReturnsEmpty() {
        var registry = new EventAuditMapperRegistry(List.of());

        assertThat(registry.find("NeverSeenBefore")).isEmpty();
    }
}
