package com.stash.admin.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_login_attempts", schema = "admin")
public class AdminLoginAttemptEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "email",        nullable = false) private String  email;
    @Column(name = "succeeded",    nullable = false) private boolean succeeded;
    @Column(name = "ip_address")                     private String  ipAddress;
    @Column(name = "attempted_at", nullable = false) private Instant attemptedAt;

    protected AdminLoginAttemptEntity() {}

    public static AdminLoginAttemptEntity record(String email, boolean succeeded,
                                                 String ipAddress, Instant now) {
        AdminLoginAttemptEntity a = new AdminLoginAttemptEntity();
        a.email       = email.toLowerCase().trim();
        a.succeeded   = succeeded;
        a.ipAddress   = ipAddress;
        a.attemptedAt = now;
        return a;
    }

    public UUID    getId()          { return id; }
    public String  getEmail()       { return email; }
    public boolean isSucceeded()    { return succeeded; }
    public String  getIpAddress()   { return ipAddress; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
