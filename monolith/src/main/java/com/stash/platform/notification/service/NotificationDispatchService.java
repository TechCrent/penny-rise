package com.stash.platform.notification.service;

import com.stash.platform.notification.client.ExpoPushClient;
import com.stash.platform.notification.client.ExpoPushClient.ExpoPushResult;
import com.stash.platform.notification.domain.DeviceTokenLike;
import com.stash.platform.notification.domain.NotificationEntity;
import com.stash.platform.notification.domain.ProcessedWorkerEventEntity;
import com.stash.platform.notification.event.EventEnvelope;
import com.stash.platform.notification.repository.DeviceTokenRepository;
import com.stash.platform.notification.repository.NotificationRepository;
import com.stash.platform.notification.repository.ProcessedWorkerEventRepository;
import com.stash.platform.notification.template.NotificationTemplateRegistry;
import com.stash.platform.notification.template.RenderedNotification;
import com.stash.platform.notification.util.GhanaPhoneFormatter;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Retry model: synchronous, in-listener — up to 3 Expo HTTP attempts per push
 * notification, blocking the message-handling thread for a few seconds
 * worst-case. Email relies on EmailSender's own @Retryable (3 attempts,
 * exponential backoff). This is one valid reading of the AC's "retries up to
 * 3 times with exponential backoff"; the async-sweep alternative (rows
 * genuinely PENDING across multiple worker polls) would need a last_attempted_at
 * column and a @Scheduled component — not built here.
 *
 * userEmailFor() is an explicit gap — flagged in this issue's notes. It needs
 * UserService (v0.5-005) wired before email dispatch works end-to-end.
 *
 * SMS failures are soft: logged and recorded on the SMS channel row, but never
 * rethrown — a deposit notification must not fail because SMS delivery failed.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final int    MAX_PUSH_ATTEMPTS = 3;
    private static final long   INITIAL_BACKOFF_MS = 500;

    private final ProcessedWorkerEventRepository processedEventRepository;
    private final NotificationRepository         notificationRepository;
    private final DeviceTokenRepository          deviceTokenRepository;
    private final NotificationTemplateRegistry   templateRegistry;
    private final NotificationPreferencesService preferencesService;
    private final ExpoPushClient                 expoPushClient;
    private final EmailSender                    emailSender;
    private final SmsSender                      smsSender;
    private final UserRepository                 userRepository;
    private final Clock                          clock;
    private final Counter                        dispatchFailureCounter;
    private final Counter                        dispatchAttemptCounter;

    public NotificationDispatchService(ProcessedWorkerEventRepository processedEventRepository,
                                        NotificationRepository notificationRepository,
                                        DeviceTokenRepository deviceTokenRepository,
                                        NotificationTemplateRegistry templateRegistry,
                                        NotificationPreferencesService preferencesService,
                                        ExpoPushClient expoPushClient,
                                        EmailSender emailSender,
                                        SmsSender smsSender,
                                        UserRepository userRepository,
                                        Clock clock,
                                        MeterRegistry meterRegistry) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository   = notificationRepository;
        this.deviceTokenRepository    = deviceTokenRepository;
        this.templateRegistry         = templateRegistry;
        this.preferencesService       = preferencesService;
        this.expoPushClient           = expoPushClient;
        this.emailSender              = emailSender;
        this.smsSender                = smsSender;
        this.userRepository           = userRepository;
        this.clock                    = clock;
        // Prometheus's Counter naming convention appends _total, so this renders
        // as notification_dispatch_failure_rate_total on /actuator/prometheus.
        this.dispatchFailureCounter   = meterRegistry.counter("notification.dispatch.failure.rate");
        // v0.5-027: companion counter so a failure *rate* (not just a raw
        // failure count) can be graphed — renders as
        // notification_dispatch_attempts_total. Incremented once per channel
        // dispatch attempted (once per dispatchPushWithRetry call, once per
        // dispatchEmail call), not per internal retry-loop iteration.
        this.dispatchAttemptCounter   = meterRegistry.counter("notification.dispatch.attempts");
    }

    @Transactional
    public void handle(EventEnvelope envelope) {
        if (processedEventRepository.existsByEventId(envelope.eventId())) {
            log.debug("Duplicate event_id={} — already processed, no-op", envelope.eventId());
            return;
        }

        var templateOpt = templateRegistry.find(envelope.eventType());
        if (templateOpt.isEmpty()) {
            markProcessed(envelope);
            return;
        }
        var template = templateOpt.get();
        RenderedNotification rendered = template.render(envelope.payload());

        if (!preferencesService.isEnabled(rendered.userId(), rendered.notificationType())) {
            markProcessed(envelope);
            return;
        }

        // In-app: always synchronous DB write — no network, always succeeds.
        persistDelivered(rendered, "IN_APP");

        // Push: one row + one Expo dispatch per active device.
        for (var token : deviceTokenRepository.findActiveForUser(rendered.userId())) {
            dispatchPushWithRetry(rendered, token);
        }

        // Email: only for the AC's high-priority event types.
        if (template.requiresEmail()) {
            dispatchEmail(rendered);
        }

        // SMS: opt-in per template; soft-fail so deposits aren't blocked by SMS outages.
        if (template.requiresSms()) {
            dispatchSms(rendered);
        }

        markProcessed(envelope);
    }

    void dispatchPushWithRetry(RenderedNotification rendered, DeviceTokenLike token) {
        dispatchAttemptCounter.increment();
        NotificationEntity row = persistPending(rendered, "PUSH");
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_PUSH_ATTEMPTS; attempt++) {
            ExpoPushResult result;
            try {
                result = expoPushClient.send(token.expoPushToken(), rendered.title(), rendered.body(), rendered.data());
            } catch (Exception e) {
                log.warn("Expo push attempt {}/{} failed for token={}: {}", attempt, MAX_PUSH_ATTEMPTS,
                        token.expoPushToken(), e.getMessage());
                if (attempt == MAX_PUSH_ATTEMPTS) { finalizeFailed(row, attempt); return; }
                sleep(backoffMs); backoffMs *= 2;
                continue;
            }

            if (result.success()) {
                notificationRepository.markDelivered(row.getId());
                return;
            }

            if ("DeviceNotRegistered".equals(result.errorCode())) {
                // Known-good outcome (stale token from uninstall) — NOT a failure.
                // Deliberately does NOT increment dispatchFailureCounter.
                deviceTokenRepository.deactivate(token.id());
                notificationRepository.markFailed(row.getId(), attempt);
                log.info("Deregistered stale token (DeviceNotRegistered): tokenId={}", token.id());
                return;
            }

            if (attempt == MAX_PUSH_ATTEMPTS) { finalizeFailed(row, attempt); return; }
            sleep(backoffMs); backoffMs *= 2;
        }
    }

    void dispatchEmail(RenderedNotification rendered) {
        dispatchAttemptCounter.increment();
        NotificationEntity row = persistPending(rendered, "EMAIL");
        try {
            String userEmail = userEmailFor(rendered.userId()); // UnsupportedOperationException until wired
            emailSender.send(new com.stash.platform.notification.service.EmailMessage(
                    userEmail, rendered.title(), rendered.body(), rendered.body()));
            notificationRepository.markDelivered(row.getId());
        } catch (UnsupportedOperationException e) {
            log.warn("Email dispatch skipped — userEmailFor not yet wired: {}", e.getMessage());
            finalizeFailed(row, 1);
        } catch (Exception e) {
            log.warn("Email dispatch failed for user={}: {}", rendered.userId(), e.getMessage());
            finalizeFailed(row, 1);
        }
    }

    /**
     * Soft-fail SMS path: missing phone skips cleanly; provider errors are logged
     * and recorded on the SMS row but never rethrown to the caller.
     */
    void dispatchSms(RenderedNotification rendered) {
        String rawPhone = userPhoneFor(rendered.userId());
        if (rawPhone == null || rawPhone.isBlank()) {
            log.debug("SMS dispatch skipped — no phone for userId={}", rendered.userId());
            return;
        }

        var recipientOpt = GhanaPhoneFormatter.toMoolreRecipient(rawPhone);
        if (recipientOpt.isEmpty()) {
            log.debug("SMS dispatch skipped — unrecognised phone format for userId={}", rendered.userId());
            return;
        }

        dispatchAttemptCounter.increment();
        NotificationEntity row = persistPending(rendered, "SMS");
        try {
            String body = rendered.title() + ". " + rendered.body();
            smsSender.send(new SmsMessage(recipientOpt.get(), body, row.getId().toString()));
            notificationRepository.markDelivered(row.getId());
        } catch (Exception e) {
            log.warn("SMS dispatch failed for user={}: {}", rendered.userId(), e.getMessage());
            finalizeFailed(row, 1);
            // Soft-fail: do not rethrow — deposit / event processing continues.
        }
    }

    private void finalizeFailed(NotificationEntity row, int attempts) {
        notificationRepository.markFailed(row.getId(), attempts);
        dispatchFailureCounter.increment();
    }

    private NotificationEntity persistPending(RenderedNotification rendered, String channel) {
        return notificationRepository.save(NotificationEntity.create(
                rendered.userId(), rendered.notificationType(), channel, rendered.title(),
                rendered.body(), rendered.deepLink(), rendered.data(), "PENDING", Instant.now(clock)));
    }

    private void persistDelivered(RenderedNotification rendered, String channel) {
        notificationRepository.save(NotificationEntity.create(
                rendered.userId(), rendered.notificationType(), channel, rendered.title(),
                rendered.body(), rendered.deepLink(), rendered.data(), "DELIVERED", Instant.now(clock)));
    }

    private void markProcessed(EventEnvelope envelope) {
        processedEventRepository.save(ProcessedWorkerEventEntity.create(
                envelope.eventId(), envelope.eventType(), Instant.now(clock)));
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * EXPLICIT GAP — flagged in the issue notes. Needs UserService (v0.5-005)
     * injected and a real lookup before email dispatch works end-to-end.
     */
    private String userEmailFor(UUID userId) {
        throw new UnsupportedOperationException(
                "TODO: wire to UserService to look up email for userId=" + userId);
    }

    /** Looks up the user's profile phone (E.164). Returns null if missing. */
    private String userPhoneFor(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getPhone)
                .orElse(null);
    }
}
