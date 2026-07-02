package com.stash.audit.repository;

import java.time.Instant;
import java.util.UUID;

public record CursorPosition(UUID id, Instant occurredAt) {}
