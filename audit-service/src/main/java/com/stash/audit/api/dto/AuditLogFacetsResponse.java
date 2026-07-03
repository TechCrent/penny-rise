package com.stash.audit.api.dto;

import java.util.List;

public record AuditLogFacetsResponse(List<String> eventTypes, List<String> targetEntityTypes) {}
