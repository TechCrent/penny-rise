package com.stash.platform.user.domain;

import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for auth.refresh_tokens — one row per active or recently-revoked session.
 *
 * <p>See Schema doc §1.1 for the canonical field reference.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Only the SHA-256 hash of the token is stored. The plaintext is never persisted.</li>
 *   <li>{@code revokedAt IS NULL} means the token is active.</li>
 *   <li>{@code replacedById} points to the successor token after rotation.</li>
 * </ul>
 */
@Entity
@Table(schema = "auth", name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** SHA-256 hex digest — never the plaintext token. */
    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "device_id", nullable = false, updatable = false)
    private String deviceId;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 50)
    private String revokedReason;

    /** Points to the successor token after this one has been rotated. */
    @Column(name = "replaced_by_id")
    private UUID replacedById;

    // ── Factory ───────────────────────────────────────────────────────────

    public RefreshToken(UUID userId, String tokenHash, String deviceId,
                        String deviceLabel, String ipAddress, Instant expiresAt) {
        this.id          = UuidV7Generator.generate();
        this.userId      = userId;
        this.tokenHash   = tokenHash;
        this.deviceId    = deviceId;
        this.deviceLabel = deviceLabel;
        this.ipAddress   = ipAddress;
        this.createdAt   = Instant.now();
        this.expiresAt   = expiresAt;
    }

    // ── Domain helpers ────────────────────────────────────────────────────

    public boolean isActive() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(String reason) {
        this.revokedAt     = Instant.now();
        this.revokedReason = reason;
    }
}