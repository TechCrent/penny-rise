package com.stash.payments.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.ledger.service.UserWalletProvisioningService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserCreatedEventConsumerTest {

    private final UserWalletProvisioningService provisioningService =
            Mockito.mock(UserWalletProvisioningService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserCreatedEventConsumer consumer =
            new UserCreatedEventConsumer(provisioningService, objectMapper);

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid event provisions wallet and acknowledges cleanly")
    void valid_event_provisions_wallet() {
        UUID userId = UUID.randomUUID();
        Message message = messageWith(
                """
                {"userId":"%s","email":"akua@test.com","correlationId":"corr-001"}
                """.formatted(userId));

        consumer.onUserCreated(message);

        verify(provisioningService).provisionWallet(eq(userId), any(), any());
    }

    @Test
    @DisplayName("idempotent duplicate event acknowledges without exception")
    void idempotent_duplicate_acknowledges_cleanly() {
        UUID userId = UUID.randomUUID();
        when(provisioningService.provisionWallet(any(), any(), any())).thenReturn(null);

        Message message = messageWith(
                """
                {"userId":"%s","email":"akua@test.com","correlationId":"corr-002"}
                """.formatted(userId));

        assertThatCode(() -> consumer.onUserCreated(message))
                .doesNotThrowAnyException();
    }

    // ── Dead-letter paths ─────────────────────────────────────────────────

    @Test
    @DisplayName("malformed JSON causes RuntimeException — message dead-lettered")
    void malformed_json_throws_for_dead_letter() {
        Message message = messageWith("not valid json {{{");

        assertThatThrownBy(() -> consumer.onUserCreated(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Malformed user.created event");

        verifyNoInteractions(provisioningService);
    }

    @Test
    @DisplayName("missing userId causes IllegalArgumentException — message dead-lettered")
    void missing_userId_throws_for_dead_letter() {
        Message message = messageWith(
                """
                {"email":"akua@test.com","correlationId":"corr-003"}
                """);

        assertThatThrownBy(() -> consumer.onUserCreated(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing userId");

        verifyNoInteractions(provisioningService);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static Message messageWith(String json) {
        return MessageBuilder
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .andProperties(new MessageProperties())
                .build();
    }
}
