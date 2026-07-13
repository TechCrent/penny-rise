package com.stash.payments.moolre.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Generic Moolre API envelope. {@code status}/{@code message}/{@code data}/{@code go}
 * vary by endpoint (status may be int or string; data may be string or object).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MoolreEnvelope(
        JsonNode status,
        String code,
        JsonNode message,
        JsonNode data,
        JsonNode go
) {
    public boolean isSuccess() {
        if (status == null || status.isNull()) {
            return false;
        }
        if (status.isIntegralNumber()) {
            return status.asInt() == 1;
        }
        if (status.isTextual()) {
            String text = status.asText().trim();
            return "1".equals(text) || "true".equalsIgnoreCase(text);
        }
        return false;
    }

    public String dataAsText() {
        if (data == null || data.isNull()) {
            return null;
        }
        if (data.isTextual() || data.isNumber() || data.isBoolean()) {
            return data.asText();
        }
        return data.toString();
    }

    public String messageAsText() {
        if (message == null || message.isNull()) {
            return null;
        }
        if (message.isTextual()) {
            return message.asText();
        }
        if (message.isArray() && !message.isEmpty()) {
            return message.get(0).asText();
        }
        return message.toString();
    }
}
