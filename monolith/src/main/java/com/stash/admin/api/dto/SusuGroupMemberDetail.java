package com.stash.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SusuGroupMemberDetail(
        UUID userId, String displayName, Integer rotationPosition, String status, Instant joinedAt) {}
