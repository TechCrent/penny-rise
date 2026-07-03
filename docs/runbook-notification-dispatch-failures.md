# Notification Dispatch Failure Rate Runbook

## What it does

`NotificationDispatchService` (monolith) delivers push (Expo) and email
(Resend, via `EmailSender`) notifications, retrying push up to 3 times with
exponential backoff. This runbook covers the alert on the *rate* of
dispatches that exhaust their retry budget and fail.

## Metrics

| Metric | Type | Description |
|--------|------|--------------|
| `notification_dispatch_failure_rate_total` | Counter | Cumulative dispatch failures. Despite the name (kept for backward compatibility with the field name already in code), this is a raw count, not a percentage. |
| `notification_dispatch_attempts_total` | Counter | Cumulative dispatch attempts — added in v0.5-027. One increment per channel dispatch attempted (once per push-to-a-device, once per email), not per internal retry. |

A true failure **rate** is `100 * rate(notification_dispatch_failure_rate_total[5m]) / rate(notification_dispatch_attempts_total[5m])` — both counters are required; the attempts counter didn't exist before v0.5-027.

## What does NOT count as a failure

`DeviceNotRegistered` responses from Expo (a stale token from an
uninstalled app) are **deliberately excluded** from
`notification_dispatch_failure_rate_total` — this is graceful token cleanup,
not a delivery failure. It still counts as an attempt. If the failure rate
spikes alongside a spike in device deregistrations, first confirm this
distinction is actually holding in the deployed code before treating it as
an incident — a bug here would double-count expected cleanup as failures.

## Alerting threshold

| Condition | Severity | Action |
|-----------|----------|--------|
| Failure rate `> 5%` sustained 2+ minutes | WARNING | See investigation steps below. |

## Alert configuration (v0.5-027)

Dashboard: `infra/grafana/provisioning/dashboards/notification-dispatch-failures.json` (Grafana UID `notification-failure-rate`).
Alert rule: `infra/grafana/provisioning/alerting/rules.yml`, uid `alert-notification-failure-rate`.

## Likely causes

1. **Expo Push API outage or degradation** — check Expo's status page; affects push only.
2. **Resend API outage or rate-limiting** — check Resend's dashboard; affects email only.
3. **Misclassified `DeviceNotRegistered`** — see above; if this distinction has regressed, failures would spike alongside deregistrations for no real delivery reason.
4. **A bad notification template** producing malformed requests for one event type — check whether failures cluster around a single `notification_type`.

## Investigation steps

1. Check the dashboard's "Raw counts" panel — is attempt volume normal with failures spiking (real problem), or has attempt volume itself dropped near zero (a different problem — check the RabbitMQ consumer's health, not the failure rate)?
2. Grep monolith logs for `Expo push attempt` / `Email dispatch failed` WARN lines — what's the actual error from Expo/Resend?
3. Check `notification.notifications` for a cluster of `FAILED` rows, filtered by `notification_type` and `channel`, to isolate the pattern.

## Resolution

- **Third-party outage**: no action beyond monitoring — the retry mechanism catches up once the provider recovers. Confirm no backlog of permanently-`FAILED` rows needs manual reprocessing afterward.
- **Template bug**: fix and redeploy; consider whether affected users need a manual resend for anything time-sensitive (e.g. a security alert).
- **Misclassified `DeviceNotRegistered`**: file a bug — this is a code fix in `NotificationDispatchService.dispatchPushWithRetry`, not an ops-side mitigation.

## Demonstrating the alert firing locally

Point the Expo/Resend client config at an invalid endpoint (local/staging env var override), trigger several notifications so a meaningful number of dispatch attempts occur, and confirm the failure rate crosses 5% and the alert fires after 2 minutes sustained. Revert the config and confirm resolution.
