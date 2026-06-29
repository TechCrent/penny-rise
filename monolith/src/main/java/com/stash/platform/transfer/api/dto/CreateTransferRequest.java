package com.stash.platform.transfer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateTransferRequest(
        @NotNull(message = "recipient_user_id is required")
        @JsonProperty("recipient_user_id")
        java.util.UUID recipientUserId,

        @Min(value = 1, message = "amount must be at least 1 pesewa")
        long amount,

        @JsonProperty("narrative")
        String narrative
) {}
