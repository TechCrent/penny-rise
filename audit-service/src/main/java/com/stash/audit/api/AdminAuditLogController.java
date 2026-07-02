package com.stash.audit.api;

import com.stash.audit.api.dto.AuditLogPageResponse;
import com.stash.audit.api.dto.AuditLogQueryFilter;
import com.stash.audit.security.AdminAuditLogAccessGuard;
import com.stash.audit.service.AuditLogQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-log")
public class AdminAuditLogController {

    private final AuditLogQueryService service;
    private final AdminAuditLogAccessGuard accessGuard;

    public AdminAuditLogController(AuditLogQueryService service, AdminAuditLogAccessGuard accessGuard) {
        this.service     = service;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public AuditLogPageResponse list(
            Authentication authentication,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) UUID targetEntityId,
            @RequestParam(required = false) String targetEntityType,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        accessGuard.requireAccess(authentication);

        var filter = new AuditLogQueryFilter(
                actorId, actorType, targetEntityId, targetEntityType, eventType, fromDate, toDate);

        return service.list(filter, cursor, limit);
    }
}
