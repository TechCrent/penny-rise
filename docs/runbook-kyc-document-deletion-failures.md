# KYC Document Deletion Failures Runbook

## What it does

`DocumentDeletionWorker` (kyc-service) deletes document bytes from object
storage (Supabase Storage in production, MinIO/local storage in dev) once
their grace period has passed. This is a **data retention compliance
signal**, not just an operational nuisance — documents that should have
been deleted are still sitting in storage while this alert is active.

## Metrics

| Metric | Type | Description |
|--------|------|--------------|
| `kyc_document_deletion_failures_total` | Counter | Cumulative deletion failures across all documents. New as of v0.5-027. |

## New metric note

Before v0.5-027, the only failure signal was a structured `[P0_ALERT]`-tagged
ERROR log line, fired by `DocumentEscalationService` once a **single
document** reaches 5 failed attempts (`deletion_failure_count = 5`). That
per-document escalation log still exists and is still the higher-priority
signal for any one document — this metric catches the **trend across all
documents**, so a rising count is visible before any individual document
has escalated. Treat this as an earlier warning, not a replacement for the
per-document log alert.

## Alerting threshold

| Condition | Severity | Action |
|-----------|----------|--------|
| More than 2 new failures within a 10-minute window | WARNING | See investigation steps below. |

## Alert configuration (v0.5-027)

Dashboard: `infra/grafana/provisioning/dashboards/kyc-document-deletion-failures.json` (Grafana UID `kyc-deletion-failures`).
Alert rule: `infra/grafana/provisioning/alerting/rules.yml`, uid `alert-kyc-deletion-failures-rising`.

## Likely causes

1. **Object storage outage or credential issue** — check Supabase Storage status (or MinIO container health in dev/staging); check whether the storage credential expired or was rotated without updating kyc-service's config.
2. **A specific `storage_key` pattern failing** — e.g. documents from before a bucket restructuring, or a path-encoding bug for certain filenames.
3. **The worker itself is unhealthy** — `DocumentDeletionScheduler` runs on a fixed schedule; a truly rising failure count means it IS running (just failing), not that it's silently dead.

## Investigation steps

1. Query `kyc.kyc_deletion_attempts` (via `DocumentDeletionJobRepository`) for recent `FAILURE` rows — check the error message/class for a common pattern.
2. Query `kyc.kyc_documents` for rows with `deletion_status = 'DELETE_FAILED'` and `deletion_failure_count` approaching 5 — these are closest to the per-document escalation log; cross-reference with recent `[P0_ALERT]` log lines.
3. Confirm object storage connectivity independently (a manual test delete/list call against the bucket) to rule out a kyc-service bug versus a genuine storage-provider issue.

## Resolution

- **Storage outage/credential issue**: fix connectivity/credentials — `DocumentDeletionScheduler` retries `DELETE_FAILED` rows automatically on its next cycle, no manual replay needed.
- **Storage-key-specific bug**: file a bug; may need a one-off manual cleanup script for the affected document set once the root cause is fixed.
- **Any document reaching `deletion_failure_count = 5`**: this has already escalated via the existing per-document `[P0_ALERT]` log — treat as higher priority than the trend alert alone; needs manual investigation of that specific document/storage_key.

## Demonstrating the alert firing locally

Point the object storage config at an invalid bucket/credential temporarily, let `DocumentDeletionScheduler` run enough cycles to accumulate more than 2 failures within a 10-minute window, confirm the alert fires, then revert the config and confirm the worker catches up and the alert resolves.
