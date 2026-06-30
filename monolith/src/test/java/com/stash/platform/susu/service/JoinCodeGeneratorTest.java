package com.stash.platform.susu.service;

import com.stash.platform.susu.repository.SusuGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JoinCodeGeneratorTest {

    private final SusuGroupRepository groupRepo = Mockito.mock(SusuGroupRepository.class);
    private final JoinCodeGenerator   generator = new JoinCodeGenerator(groupRepo);

    @Test
    @DisplayName("generated code is 8 characters")
    void code_length_is_8() {
        when(groupRepo.findByJoinCode(anyString())).thenReturn(Optional.empty());
        assertThat(generator.generate()).hasSize(8);
    }

    @Test
    @DisplayName("generated code contains only allowed characters (no 0, O, 1, I)")
    void code_contains_only_allowed_chars() {
        when(groupRepo.findByJoinCode(anyString())).thenReturn(Optional.empty());
        for (int i = 0; i < 100; i++) {
            String code = generator.generate();
            assertThat(code).doesNotContain("0", "O", "1", "I");
            assertThat(code).matches("[A-Z2-9]{8}");
        }
    }

    @Test
    @DisplayName("collision: first candidate taken, second candidate returned")
    void collision_triggers_regeneration() {
        var existingGroup = Mockito.mock(com.stash.platform.susu.domain.SusuGroupEntity.class);
        // First call returns collision, second is unique
        when(groupRepo.findByJoinCode(anyString()))
                .thenReturn(Optional.of(existingGroup))
                .thenReturn(Optional.empty());

        String code = generator.generate();
        assertThat(code).isNotNull();
        verify(groupRepo, times(2)).findByJoinCode(anyString());
    }

    @Test
    @DisplayName("throws after MAX_ATTEMPTS consecutive collisions")
    void throws_after_max_attempts() {
        var existingGroup = Mockito.mock(com.stash.platform.susu.domain.SusuGroupEntity.class);
        when(groupRepo.findByJoinCode(anyString()))
                .thenReturn(Optional.of(existingGroup));

        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate a unique join code");
    }

    @Test
    @DisplayName("100 generated codes are all unique (statistical smoke test)")
    void codes_are_statistically_unique() {
        when(groupRepo.findByJoinCode(anyString())).thenReturn(Optional.empty());

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(100);
    }
}
