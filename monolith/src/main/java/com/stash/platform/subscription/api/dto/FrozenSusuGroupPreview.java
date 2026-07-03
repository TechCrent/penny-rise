package com.stash.platform.subscription.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FrozenSusuGroupPreview(
        @JsonProperty("susu_group_id") String susuGroupId,
        @JsonProperty("susu_group_name") String susuGroupName
) {}
