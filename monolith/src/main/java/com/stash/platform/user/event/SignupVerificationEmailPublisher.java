package com.stash.platform.user.event;

import com.stash.platform.notification.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the signup verification email AFTER the signup transaction commits.
 *
 * <p>{@code EmailSender.send} retries up to 3 times with exponential backoff
 * (up to ~7s) on any transient SMTP/Resend failure. Calling it synchronously
 * from inside {@code SignupService.signup()} (as this used to) held the
 * transaction — and the HTTP response — open for that whole retry window:
 * a slow/retrying send meant the new user row wasn't visible to a `findByEmail`
 * (e.g. the client's very next login attempt) until the email finally went out,
 * and a client with a shorter HTTP timeout than the retry window would see
 * signup itself appear to fail even though the account was created.
 *
 * <p>Same {@code @Async} + {@code AFTER_COMMIT} pattern as
 * {@link UserCreatedEventPublisher} — best-effort, not at-least-once; if the
 * service crashes between commit and dispatch, the email is lost and the user
 * would need to request a fresh verification link.
 */
@Component
public class SignupVerificationEmailPublisher {

    private static final Logger log = LoggerFactory.getLogger(SignupVerificationEmailPublisher.class);

    private final EmailSender emailSender;

    public SignupVerificationEmailPublisher(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignupVerificationEmailRequested(SignupVerificationEmailRequestedEvent event) {
        try {
            emailSender.sendEmailVerification(
                    event.getEmail(), event.getDisplayName(), event.getVerificationUrl());
        } catch (Exception e) {
            log.error("Failed to send signup verification email — user will need to request a new one. error={}",
                    e.getMessage());
        }
    }
}
