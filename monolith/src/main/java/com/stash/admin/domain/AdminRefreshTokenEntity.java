package com.stash.admin.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_refresh_tokens", schema = "admin")
public class AdminRefreshTokenEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "admin_account_id", nullable = false) private UUID    adminAccountId;
    @Column(name = "token_hash",       nullable = false) private String  tokenHash;
    @Column(name = "ip_address",       nullable = false) private String  ipAddress;
    @Column(name = "issued_at",        nullable = false) private Instant issuedAt;
    @Column(name = "last_used_at",     nullable = false) private Instant lastUsedAt;
    @Column(name = "expires_at",       nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at")                         private Instant revokedAt;
    @Column(name = "revoked_reason")                     private String  revokedReason;
    @Column(name = "replaced_by_id")                     private UUID    replacedById;

    protected AdminRefreshTokenEntity() {}

    public static AdminRefreshTokenEntity issue(UUID adminAccountId, String tokenHash,
                                                String ipAddress, Instant now,
                                                Duration inactivityWindow) {
        AdminRefreshTokenEntity t = new AdminRefreshTokenEntity();
        t.adminAccountId = adminAccountId;
        t.tokenHash      = tokenHash;
        t.ipAddress      = ipAddress;
        t.issuedAt       = now;
        t.lastUsedAt     = now;
        t.expiresAt      = now.plus(inactivityWindow);
        return t;
    }

    public void revoke(String reason) {
        this.revokedAt     = Instant.now();
        this.revokedReason = reason;
    }

    public void linkReplacement(UUID newTokenId) {
        this.replacedById = newTokenId;
    }

    public void touch(Instant now, Duration inactivityWindow) {
        this.lastUsedAt = now;
        this.expiresAt  = now.plus(inactivityWindow);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public UUID    getId()             { return id; }
    public UUID    getAdminAccountId() { return adminAccountId; }
    public String  getTokenHash()      { return tokenHash; }
    public String  getIpAddress()      { return ipAddress; }
    public Instant getIssuedAt()       { return issuedAt; }
    public Instant getLastUsedAt()     { return lastUsedAt; }
    public Instant getExpiresAt()      { return expiresAt; }
    public Instant getRevokedAt()      { return revokedAt; }
    public String  getRevokedReason()  { return revokedReason; }
    public UUID    getReplacedById()   { return replacedById; }
}
