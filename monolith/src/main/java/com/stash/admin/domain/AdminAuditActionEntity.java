package com.stash.admin.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_actions", schema = "admin")
public class AdminAuditActionEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "admin_account_id", nullable = false) private UUID    adminAccountId;
    @Column(name = "action_type",      nullable = false) private String  actionType;
    @Column(name = "target_type",      nullable = false) private String  targetType;
    @Column(name = "target_id",        nullable = false) private UUID    targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "ip_address")                         private String  ipAddress;
    @Column(name = "created_at",       nullable = false) private Instant createdAt;

    protected AdminAuditActionEntity() {}

    public static AdminAuditActionEntity create(UUID adminAccountId, String actionType,
                                                String targetType, UUID targetId,
                                                String payloadJson, String ipAddress,
                                                Instant now) {
        AdminAuditActionEntity a = new AdminAuditActionEntity();
        a.adminAccountId = adminAccountId;
        a.actionType     = actionType;
        a.targetType     = targetType;
        a.targetId       = targetId;
        a.payloadJson    = payloadJson;
        a.ipAddress      = ipAddress;
        a.createdAt      = now;
        return a;
    }

    public UUID    getId()             { return id; }
    public UUID    getAdminAccountId() { return adminAccountId; }
    public String  getActionType()     { return actionType; }
    public String  getTargetType()     { return targetType; }
    public UUID    getTargetId()       { return targetId; }
    public String  getPayloadJson()    { return payloadJson; }
    public String  getIpAddress()      { return ipAddress; }
    public Instant getCreatedAt()      { return createdAt; }

    // No setters and no update/mutation methods — this table is append-only
    // by convention and (per V21_1) by DB grant.
}
