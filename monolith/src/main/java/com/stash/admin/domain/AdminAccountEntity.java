package com.stash.admin.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_accounts", schema = "admin")
public class AdminAccountEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "email",          nullable = false) private String  email;
    @Column(name = "password_hash",  nullable = false) private String  passwordHash;
    @Column(name = "full_name",      nullable = false) private String  fullName;
    @Column(name = "role_name")                        private String  roleName;
    @Column(name = "account_type",   nullable = false) private String  accountType;
    @Column(name = "created_by_id")                    private UUID    createdById;
    @Column(name = "is_active",      nullable = false) private boolean isActive;
    @Column(name = "deactivated_at")                   private Instant deactivatedAt;
    @Column(name = "created_at",     nullable = false) private Instant createdAt;

    protected AdminAccountEntity() {}

    /**
     * Creates a new admin account. Used by v0.5-004's admin-creation flow
     * (SUPER creates VICE_SUPER, VICE_SUPER creates TAB).
     *
     * @param creatorId required for all account types except the bootstrap
     *                  SUPER, which is created directly by the V20_1
     *                  migration and never goes through this factory.
     */
    public static AdminAccountEntity create(String email, String passwordHash,
                                             String fullName, String roleName,
                                             String accountType, UUID creatorId,
                                             Instant now) {
        if (creatorId == null && !"SUPER".equals(accountType)) {
            throw new IllegalArgumentException(
                    "creatorId is required for all account types except bootstrap SUPER");
        }
        AdminAccountEntity a = new AdminAccountEntity();
        a.email         = email.toLowerCase().trim();
        a.passwordHash  = passwordHash;
        a.fullName      = fullName;
        a.roleName      = roleName;
        a.accountType   = accountType;
        a.createdById   = creatorId;
        a.isActive      = true;
        a.createdAt     = now;
        return a;
    }

    public UUID    getId()             { return id; }
    public String  getEmail()          { return email; }
    public String  getPasswordHash()   { return passwordHash; }
    public String  getFullName()       { return fullName; }
    public String  getRoleName()       { return roleName; }
    public String  getAccountType()    { return accountType; }
    public UUID    getCreatedById()    { return createdById; }
    public boolean isActive()          { return isActive; }
    public Instant getDeactivatedAt()  { return deactivatedAt; }
    public Instant getCreatedAt()      { return createdAt; }
}
