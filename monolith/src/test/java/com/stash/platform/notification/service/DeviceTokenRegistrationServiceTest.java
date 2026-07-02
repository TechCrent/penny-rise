package com.stash.platform.notification.service;

import com.stash.platform.notification.domain.DeviceTokenEntity;
import com.stash.platform.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceTokenRegistrationServiceTest {

    private static final Clock  FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID   USER_ID     = UUID.randomUUID();
    private static final String VALID_TOKEN = "ExponentPushToken[abc123XYZ_-9]";

    private final DeviceTokenRepository         repository = mock(DeviceTokenRepository.class);
    private final DeviceTokenRegistrationService service    = new DeviceTokenRegistrationService(repository, FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        reset(repository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.saveAll(anyList())).thenReturn(List.of());
    }

    @Test
    @DisplayName("new token registration inserts a new active row")
    void newTokenRegistration() {
        when(repository.findByExpoPushToken(VALID_TOKEN)).thenReturn(Optional.empty());
        when(repository.findActiveOrderedByLastUsedAsc(USER_ID)).thenReturn(List.of());

        service.register(USER_ID, VALID_TOKEN, "IOS");

        verify(repository).save(argThat(t -> t.isActive() && USER_ID.equals(t.getUserId())));
    }

    @Test
    @DisplayName("existing token is refreshed (upsert), not duplicated")
    void existingTokenRefreshed() {
        DeviceTokenEntity existing = DeviceTokenEntity.create(USER_ID, null, VALID_TOKEN, "IOS",
                Instant.parse("2026-06-01T00:00:00Z"));
        when(repository.findByExpoPushToken(VALID_TOKEN)).thenReturn(Optional.of(existing));

        service.register(USER_ID, VALID_TOKEN, "ANDROID");

        assertThat(existing.getLastUsedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
        assertThat(existing.isActive()).isTrue();
        verify(repository, never()).findActiveOrderedByLastUsedAsc(any()); // limit check skipped on refresh path
    }

    @Test
    @DisplayName("6th active token evicts the least-recently-used one")
    void fiveTokenLimitEvictsOldest() {
        when(repository.findByExpoPushToken(VALID_TOKEN)).thenReturn(Optional.empty());

        List<DeviceTokenEntity> fiveExistingLruFirst = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fiveExistingLruFirst.add(DeviceTokenEntity.create(USER_ID, null, "ExponentPushToken[token" + i + "]",
                    "IOS", Instant.parse("2026-06-0" + (i + 1) + "T00:00:00Z")));
        }
        // Query is issued AFTER the new row is saved, so it reports all 6 active tokens.
        List<DeviceTokenEntity> sixTotal = new ArrayList<>(fiveExistingLruFirst);
        sixTotal.add(DeviceTokenEntity.create(USER_ID, null, VALID_TOKEN, "IOS", Instant.now(FIXED_CLOCK)));
        when(repository.findActiveOrderedByLastUsedAsc(USER_ID)).thenReturn(sixTotal);

        service.register(USER_ID, VALID_TOKEN, "IOS");

        assertThat(fiveExistingLruFirst.get(0).isActive()).isFalse(); // least-recently-used — evicted
        assertThat(fiveExistingLruFirst.get(1).isActive()).isTrue();  // everything else untouched
        verify(repository).saveAll(argThat((List<DeviceTokenEntity> list) ->
                list.size() == 1 && list.get(0) == fiveExistingLruFirst.get(0)));
    }

    @Test
    @DisplayName("invalid token format returns 422 NOTIFICATION_INVALID_TOKEN_FORMAT")
    void invalidTokenFormatReturns422() {
        assertThatThrownBy(() -> service.register(USER_ID, "not-a-real-token", "IOS"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("NOTIFICATION_INVALID_TOKEN_FORMAT");
                });

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("malformed token variants are also rejected")
    void malformedVariantsRejected() {
        for (String malformed : List.of("ExponentPushToken", "ExponentPushToken[]", "SomeOtherFormat[abc]", "")) {
            assertThatThrownBy(() -> service.register(USER_ID, malformed, "IOS"))
                    .as("token=%s", malformed)
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    @DisplayName("invalid platform value returns 400, not 422")
    void invalidPlatformReturns400() {
        assertThatThrownBy(() -> service.register(USER_ID, VALID_TOKEN, "WINDOWS_PHONE"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
