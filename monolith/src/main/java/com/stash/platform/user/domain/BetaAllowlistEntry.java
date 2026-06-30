package com.stash.platform.user.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beta_allowlist", schema = "user_module")
public class BetaAllowlistEntry {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "email",    nullable = false) private String  email;
    @Column(name = "added_by", nullable = false) private String  addedBy;
    @Column(name = "added_at", nullable = false) private Instant addedAt;

    protected BetaAllowlistEntry() {}

    public static BetaAllowlistEntry create(String email, String addedBy, Instant now) {
        BetaAllowlistEntry e = new BetaAllowlistEntry();
        e.email   = email.toLowerCase().trim();
        e.addedBy = addedBy;
        e.addedAt = now;
        return e;
    }

    public UUID    getId()      { return id; }
    public String  getEmail()   { return email; }
    public String  getAddedBy() { return addedBy; }
    public Instant getAddedAt() { return addedAt; }
}
