package com.stash.platform.notification.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("EmailSender")
class EmailSenderTest {

    // Minimal stub to avoid Spring context in unit tests
    private EmailSender smtpSender() {
        return new EmailSender("smtp", "noreply@test.local", "Stash Test",
                "", mock(JavaMailSender.class), mock(WebClient.class)) {
            @Override
            public void send(EmailMessage message) {
                LoggerFactory.getLogger(EmailSender.class)
                        .info("Email dispatched successfully via provider=smtp");
            }
        };
    }

    @Nested
    @DisplayName("PII must not appear in logs at INFO+")
    class PiiLogging {

        @Test
        @DisplayName("recipient address does not appear in INFO logs")
        void recipient_not_in_info_logs() {
            Logger logger = (Logger) LoggerFactory.getLogger(EmailSender.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);

            EmailSender sender = smtpSender();
            sender.sendEmailVerification(
                    "alice@example.com", "Alice", "http://localhost/verify?token=abc");

            List<String> infoAndAbove = appender.list.stream()
                    .filter(e -> e.getLevel().isGreaterOrEqual(Level.INFO))
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String msg : infoAndAbove) {
                assertThat(msg)
                        .as("Recipient must not appear in INFO+ logs")
                        .doesNotContain("alice@example.com");
            }

            logger.detachAppender(appender);
        }

        @Test
        @DisplayName("email subject does not appear in INFO logs")
        void subject_not_in_info_logs() {
            Logger logger = (Logger) LoggerFactory.getLogger(EmailSender.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);

            EmailSender sender = smtpSender();
            sender.sendPasswordReset("bob@example.com", "Bob", "http://localhost/reset?token=xyz");

            List<String> infoAndAbove = appender.list.stream()
                    .filter(e -> e.getLevel().isGreaterOrEqual(Level.INFO))
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String msg : infoAndAbove) {
                assertThat(msg)
                        .doesNotContain("bob@example.com")
                        .doesNotContain("Reset your Stash password");
            }

            logger.detachAppender(appender);
        }

        @Test
        @DisplayName("email body content does not appear in INFO logs")
        void body_not_in_info_logs() {
            Logger logger = (Logger) LoggerFactory.getLogger(EmailSender.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);

            EmailSender sender = smtpSender();
            sender.sendReplaySecurityAlert("carol@example.com", "Carol");

            List<String> infoAndAbove = appender.list.stream()
                    .filter(e -> e.getLevel().isGreaterOrEqual(Level.INFO))
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String msg : infoAndAbove) {
                assertThat(msg)
                        .doesNotContain("carol@example.com")
                        .doesNotContain("Security alert");
            }

            logger.detachAppender(appender);
        }
    }

    @Nested
    @DisplayName("EmailTemplates")
    class Templates {

        @Test
        @DisplayName("verification template contains the verification URL")
        void verification_contains_url() {
            String html = EmailTemplates.verification("Alice", "http://localhost/verify?t=abc123");
            assertThat(html).contains("http://localhost/verify?t=abc123");
            assertThat(html).contains("Alice");
        }

        @Test
        @DisplayName("password reset template contains the reset URL")
        void reset_contains_url() {
            String html = EmailTemplates.passwordReset("Bob", "http://localhost/reset?t=xyz");
            assertThat(html).contains("http://localhost/reset?t=xyz");
        }

        @Test
        @DisplayName("templates sanitise HTML in display names")
        void sanitises_html_in_display_name() {
            String html = EmailTemplates.verification("<script>alert(1)</script>", "http://safe.url");
            assertThat(html)
                    .doesNotContain("<script>")
                    .contains("&lt;script&gt;");
        }
    }
}