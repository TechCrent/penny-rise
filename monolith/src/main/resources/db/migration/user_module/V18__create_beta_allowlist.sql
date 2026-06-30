-- ============================================================
-- v0.4-016 : Beta allowlist gate
-- Restricts signup and login to invited testers only.
-- Remains active for the entire Phase 1 plan per Section 0.1.
--
-- The feature flag (STASH_BETA_ALLOWLIST_ENABLED env var) controls
-- whether the gate is enforced. When false, all users pass
-- through regardless of this table's contents.
-- ============================================================

CREATE TABLE user_module.beta_allowlist (
    id         UUID          NOT NULL DEFAULT gen_random_uuid(),
    email      VARCHAR(255)  NOT NULL,
    added_by   VARCHAR(255)  NOT NULL,    -- admin identifier (email or system)
    added_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT beta_allowlist_pk       PRIMARY KEY (id),
    CONSTRAINT beta_allowlist_email_uk UNIQUE (email),

    CONSTRAINT beta_allowlist_email_not_blank
        CHECK (length(trim(email)) > 0),

    CONSTRAINT beta_allowlist_added_by_not_blank
        CHECK (length(trim(added_by)) > 0)
);

COMMENT ON TABLE user_module.beta_allowlist IS
    'Invited beta testers. Signup and login are blocked for emails '
    'not in this table when the STASH_BETA_ALLOWLIST_ENABLED flag is true. '
    'Remains active for the entire Phase 1 plan per Section 0.1.';

COMMENT ON COLUMN user_module.beta_allowlist.email IS
    'Email address of the invited tester (case-insensitive match at service layer). '
    'Unique — one row per email.';
COMMENT ON COLUMN user_module.beta_allowlist.added_by IS
    'Admin identifier who added this entry. Free text — email or "SYSTEM" for '
    'seed entries added during deployment.';

CREATE INDEX beta_allowlist_email_idx
    ON user_module.beta_allowlist (lower(email));

COMMENT ON INDEX user_module.beta_allowlist_email_idx IS
    'Case-insensitive email lookup used by the gate check on every signup/login.';
