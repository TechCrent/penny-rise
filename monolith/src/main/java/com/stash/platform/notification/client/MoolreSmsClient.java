package com.stash.platform.notification.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.stash.platform.notification.service.SmsMessage;
import com.stash.platform.notification.service.SmsSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

/**
 * Thin HTTP client for Moolre's Send SMS API ({@code POST /open/sms/send}).
 *
 * <p>Auth: {@code X-API-VASKEY}. Recipient and message body are never logged
 * at INFO or above.
 */
@Component
public class MoolreSmsClient {

    private static final Logger log = LoggerFactory.getLogger(MoolreSmsClient.class);

    private final WebClient webClient;
    private final String baseUrl;
    private final String vasKey;
    private final String senderId;

    public MoolreSmsClient(
            WebClient correlationAwareWebClient,
            @Value("${stash.moolre.sms.base-url:https://sandbox.moolre.com}") String baseUrl,
            @Value("${stash.moolre.sms.vas-key:}") String vasKey,
            @Value("${stash.moolre.sms.sender-id:PennyRise}") String senderId) {
        this.webClient = correlationAwareWebClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.vasKey = vasKey;
        this.senderId = senderId;
        log.info("MoolreSmsClient initialised: baseUrl={}", this.baseUrl);
    }

    public void send(SmsMessage message) {
        // DO NOT log message.to() or message.body()
        log.debug("Posting SMS to Moolre");

        var body = new MoolreSendRequest(
                1,
                senderId,
                List.of(new MoolreMessageItem(message.to(), message.body(), message.ref()))
        );

        JsonNode response;
        try {
            response = webClient.post()
                    .uri(baseUrl + "/open/sms/send")
                    .header("X-API-VASKEY", vasKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new SmsSendException(
                    "Moolre SMS HTTP " + e.getStatusCode().value() + ": " + e.getStatusText(), e);
        } catch (Exception e) {
            throw new SmsSendException("Moolre SMS request failed: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new SmsSendException("Moolre SMS returned empty response");
        }

        int status = response.path("status").asInt(-1);
        if (status != 1) {
            String code = response.path("code").asText("?");
            String msg = response.path("message").asText("unknown error");
            throw new SmsSendException("Moolre SMS rejected: code=" + code + " message=" + msg);
        }

        log.info("Moolre SMS accepted");
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    record MoolreSendRequest(int type, String senderid, List<MoolreMessageItem> messages) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MoolreMessageItem(String recipient, String message, String ref) {}
}
