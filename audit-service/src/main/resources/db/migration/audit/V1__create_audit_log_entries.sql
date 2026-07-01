-- ============================================================
-- v0.5-009 : audit.audit_log_entries base table
--
-- Per Schema doc §9.1. Append-only enforcement (REVOKE UPDATE/DELETE) is
-- explicitly a SEPARATE issue (v0.5-010) — this migration intentionally
-- does NOT restrict the application role's write permissions. Do not add
-- REVOKE statements here; that's the next issue's job and its own
-- migration file, kept separate per the established "CREATE and REVOKE in
-- separate migration files" convention.
-- ============================================================

CREATE TABLE audit.audit_log_entries (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    event_id         VARCHAR(255)  NOT NULL,
    event_type       VARCHAR(100)  NOT NULL,
    schema_version   VARCHAR(20)   NULL,
    source_service   VARCHAR(50)   NOT NULL,
    actor_type       VARCHAR(50)   NOT NULL,
    actor_id         UUID          NULL,
    target_type      VARCHAR(50)   NOT NULL,
    target_id        UUID          NULL,
    payload          JSONB         NOT NULL,
    correlation_id   VARCHAR(255)  NULL,
    occurred_at      TIMESTAMPTZ   NOT NULL,
    received_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT audit_log_entries_pk PRIMARY KEY (id),
    CONSTRAINT audit_log_entries_event_id_uk UNIQUE (event_id),

    CONSTRAINT audit_log_entries_actor_type_check
        CHECK (actor_type IN ('USER', 'ADMIN', 'SYSTEM', 'PROVIDER'))
);

COMMENT ON TABLE audit.audit_log_entries IS
    'Append-only platform-wide audit log. INSERT-only enforcement (REVOKE '
    'UPDATE/DELETE from the application role) lands in v0.5-010, not here.';
COMMENT ON COLUMN audit.audit_log_entries.event_id IS
    'Deduplication key — the UNIQUE constraint is the entire idempotency '
    'mechanism. A duplicate delivery is caught as a constraint violation '
    'and treated as a successful no-op ack, not an error.';
COMMENT ON COLUMN audit.audit_log_entries.actor_type IS
    'SYSTEM with actor_id NULL is the fallback for unrecognised event types '
    '(Issue v0.5-009) where no mapper exists yet to determine the real actor.';
COMMENT ON COLUMN audit.audit_log_entries.target_type IS
    'UNKNOWN is the fallback for unrecognised event types '
    '(target taxonomy is open-ended per Schema doc §9.1).';

CREATE INDEX audit_log_entries_target_idx
    ON audit.audit_log_entries (target_type, target_id, occurred_at DESC);
CREATE INDEX audit_log_entries_actor_idx
    ON audit.audit_log_entries (actor_id, occurred_at DESC);
CREATE INDEX audit_log_entries_event_type_idx
    ON audit.audit_log_entries (event_type, occurred_at DESC);
CREATE INDEX audit_log_entries_correlation_idx
    ON audit.audit_log_entries (correlation_id);
CREATE INDEX audit_log_entries_received_at_idx
    ON audit.audit_log_entries (received_at);
