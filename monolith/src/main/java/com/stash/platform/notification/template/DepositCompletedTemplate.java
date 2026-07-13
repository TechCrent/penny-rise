package com.stash.platform.notification.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Field names guessed from System Design §10's worked example
 * ("payload containing vault_id, user_id, amount").
 * Confirm against Payments' actual DepositCompleted event once visible.
 */
@Component
public class DepositCompletedTemplate implements NotificationTemplate {

    @Override
    public String supportedEventType() { return "DepositCompleted"; }

    @Override
    public boolean requiresEmail() { return true; }

    @Override
    public boolean requiresSms() { return true; }

    @Override
    public RenderedNotification render(JsonNode payload) {
        long amountPesewas = payload.get("amount").asLong();
        String cedis = String.format("%.2f", amountPesewas / 100.0);
        return new RenderedNotification(
                UUID.fromString(payload.get("user_id").asText()),
                "DEPOSIT_SUCCESS",
                "Deposit successful",
                "GHS " + cedis + " has landed in your vault.",
                "stash://vaults/" + payload.get("vault_id").asText(),
                payload);
    }
}
