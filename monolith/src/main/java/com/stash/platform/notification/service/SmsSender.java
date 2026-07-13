package com.stash.platform.notification.service;

import com.stash.platform.notification.client.MoolreSmsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single dispatch point for outbound SMS in the Stash platform.
 *
 * <p>Routing:
 * <ul>
 *   <li>{@code stash.sms.provider=moolre} — sends via {@link MoolreSmsClient}</li>
 *   <li>{@code stash.sms.provider=noop} — no-op (default; safe for local/dev)</li>
 * </ul>
 *
 * <p><strong>PII invariants — never violate:</strong>
 * <ul>
 *   <li>Recipient phone is never logged at INFO or above.</li>
 *   <li>SMS body is never logged at INFO or above.</li>
 * </ul>
 */
@Service
public class SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);

    private final String provider;
    private final MoolreSmsClient moolreSmsClient;

    public SmsSender(
            @Value("${stash.sms.provider:noop}") String provider,
            MoolreSmsClient moolreSmsClient) {
        this.provider = provider;
        this.moolreSmsClient = moolreSmsClient;
        log.info("SmsSender initialised: provider={}", provider);
    }

    /**
     * Dispatches an SMS via the configured provider.
     *
     * @param message the SMS to send — recipient and body are never logged at INFO+
     * @throws SmsSendException if the provider rejects the send or is unknown
     */
    public void send(SmsMessage message) {
        // DO NOT log message.to() or message.body()
        log.debug("Dispatching SMS via provider={}", provider);

        switch (provider.toLowerCase()) {
            case "moolre" -> moolreSmsClient.send(message);
            case "noop" -> log.debug("SMS noop — skipping send");
            default -> throw new SmsSendException("Unknown SMS provider: " + provider);
        }

        log.info("SMS dispatched successfully via provider={}", provider);
    }
}
