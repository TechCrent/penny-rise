-- ============================================================
-- v0.5-002 (part 2) : admin.disputes
--
-- Schema doc §2.3 reference, with one deliberate addition beyond
-- the doc: a `resolution` JSONB column and an `updated_at` column.
--
-- GAP IN SCHEMA DOC §2.3: the doc defines resolved_at and
-- resolved_by_admin_id but has NO column to store the resolution
-- CATEGORY (Resolved-no-action / Refunded / Reversed / Escalated /
-- Closed-invalid) or the handler's NOTES. The A8 wireframe
-- ("Wireframe Planning Guide.docx") explicitly requires both:
-- "Resolve dispute opens a modal with the resolution categories
-- ... plus notes." Without a place to store them, the wireframe's
-- core interaction cannot be implemented. Adding `resolution JSONB`
-- to close this gap — Schema doc should be updated to include it.
--
-- Status and priority enums also follow Schema doc over the issue
-- description: CLOSED_NO_ACTION (not bare CLOSED), CRITICAL (not
-- URGENT) — both corroborated by the A8 wireframe's resolution
-- category list and the A7 dispute-queue SLA-breach language.
-- ============================================================

CREATE TABLE admin.disputes (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    raised_by_user_id      UUID          NOT NULL,
    dispute_type           VARCHAR(50)   NOT NULL,
    related_entity_type    VARCHAR(50)   NOT NULL,
    related_entity_id      UUID          NOT NULL,
    subject                VARCHAR(255)  NOT NULL,
    description            TEXT          NOT NULL,
    status                 VARCHAR(50)   NOT NULL DEFAULT 'OPEN',
    priority               VARCHAR(20)   NOT NULL DEFAULT 'NORMAL',
    assigned_to_admin_id   UUID          NULL,
    resolution             JSONB         NULL,
    resolved_at            TIMESTAMPTZ   NULL,
    resolved_by_admin_id   UUID          NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT disputes_pk
        PRIMARY KEY (id),

    -- assigned_to_admin_id and resolved_by_admin_id are physical FKs —
    -- disputes lives in the same database as admin_accounts (both in
    -- monolith_db, admin schema). This is allowed per System Design §5.2's
    -- "same physical database" exception to the no-cross-service-FK rule.
    CONSTRAINT disputes_assigned_to_fk
        FOREIGN KEY (assigned_to_admin_id)
        REFERENCES admin.admin_accounts(id)
        ON DELETE SET NULL,

    CONSTRAINT disputes_resolved_by_fk
        FOREIGN KEY (resolved_by_admin_id)
        REFERENCES admin.admin_accounts(id)
        ON DELETE RESTRICT,

    -- raised_by_user_id is a LOGICAL reference to user_module.users.id —
    -- no physical FK, per System Design §5.2's three reasons (service
    -- independence, database independence, operational simplicity),
    -- even though both tables happen to live in the same physical
    -- database today. Treating it as logical now avoids a breaking
    -- change if user_module is ever extracted into its own service.

    CONSTRAINT disputes_type_check
        CHECK (dispute_type IN ('TRANSACTION', 'KYC', 'SUSU', 'OTHER')),

    CONSTRAINT disputes_status_check
        CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'CLOSED_NO_ACTION')),

    CONSTRAINT disputes_priority_check
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),

    CONSTRAINT disputes_subject_not_blank
        CHECK (length(trim(subject)) > 0),

    CONSTRAINT disputes_description_not_blank
        CHECK (length(trim(description)) > 0),

    CONSTRAINT disputes_related_entity_type_not_blank
        CHECK (length(trim(related_entity_type)) > 0),

    -- Resolution fields only populated for terminal statuses
    CONSTRAINT disputes_resolved_at_requires_terminal
        CHECK (
            resolved_at IS NULL
            OR status IN ('RESOLVED', 'CLOSED_NO_ACTION')
        ),

    CONSTRAINT disputes_resolved_by_requires_terminal
        CHECK (
            resolved_by_admin_id IS NULL
            OR status IN ('RESOLVED', 'CLOSED_NO_ACTION')
        ),

    CONSTRAINT disputes_resolution_requires_terminal
        CHECK (
            resolution IS NULL
            OR status IN ('RESOLVED', 'CLOSED_NO_ACTION')
        ),

    -- A terminal-status dispute must have resolved_at and resolved_by set —
    -- you cannot mark something RESOLVED without recording who and when.
    CONSTRAINT disputes_terminal_requires_resolution_metadata
        CHECK (
            status NOT IN ('RESOLVED', 'CLOSED_NO_ACTION')
            OR (resolved_at IS NOT NULL AND resolved_by_admin_id IS NOT NULL)
        )
);

