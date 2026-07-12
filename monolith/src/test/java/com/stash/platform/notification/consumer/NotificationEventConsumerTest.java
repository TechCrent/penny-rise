package com.stash.platform.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.platform.notification.event.EventEnvelope;
import com.stash.platform.notification.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationEventConsumerTest {

    private final NotificationDispatchService dispatchService = mock(NotificationDispatchService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationEventConsumer consumer =
            new NotificationEventConsumer(dispatchService, objectMapper);

    @Test
    void dispatchesEventsWithAFullEnvelopeInTheBody() throws Exception {
        String body = """
                {
                  "event_id": "evt-1",
                  "event_type": "ChallengeCompleted",
                  "schema_version": "1.0",
                  "source_service": "monolith",
                  "occurred_at": "2026-07-12T00:00:00Z",
                  "payload": {"user_id": "u-1"}
                }
                """;
        consumer.onMessage(message(body, Map.of("event_id", "evt-1", "event_type", "ChallengeCompleted")));

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(dispatchService).handle(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo("evt-1");
        assertThat(captor.getValue().eventType()).isEqualTo("ChallengeCompleted");
        assertThat(captor.getValue().payload().get("user_id").asText()).isEqualTo("u-1");
    }

    @Test
    void dispatchesEventsWhoseEventIdAndTypeAreOnlyInHeadersWithAFlatBody() throws Exception {
        // Matches UserCreatedEventPublisher / OutboxRelay: event_id/event_type
        // are AMQP headers only, and the body is the raw payload with no
        // envelope wrapper — this used to NPE in onMessage().
        String body = """
                {"userId": "u-1", "email": "a@example.com", "correlationId": "corr-1"}
                """;
        consumer.onMessage(message(body, Map.of(
                "event_id", "evt-2", "event_type", "user.created", "correlation_id", "corr-1")));

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(dispatchService).handle(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo("evt-2");
        assertThat(captor.getValue().eventType()).isEqualTo("user.created");
        assertThat(captor.getValue().correlationId()).isEqualTo("corr-1");
        // No "payload" field in the body — falls back to the whole body node.
        assertThat(captor.getValue().payload().get("userId").asText()).isEqualTo("u-1");
    }

    @Test
    void dropsMessagesWithNoEventIdOrTypeInHeadersOrBodyInsteadOfThrowing() throws Exception {
        String body = """
                {"ledgerAccountId": "la-1", "userId": "u-1"}
                """;
        consumer.onMessage(message(body, Map.of()));

        verify(dispatchService, never()).handle(any());
    }

    private static Message message(String body, Map<String, Object> headers) {
        MessageProperties props = new MessageProperties();
        headers.forEach(props::setHeader);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }
}
