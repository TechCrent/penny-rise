package com.stash.admin.api.dto;

import java.util.UUID;

public record AdminSusuMembershipSummary(
        UUID    susuGroupId,
        String  susuGroupName,
        Integer rotationPosition,
        String  status
) {}
