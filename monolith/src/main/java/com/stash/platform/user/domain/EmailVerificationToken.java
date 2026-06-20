monolith/src/main/java/com/stash/platform/user/domain/EmailVerificationToken.java

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
 * JPA entity for user_module.email_verification_tokens.
 * See Schema doc §1.3.
 */
@Entity
@Table(schema = "user_module", name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationToken {

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

    public EmailVerificationToken(UUID userId, String tokenHash) {
        this.id        = UuidV7Generator.generate();
        this.userId    = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
    }

    public boolean isConsumed() { return consumedAt != null; }
    public boolean isExpired()  { return Instant.now().isAfter(expiresAt); }
    public boolean isValid()    { return !isConsumed() && !isExpired(); }
}