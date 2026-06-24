package com.stash.platform.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Single dispatch point for all outbound emails in the Stash platform.
 *
 * <p>Routing:
 * <ul>
 *   <li>{@code stash.email.provider=smtp} — sends via Spring Mail (Mailpit locally)</li>
 *   <li>{@code stash.email.provider=resend} — sends via Resend HTTP API</li>
 * </ul>
 *
 * <p><strong>PII invariants — never violate:</strong>
 * <ul>
 *   <li>Recipient address is never logged at INFO or above.</li>
 *   <li>Email body is never logged at INFO or above.</li>
 *   <li>Subject line is never logged at INFO or above.</li>
 *   <li>DEBUG logging of recipient may be used in local-only dev builds.</li>
 * </ul>
 *
 * <p>Failures retry with exponential backoff: 3 attempts, doubling delay
 * starting at 1 second. After 3 failures, {@link #recoverFromSendFailure}
 * is called and an error is logged with the correlation of the failure
 * (no PII in the error log).
 */
@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String provider;
    private final String fromAddress;
    private final String fromName;
    private final String resendApiKey;
    private final JavaMailSender mailSender;
    private final WebClient webClient;

    public EmailSender(
            @Value("${stash.email.provider:smtp}") String provider,
            @Value("${stash.email.from-address}") String fromAddress,
            @Value("${stash.email.from-name}") String fromName,
            @Value("${stash.email.resend-api-key:}") String resendApiKey,
            JavaMailSender mailSender,
            WebClient correlationAwareWebClient) {

        this.provider      = provider;
        this.fromAddress   = fromAddress;
        this.fromName      = fromName;
        this.resendApiKey  = resendApiKey;
        this.mailSender    = mailSender;
        this.webClient     = correlationAwareWebClient;

        // DO NOT log fromAddress or resendApiKey
        log.info("EmailSender initialised: provider={}", provider);
    }

    /**
     * Dispatches an email. Retries up to 3 times with exponential backoff
     * on any exception (transient Resend outage, SMTP connection failure, etc.)
     *
     * @param message the email to send — recipient and body are never logged
     * @throws EmailSendException if all retry attempts are exhausted
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void send(EmailMessage message) {
        // DO NOT log message.to(), message.subject(), or message.htmlBody()
        log.debug("Dispatching email via provider={}", provider);

        switch (provider.toLowerCase()) {
            case "resend" -> sendViaResend(message);
            case "smtp"   -> sendViaSmtp(message);
            default -> throw new EmailSendException(
                    "Unknown email provider: " + provider);
        }

        log.info("Email dispatched successfully via provider={}", provider);
        // No recipient or subject logged above.
    }

    /**
     * Recovery method called after all retry attempts fail.
     * Logs the failure without PII.
     */
    @Recover
    public void recoverFromSendFailure(Exception e, EmailMessage message) {
        // Log failure without message.to() or message.subject()
        log.error("Email send failed after all retry attempts via provider={}: {}",
                provider, e.getMessage());
        throw new EmailSendException(
                "Email delivery failed after 3 attempts: " + e.getMessage(), e);
    }

    // ── Template factory methods ───────────────────────────────────────────

    /**
     * Builds and sends an email verification email.
     *
     * @param recipientEmail the user's email address
     * @param displayName    the user's display name for personalisation
     * @param verificationUrl the full verification URL (token embedded)
     */
    public void sendEmailVerification(String recipientEmail,
                                      String displayName,
                                      String verificationUrl) {
        String html = EmailTemplates.verification(displayName, verificationUrl);
        String text = "Verify your Stash account: " + verificationUrl;

        send(new EmailMessage(
                recipientEmail,
                "Verify your Stash account",
                html,
                text
        ));
    }

    /**
     * Builds and sends a password reset email.
     *
     * @param recipientEmail the user's email address
     * @param displayName    the user's display name
     * @param resetUrl       the full reset URL (token embedded)
     */
    public void sendPasswordReset(String recipientEmail,
                                  String displayName,
                                  String resetUrl) {
        String html = EmailTemplates.passwordReset(displayName, resetUrl);
        String text = "Reset your Stash password: " + resetUrl;

        send(new EmailMessage(
                recipientEmail,
                "Reset your Stash password",
                html,
                text
        ));
    }

    /**
     * Builds and sends a refresh-token replay security alert.
     * Preview only at v0.2 — fully wired in v0.5.
     *
     * @param recipientEmail the user's email address
     * @param displayName    the user's display name
     */
    public void sendReplaySecurityAlert(String recipientEmail, String displayName) {
        String html = EmailTemplates.replayAlert(displayName);
        String text = "Security alert: unusual activity detected on your Stash account. "
                + "All your sessions have been signed out. "
                + "If this wasn't you, please contact support immediately.";

        send(new EmailMessage(
                recipientEmail,
                "Security alert: your Stash account sessions were revoked",
                html,
                text
        ));
    }

    // ── Private dispatch implementations ──────────────────────────────────

    private void sendViaSmtp(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mime);
        } catch (Exception e) {
            throw new EmailSendException("SMTP send failed: " + e.getMessage(), e);
        }
    }

    private void sendViaResend(EmailMessage message) {
        var body = Map.of(
                "from",    fromName + " <" + fromAddress + ">",
                "to",      new String[]{message.to()},
                "subject", message.subject(),
                "html",    message.htmlBody(),
                "text",    message.textBody()
        );

        webClient.post()
                .uri(RESEND_API_URL)
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
        // WebClient throws on 4xx/5xx — Spring Retry picks that up
    }
}