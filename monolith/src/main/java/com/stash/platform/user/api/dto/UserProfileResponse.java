package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;


/**
 * Response for GET /api/v1/users/me.
 *
 * <p>Per acceptance criteria: NEVER includes password_hash or
 * ghana_card_number. Only the explicitly listed fields are exposed.
 */

@JsonInclude(JsonInclude.Include.ALWAYS)

public record UserProfileResponse(

        UUID id,

        String email,

        @JsonProperty("display_name")       String displayName,

        String phone,

        @JsonProperty("kyc_status")         String kycStatus,

        @JsonProperty("subscription_tier")  String subscriptionTier,

        @JsonProperty("account_status")     String accountStatus,

        @JsonProperty("email_verified_at")  Instant emailVerifiedAt,

        @JsonProperty("created_at")         Instant createdAt

) {}
