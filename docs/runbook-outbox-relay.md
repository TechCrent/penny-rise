# Outbox Relay Runbook

## What it does

The outbox relay polls `outbox.outbox_events` every 2 seconds for `PENDING` rows
and publishes them to RabbitMQ. It is the bridge between the Payments Service
database and all downstream event consumers (Audit, Notification, Challenge).

## Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `outbox_pending_count` | Gauge | Current PENDING row count. Zero is healthy. |
| `outbox_relay_lag_seconds` | Gauge | Age of the oldest PENDING row. Zero is healthy. Alert at > 60s. |
| `outbox_events_published_total` | Counter | Cumulative successful publishes. |
| `outbox_events_failed_total` | Counter | Cumulative failed publish attempts. |
| `outbox_events_dead_lettered_total` | Counter | Cumulative dead-lettered events. |

## Alerting thresholds

| Condition | Severity | Action |
|-----------|----------|--------|
| `outbox_relay_lag_seconds > 60` for > 2 minutes | WARNING | Check RabbitMQ connectivity; check relay logs for repeated WARN entries. |
| `outbox_relay_lag_seconds > 300` for > 1 minute | CRITICAL | Page on-call. RabbitMQ is likely down or the relay has crashed. |
| `outbox_pending_count > 500` | WARNING | Relay is behind. Check batch throughput and RabbitMQ connection. |
| `outbox_events_dead_lettered_total` rate > 0 | WARNING | At least one event exhausted 5 retry attempts. Investigate immediately — money movement events must not be lost. |

## Querying dead-letter events

```sql
-- All dead-letter events, newest first
SELECT id, event_type, aggregate_type, aggregate_id,
       failed_attempts, failed_reason, dead_lettered_at
FROM outbox.outbox_dead_letters
ORDER BY dead_lettered_at DESC;

-- Unreviewed dead-letters (not yet flagged for requeue)
SELECT * FROM outbox.outbox_dead_letters
WHERE requeue_requested = FALSE
ORDER BY dead_lettered_at DESC;

-- Dead-letters for a specific aggregate (e.g. a vault deposit)
SELECT * FROM outbox.outbox_dead_letters
WHERE aggregate_type = 'VAULT_DEPOSIT'
  AND aggregate_id   = '<uuid>';
```

## Replaying a dead-letter event

1. Identify the dead-letter row using the queries above.
2. Confirm the root cause is fixed (RabbitMQ was down, consumer was misconfigured, etc.).
3. Re-insert as a fresh PENDING row — the relay will pick it up within 2 seconds:

```sql
-- Step 1: re-insert to outbox_events
INSERT INTO outbox.outbox_events
    (id, event_type, schema_version, aggregate_type, aggregate_id,
     payload, routing_key, correlation_id, status, attempts, created_at)
SELECT
    gen_random_uuid(),
    event_type, schema_version, aggregate_type, aggregate_id,
    payload, routing_key, correlation_id,
    'PENDING', 0, NOW()
FROM outbox.outbox_dead_letters
WHERE id = '<dead_letter_id>';

-- Step 2: mark the dead-letter row as requeued
UPDATE outbox.outbox_dead_letters
SET requeue_requested = TRUE
WHERE id = '<dead_letter_id>';
```

4. Monitor `outbox_relay_lag_seconds` — it should spike briefly then return to zero.
5. Confirm the downstream consumer processed the event (check Audit Log or Notification table).

## What causes dead-letter events

- RabbitMQ was unreachable for the duration of all 5 retry attempts (2-second poll × 5 attempts ≈ within 10 seconds of the first failure).
- The exchange declared in `event_type` does not exist in `definitions.json` — the relay will throw a channel error on every attempt.
- A networking issue between the Payments Service and RabbitMQ.

## If the relay stops processing entirely

1. Check the Payments Service is running: `docker ps | grep payments`
2. Check RabbitMQ is healthy: open the management UI at http://localhost:15672
3. Check relay logs for `ERROR` entries in `OutboxRelay`
4. If the relay is running but not publishing, verify `payments.events` exchange exists in RabbitMQ management UI → Exchanges tab
5. If needed, restart the Payments Service — the relay resumes from all PENDING rows on startup with no data loss (the outbox is durable)
