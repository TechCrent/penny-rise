package com.stash.admin.dispute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisputeEntityValidatorRegistryTest {

    @ParameterizedTest(name = "a validator is registered for {0}")
    @EnumSource(RelatedEntityType.class)
    @DisplayName("every RelatedEntityType resolves to exactly one validator")
    void everyTypeHasAValidator(RelatedEntityType type) {
        DisputeEntityValidator v = mock(DisputeEntityValidator.class);
        when(v.supportedType()).thenReturn(type);
        var registry = new DisputeEntityValidatorRegistry(List.of(v));

        assertThat(registry.get(type)).isSameAs(v);
    }

    @Test
    @DisplayName("an unregistered type fails loudly at lookup, not silently")
    void missingValidatorThrows() {
        var registry = new DisputeEntityValidatorRegistry(List.of());

        assertThatThrownBy(() -> registry.get(RelatedEntityType.TRANSACTION))
                .isInstanceOf(IllegalStateException.class);
    }
}