COMMENT ON TABLE admin.disputes IS
    'User-raised complaints across all entity types: a susu round the '
    'user thinks was miscalculated, an unauthorised transfer, a KYC '
    'rejection they want appealed. Flows OPEN -> IN_REVIEW -> RESOLVED '
    'or CLOSED_NO_ACTION. related_entity_type/related_entity_id form a '
    'polymorphic, non-FK reference — per Schema doc §2.3, "no FK is set '
    'because the related entity may live in another service."';

COMMENT ON COLUMN admin.disputes.raised_by_user_id IS
    'Logical FK -> user_module.users.id. No physical FK — see System '
    'Design §5.2 (cross-service reference policy applies even though '
    'both tables are in the same physical database today).';
COMMENT ON COLUMN admin.disputes.dispute_type IS
    'High-level category for routing/filtering: TRANSACTION, KYC, SUSU, '
    'OTHER. Distinct from related_entity_type, which is the precise '
    'polymorphic target.';
COMMENT ON COLUMN admin.disputes.related_entity_type IS
    'Polymorphic discriminator: SUSU_ROUND, TRANSACTION, KYC_SUBMISSION, '
    'PEER_TRANSFER, etc. Determines how related_entity_id is interpreted '
    'by the dispute detail screen (A8) when fetching context.';
COMMENT ON COLUMN admin.disputes.status IS
    'OPEN: just raised, unassigned or assigned but not yet worked. '
    'IN_REVIEW: an admin is actively investigating. '
    'RESOLVED: handled with a resolution recorded (resolution JSONB set). '
    'CLOSED_NO_ACTION: closed without remedial action — still requires '
    'resolved_at/resolved_by per the terminal-status CHECK.';
COMMENT ON COLUMN admin.disputes.priority IS
    'LOW/NORMAL/HIGH/CRITICAL. Drives queue sort order on A7 (dispute '
    'list) and the SLA-breach red-text indicator for HIGH disputes open '
    '>24h per the Wireframe Planning Guide.';
COMMENT ON COLUMN admin.disputes.resolution IS
    'JSONB: { "category": "REFUNDED" | "REVERSED" | "ESCALATED" | '
    '"RESOLVED_NO_ACTION" | "CLOSED_INVALID", "notes": "..." }. '
    'NOT in Schema doc §2.3 as written — added here to close a real '
    'gap between the doc and the A8 wireframe''s resolution-modal '
    'requirement. Schema doc should be updated in the same PR that '
    'reviews this migration.';
COMMENT ON COLUMN admin.disputes.assigned_to_admin_id IS
    'Physical FK -> admin.admin_accounts.id. NULL while unassigned — '
    'the A7 dispute queue''s "Unassigned" filter relies on this being '
    'NULL, not on a sentinel value.';

-- ── Indexes ────────────────────────────────────────────────────────────────

CREATE INDEX disputes_status_priority_idx
    ON admin.disputes (status, priority);

COMMENT ON INDEX admin.disputes_status_priority_idx IS
    'Supports the A7 dispute queue: filter by status, sort by priority. '
    'Composite ordering matches the most common query shape — "show me '
    'OPEN disputes, highest priority first."';

CREATE INDEX disputes_raised_by_idx
    ON admin.disputes (raised_by_user_id);

COMMENT ON INDEX admin.disputes_raised_by_idx IS
    'Supports "this user''s dispute history" — both for the admin user '
    'detail screen and for a possible future user-facing "my disputes" '
    'screen.';

CREATE INDEX disputes_assigned_to_idx
    ON admin.disputes (assigned_to_admin_id)
    WHERE assigned_to_admin_id IS NOT NULL;

COMMENT ON INDEX admin.disputes_assigned_to_idx IS
    'Partial index: "disputes currently assigned to me" for an '
    'individual admin''s personal queue view. Excludes NULL '
    '(unassigned) rows since those are found via the status_priority '
    'index instead.';

CREATE INDEX disputes_related_entity_idx
    ON admin.disputes (related_entity_type, related_entity_id);

COMMENT ON INDEX admin.disputes_related_entity_idx IS
    'Supports "are there any open disputes about this susu round / '
    'transaction / KYC submission?" — checked before certain admin '
    'actions (e.g. blocking a refund if a dispute on the same entity '
    'is already in flight) and surfaced on entity detail screens.';
