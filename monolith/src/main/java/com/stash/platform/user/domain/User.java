package com.stash.platform.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for user_module.users — the canonical user account.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>UUID v7 primary key generated at the application layer by
 *       {@link com.stash.shared.uuidv7.UuidV7Generator} in the constructor.
 *       The DB column is plain UUID; no DB-side generation function.</li>
 *   <li>Enum fields use {@link EnumType#STRING} so the DB stores the name
 *       ('PENDING', 'FREE', 'ACTIVE') and a rename in Java requires a
 *       migration rather than silently breaking existing data.</li>
 *   <li>Soft delete via {@code deletedAt}. NULL = active. Queries that
 *       should not see deleted users go through the repository, which
 *       applies the filter automatically.</li>
 *   <li>No @OneToMany relationships on this entity. Other modules store
 *       user_id as a UUID (logical reference), never as a JPA FK
 *       into this entity. See Module Boundaries doc Rule 4.</li>
 * </ul>
 *
 * <p>See Schema doc §1.2 for the canonical field reference.
 */
@Entity
@Table(
        schema  = "user_module",
        name    = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "users_email_unique", columnNames = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class User {

    // ── Identity ──────────────────────────────────────────────────────────

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    // ── Contact ───────────────────────────────────────────────────────────

    /** E.164 format. Optional. Unique where non-NULL (partial index in DB). */
    @Column(name = "phone", length = 20)
    private String phone;

    // ── KYC ───────────────────────────────────────────────────────────────

    /**
     * Set only after KYC approval. Unique where non-NULL (partial index in DB).
     * The raw submission lives in the KYC Service; this is the canonical
     * approved value on the user record.
     */
    @Column(name = "ghana_card_number", length = 20)
    private String ghanaCardNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 50)
    private KycStatus kycStatus;

    // ── Subscription ──────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 50)
    private SubscriptionTier subscriptionTier;

    // ── Account lifecycle ─────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 50)
    private AccountStatus accountStatus;

    @Column(name = "referred_by_code", length = 50)
    private String referredByCode;

    /** Set at signup when the user accepts the ToS/Privacy Policy. NULL predates consent capture. */
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    // ── Timestamps ────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * NULL = active. Non-NULL = soft-deleted.
     * Set by the deletion flow after the 30-day cool-off period.
     * Never set this directly — use a dedicated deletion service.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ── Factory constructor ───────────────────────────────────────────────

    /**
     * Creates a new active user with UUID v7 primary key and sensible defaults.
     * The caller must provide email, passwordHash, and displayName at minimum.
     *
     * @param email        lowercased email address
     * @param passwordHash BCrypt hash (cost 12) — never the plaintext
     * @param displayName  user's chosen display name
     */
    public User(String email, String passwordHash, String displayName) {
        this.id               = com.stash.shared.uuidv7.UuidV7Generator.generate();
        this.email            = email;
        this.passwordHash     = passwordHash;
        this.displayName      = displayName;
        this.kycStatus        = KycStatus.PENDING;
        this.subscriptionTier = SubscriptionTier.FREE;
        this.accountStatus    = AccountStatus.ACTIVE;
        this.createdAt        = Instant.now();
    }

    // ── Domain helpers ────────────────────────────────────────────────────

    /** Returns true if this user has been soft-deleted. */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Returns true if the user's email has been verified. */
    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** Returns true if the user has passed KYC. */
    public boolean isKycApproved() {
        return KycStatus.APPROVED.equals(kycStatus);
    }

    /** Returns true if the account is currently active. */
    public boolean isActive() {
        return AccountStatus.ACTIVE.equals(accountStatus);
    }

    /**
     * Soft-deletes this user. Call from the deletion service only.
     * Setting deleted_at makes the user invisible to all normal repository queries.
     */
    public void softDelete() {
        this.deletedAt     = Instant.now();
        this.accountStatus = AccountStatus.CLOSED;
    }
}