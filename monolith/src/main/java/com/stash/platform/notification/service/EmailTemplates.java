package com.stash.platform.notification.service;

/**
 * Simple inline HTML email templates for v0.2.
 *
 * <p>These are intentionally minimal — a proper React Email template pipeline
 * is a public-launch-readiness item. The priority at v0.2 is a working,
 * non-branded email that conveys the right information.
 *
 * <p>Templates are pure functions — no side effects, no logging.
 */
final class EmailTemplates {

    private EmailTemplates() {}

    static String verification(String displayName, String verificationUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: sans-serif; max-width: 600px; margin: 40px auto; color: #1a1a1a;">
                  <h1 style="font-size: 24px;">Verify your Stash account</h1>
                  <p>Hi %s,</p>
                  <p>Click the button below to verify your email address.
                     This link expires in 24 hours.</p>
                  <a href="%s"
                     style="display:inline-block; padding: 12px 24px; background:#1a1a1a;
                            color:#fff; text-decoration:none; border-radius:6px;
                            margin: 16px 0;">
                    Verify email
                  </a>
                  <p style="color:#6b7280; font-size:14px;">
                    If you did not create a Stash account, you can ignore this email.
                  </p>
                </body>
                </html>
                """.formatted(sanitise(displayName), sanitise(verificationUrl));
    }

    static String passwordReset(String displayName, String resetUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: sans-serif; max-width: 600px; margin: 40px auto; color: #1a1a1a;">
                  <h1 style="font-size: 24px;">Reset your password</h1>
                  <p>Hi %s,</p>
                  <p>We received a request to reset your Stash password.
                     Click the button below to set a new password.
                     This link expires in 1 hour.</p>
                  <a href="%s"
                     style="display:inline-block; padding: 12px 24px; background:#1a1a1a;
                            color:#fff; text-decoration:none; border-radius:6px;
                            margin: 16px 0;">
                    Reset password
                  </a>
                  <p style="color:#6b7280; font-size:14px;">
                    If you did not request a password reset, please contact
                    support immediately — someone may have your email address.
                  </p>
                </body>
                </html>
                """.formatted(sanitise(displayName), sanitise(resetUrl));
    }

    static String replayAlert(String displayName) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: sans-serif; max-width: 600px; margin: 40px auto; color: #1a1a1a;">
                  <h1 style="font-size: 24px; color: #dc2626;">Security alert</h1>
                  <p>Hi %s,</p>
                  <p>We detected unusual activity on your Stash account — a sign-in session
                     token was used in a way that suggests it may have been stolen.</p>
                  <p><strong>All active sessions on your account have been signed out</strong>
                     as a precaution.</p>
                  <p>If this was you using an old backup or restoring a device, you can
                     simply log in again. If this was not you, please:</p>
                  <ol>
                    <li>Log in and change your password immediately.</li>
                    <li>Contact our support team.</li>
                  </ol>
                  <p style="color:#6b7280; font-size:14px;">
                    This is an automated security alert from Stash.
                  </p>
                </body>
                </html>
                """.formatted(sanitise(displayName));
    }

    /**
     * Basic HTML sanitiser — escapes characters that could break out of
     * an HTML context. Prevents an attacker from injecting HTML if a
     * display name contains angle brackets or quotes.
     */
    private static String sanitise(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}