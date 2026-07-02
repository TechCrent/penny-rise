package com.stash.platform.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.platform.notification.client.ExpoPushClient;
import com.stash.platform.notification.client.ExpoPushClient.ExpoPushResult;
import com.stash.platform.notification.domain.DeviceTokenLike;
import com.stash.platform.notification.domain.NotificationEntity;
import com.stash.platform.notification.event.EventEnvelope;
import com.stash.platform.notification.repository.DeviceTokenRepository;
import com.stash.platform.notification.repository.NotificationRepository;
import com.stash.platform.notification.repository.ProcessedWorkerEventRepository;
import com.stash.platform.notification.template.NotificationTemplate;
import com.stash.platform.notification.template.NotificationTemplateRegistry;
import com.stash.platform.notification.template.RenderedNotification;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationDispatchServiceTest {

    private static final Clock        FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-01T09:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper MAPPER      = new ObjectMapper();
    private static final UUID         USER_ID     = UUID.randomUUID();

    private final ProcessedWorkerEventRepository processedEventRepository = mock(ProcessedWorkerEventRepository.class);
    private final NotificationRepository         notificationRepository   = mock(NotificationRepository.class);
    private final DeviceTokenRepository          deviceTokenRepository    = mock(DeviceTokenRepository.class);
    private final NotificationTemplate           template                 = mock(NotificationTemplate.class);
    private final NotificationTemplateRegistry   templateRegistry         = mock(NotificationTemplateRegistry.class);
    private final NotificationPreferencesService preferencesService       = mock(NotificationPreferencesService.class);
    private final ExpoPushClient                 expoPushClient           = mock(ExpoPushClient.class);
    private final EmailSender                    emailSender              = mock(EmailSender.class);
    private final MeterRegistry                  meterRegistry            = new SimpleMeterRegistry();

    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        reset(processedEventRepository, notificationRepository, deviceTokenRepository,
              template, templateRegistry, preferencesService, expoPushClient, emailSender);

        service = new NotificationDispatchService(processedEventRepository, notificationRepository,
                deviceTokenRepository, templateRegistry, preferencesService, expoPushClient,
                emailSender, FIXED_CLOCK, meterRegistry);

        when(preferencesService.isEnabled(any(), any())).thenReturn(true);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templateRegistry.find(anyString())).thenReturn(Optional.of(template));
        when(template.render(any())).thenReturn(sampleRendered());
        when(deviceTokenRepository.findActiveForUser(any())).thenReturn(List.of());
    }

    private RenderedNotification sampleRendered() {
        return new RenderedNotification(USER_ID, "DEPOSIT_SUCCESS", "Deposit successful", "body",
                "stash://vaults/x", MAPPER.createObjectNode());
    }

    private EventEnvelope sampleEnvelope(String eventId) {
        return new EventEnvelope(eventId, "DepositCompleted", "1.0", "payments",
                Instant.now(FIXED_CLOCK), "corr-1", MAPPER.createObjectNode());
    }

    @Test
    @DisplayName("happy in-app: always creates a DELIVERED IN_APP row regardless of push/email config")
    void happyInAppPersist() {
        when(template.requiresEmail()).thenReturn(false);

        service.handle(sampleEnvelope("evt-1"));

        verify(notificationRepository).save(argThat(n ->
                "IN_APP".equals(n.getChannel()) && "DELIVERED".equals(n.getDeliveryStatus())));
    }

    @Test
    @DisplayName("happy push: successful Expo response marks the row DELIVERED")
    void happyPushDispatch() {
        var token = stubToken("expo-token-1");
        when(expoPushClient.send(eq("expo-token-1"), any(), any(), any()))
                .thenReturn(new ExpoPushResult(true, null));

        service.dispatchPushWithRetry(sampleRendered(), token);

        verify(expoPushClient, times(1)).send(eq("expo-token-1"), any(), any(), any());
        verify(notificationRepository).markDelivered(any());
    }

    @Test
    @DisplayName("DeviceNotRegistered deregisters the token and does NOT increment the failure metric")
    void deviceNotRegisteredDeregistersWithoutFailureMetric() {
        var token = stubToken("stale-token");
        when(expoPushClient.send(eq("stale-token"), any(), any(), any()))
                .thenReturn(new ExpoPushResult(false, "DeviceNotRegistered"));

        service.dispatchPushWithRetry(sampleRendered(), token);

        verify(deviceTokenRepository).deactivate(token.id());
        verify(notificationRepository).markFailed(any(), eq(1));
        assertThat(meterRegistry.find("notification.dispatch.failure.rate").counter()).isNotNull();
        assertThat(meterRegistry.find("notification.dispatch.failure.rate").counter().count())
                .isZero(); // DeviceNotRegistered is NOT counted as a dispatch failure
    }

    @Test
    @DisplayName("duplicate event_id is a no-op — no notification rows created")
    void duplicateEventIdIsNoOp() {
        when(processedEventRepository.existsByEventId("evt-dup")).thenReturn(true);

        service.handle(sampleEnvelope("evt-dup"));

        verify(notificationRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("event with no registered template is marked processed and produces no notification rows")
    void unrecognisedEventTypeIsSkippedGracefully() {
        when(templateRegistry.find("SomeInternalEvent")).thenReturn(Optional.empty());
        var envelope = new EventEnvelope("evt-2", "SomeInternalEvent", "1.0", "payments",
                Instant.now(FIXED_CLOCK), null, MAPPER.createObjectNode());

        service.handle(envelope);

        verify(notificationRepository, never()).save(any());
        verify(processedEventRepository).save(any());
    }

    @Test
    @DisplayName("push failure after max retries marks row FAILED and increments failure metric")
    void pushFailureAfterMaxRetriesIncrementsFallureMetric() {
        var token = stubToken("bad-token");
        when(expoPushClient.send(any(), any(), any(), any()))
                .thenReturn(new ExpoPushResult(false, "SomeTransientError"));

        service.dispatchPushWithRetry(sampleRendered(), token);

        verify(notificationRepository).markFailed(any(), eq(3)); // MAX_PUSH_ATTEMPTS
        assertThat(meterRegistry.find("notification.dispatch.failure.rate").counter().count())
                .isEqualTo(1.0);
    }

    private DeviceTokenLike stubToken(String expoToken) {
        UUID id = UUID.randomUUID();
        return new DeviceTokenLike() {
            public UUID   id()             { return id; }
            public String expoPushToken()  { return expoToken; }
        };
    }
}
