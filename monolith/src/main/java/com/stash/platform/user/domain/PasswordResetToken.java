package com.stash.platform.user.domain;

import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JPA entity for user_module.password_reset_tokens.
 * See Schema doc §1.5 (added in v0.2-005).
 */
@Entity
@Table(schema = "user_module", name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** SHA-256 hex digest — never the plaintext token. */
    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public PasswordResetToken(UUID userId, String tokenHash) {
        this.id = UuidV7Generator.generate();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
    }

    public boolean isConsumed() { return consumedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isValid() { return !isConsumed() && !isExpired(); }
}