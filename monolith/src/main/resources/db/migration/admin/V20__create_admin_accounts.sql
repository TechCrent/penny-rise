-- ============================================================
-- v0.5-001 : admin.admin_accounts
-- Staff-side accounts, completely separate from user_module.users.
-- Different schema, different table, different auth flow — a
-- compromised customer account can never be escalated to admin.
--
-- Schema doc §2.1 reference.
--
-- NOTE ON 2FA: System Design §7.2 and Wireframe A1 describe TOTP
-- as mandatory on every admin sign-in. The Issue Plan explicitly
-- scopes two-factor auth to v2.0 ("type:feature, ... v2.0" in the
-- NOT in scope list for v0.5). This table has NO totp_secret column
-- because the Issue Plan is the authoritative scope document and
-- Schema doc §2.1 also omits it. v0.5-003 (admin auth) must NOT
-- implement TOTP verification — flag this conflict for product
-- before A1 is built as a screen, since the wireframe currently
-- shows a 2FA code field that has nothing to validate against.
-- ============================================================

CREATE SCHEMA IF NOT EXISTS admin;

CREATE TABLE admin.admin_accounts (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    email           VARCHAR(320)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    full_name       VARCHAR(255)  NOT NULL,
    role_name       VARCHAR(100)  NULL,
    account_type    VARCHAR(50)   NOT NULL,
    created_by_id   UUID          NULL,
    is_active       BOOLEAN       NOT NULL DEFAULT true,
    deactivated_at  TIMESTAMPTZ   NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT admin_accounts_pk
        PRIMARY KEY (id),

    CONSTRAINT admin_accounts_email_uk
        UNIQUE (email),

    CONSTRAINT admin_accounts_created_by_fk
        FOREIGN KEY (created_by_id)
        REFERENCES admin.admin_accounts(id)
        ON DELETE RESTRICT,

    CONSTRAINT admin_accounts_type_check
        CHECK (account_type IN ('SUPER', 'VICE_SUPER', 'TAB')),

    CONSTRAINT admin_accounts_email_not_blank
        CHECK (length(trim(email)) > 0),

    CONSTRAINT admin_accounts_full_name_not_blank
        CHECK (length(trim(full_name)) > 0),

    CONSTRAINT admin_accounts_password_hash_not_blank
        CHECK (length(trim(password_hash)) > 0),

    -- created_by_id is NULL only for the original bootstrap SUPER.
    -- Every other row — including subsequent SUPER rows that might
    -- exist transiently during a SUPER handover — must record who
    -- created it.
    CONSTRAINT admin_accounts_created_by_required_unless_bootstrap
        CHECK (
            created_by_id IS NOT NULL
            OR account_type = 'SUPER'
        ),

    -- deactivated_at only set when is_active = false
    CONSTRAINT admin_accounts_deactivated_at_consistency
        CHECK (
            (is_active = true  AND deactivated_at IS NULL)
            OR
            (is_active = false AND deactivated_at IS NOT NULL)
        )
);

COMMENT ON TABLE admin.admin_accounts IS
    'Staff accounts that operate the admin console. Entirely separate '
    'from user_module.users — different schema, different auth flow, '
    'no foreign key or shared identifier between the two. A compromised '
    'customer account can never be escalated to admin access because '
    'there is no code path that reads from this table using a customer '
    'JWT or vice versa.';

COMMENT ON COLUMN admin.admin_accounts.email IS
    'Lowercased before storage and at every lookup. Must be a stash.app '
    'domain per the A1 wireframe note, though this is enforced at the '
    'application layer, not the database — see SusuGroupCreationService '
    'pattern of app-layer + DB-layer dual enforcement used elsewhere.';
COMMENT ON COLUMN admin.admin_accounts.password_hash IS
    'BCrypt, cost factor 12 per Schema doc §2.1. Never plaintext, never '
    'logged, never returned in any API response.';
COMMENT ON COLUMN admin.admin_accounts.role_name IS
    'Free-text role label for TAB admins (e.g. KYC_REVIEWER, '
    'DISPUTE_HANDLER). NULL for SUPER and VICE_SUPER, who are not '
    'scoped to a single tab. The mapping from role_name to permitted '
    'endpoints lives in code (Spring @PreAuthorize), not in this table — '
    'per System Design §7.3, "no separate roles/permissions table in v1.0".';
COMMENT ON COLUMN admin.admin_accounts.account_type IS
    'SUPER: full platform access, exactly one row at any time. '
    'VICE_SUPER: broad operational access, cannot manage SUPER or '
    'create other admins. '
    'TAB: scoped to the functional area named by role_name.';
COMMENT ON COLUMN admin.admin_accounts.created_by_id IS
    'Self-referencing FK encoding the admin hierarchy: SUPER creates '
    'VICE_SUPER, VICE_SUPER creates TAB. NULL only for the original '
    'bootstrap SUPER row created by the V20.1 Java migration.';
COMMENT ON COLUMN admin.admin_accounts.is_active IS
    'false after deactivation. Deactivated admins cannot log in and '
    'have all existing sessions/refresh tokens revoked immediately '
    '(v0.5-006 force-logout pattern reused for self-deactivation).';

-- ── Indexes ────────────────────────────────────────────────────────────────

-- Partial unique index: exactly one SUPER admin at any time.
-- This is the core invariant this migration exists to enforce.
CREATE UNIQUE INDEX admin_accounts_single_super_uk
    ON admin.admin_accounts (account_type)
    WHERE account_type = 'SUPER';

COMMENT ON INDEX admin.admin_accounts_single_super_uk IS
    'Enforces exactly one SUPER admin row in the table at any time. '
    'A SUPER handover (e.g. founder leaving) must demote the existing '
    'SUPER to VICE_SUPER in the SAME transaction as promoting the new '
    'one, or this constraint will reject the second INSERT/UPDATE.';

-- email uniqueness already covered by admin_accounts_email_uk above.

-- Composite index for the staff directory view (A10 wireframe) and
-- for efficient login lookups (is_active filtering on every auth attempt).
CREATE INDEX admin_accounts_type_active_idx
    ON admin.admin_accounts (account_type, is_active);

COMMENT ON INDEX admin.admin_accounts_type_active_idx IS
    'Supports the staff directory (A10): list active/deactivated admins '
    'grouped by type. Also supports login: WHERE email = ? AND is_active '
    'benefits from the leading is_active-adjacent column for the common '
    'case of filtering out deactivated accounts at auth time.';
