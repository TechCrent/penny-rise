# Ledger Integrity Check Runbook

## What it does

Runs daily at **02:00 UTC** (05:00 WAT — Ghana low-volume window). Verifies that every
POSTED ledger transaction satisfies the double-entry invariant:

```
SUM(DEBIT entries) == SUM(CREDIT entries)
```

Also checks for PENDING transactions older than 30 minutes, which indicate an orphaned
deposit or withdrawal where the Paystack webhook never arrived.

---

## Metrics

| Metric | Type | Normal value | Alert threshold |
|--------|------|-------------|-----------------|
| `ledger_integrity_check_drift_count` | Gauge | 0 | **> 0 = P0 INCIDENT** |
| `ledger_integrity_check_last_run_at` | Gauge | within 26 h | > 26 h = job stopped |
| `ledger_integrity_check_stale_pending_count` | Gauge | 0 | > 0 = investigate |
| `ledger_integrity_check_transactions_checked` | Gauge | > 0 after run | 0 = job may not have run |

**Scope note**: these metrics cover this general double-entry check only —
lives in **Payments Service** (`LedgerIntegrityService`/`LedgerIntegrityJob`,
daily 02:00 UTC). There is a **separate, additional** integrity check,
`susu_pot_integrity_drift_count`, that lives in **monolith**
(`SusuPotIntegrityChecker`/`SusuPotIntegrityJob`, daily 03:00 UTC) and checks
a single susu group's pot balance against its contribution history. A clean
`ledger_integrity_check_drift_count` does not mean the susu-pot check is also
clean — both are zero-tolerance and alerted separately (v0.5-027). Do not
apply this runbook's "check the Payments Service is up" step to a
`susu_pot_integrity_drift_count` alert — that's the wrong service.

## Alert configuration (v0.5-027)

Dashboard: `infra/grafana/provisioning/dashboards/ledger-drift.json` (Grafana UID `ledger-drift`) — panel 1 is this general check, panel 2 is the separate susu-pot check.
Alert rules: `infra/grafana/provisioning/alerting/rules.yml`, uids `alert-ledger-integrity-drift` (this check, `for: 0m` — fires immediately per the zero-tolerance AC) and `alert-susu-pot-drift` (the separate susu-pot check).

To demonstrate the alert firing locally: **never do this against an
environment with real user funds.** In a local/staging DB only, manually
corrupt a single `ledger.ledger_entries.amount` value, then trigger
`LedgerIntegrityJob`'s check (via its scheduled run or a manual bean
invocation — see "Manual full-history check" below). Revert the corruption
and confirm the alert resolves on the next check run.

---

## If drift is detected (`ledger_integrity_check_drift_count > 0`)

**This is a P0 incident. Stop all transactions immediately.**

1. Find the drifted transaction references in the logs:
   ```
   grep '\[P0_ALERT\]' /var/log/payments-service.log | tail -20
   ```

2. Query the drifted transaction's entries directly:
   ```sql
   SELECT le.direction, le.amount, la.account_type, la.owner_id
   FROM ledger.ledger_entries le
   JOIN ledger.ledger_accounts la ON la.id = le.account_id
   WHERE le.ledger_transaction_id = (
       SELECT id FROM ledger.ledger_transactions
       WHERE transaction_reference = 'STSH-...'
   );
   ```

3. Compare the entries against the expected double-entry for that transaction type.
4. Determine root cause (application bug, direct DB mutation, failed migration).
5. **Do NOT** manually fix entries — escalate to engineering lead.
   Any correction must be via a **compensating transaction** written by the application,
   not direct SQL.

---

## If stale PENDING transactions are detected

Find the stale transactions:
```sql
SELECT reference, transaction_type, created_at, external_reference
FROM transaction.transactions
WHERE status = 'PENDING'
  AND created_at < NOW() - INTERVAL '30 minutes'
ORDER BY created_at;
```

**For DEPOSIT transactions:** check Paystack dashboard for the `external_reference`.

- If Paystack shows the charge as **succeeded** but we didn't process the webhook:
  - Manually trigger webhook redelivery from the Paystack dashboard, **OR**
  - Call `PaystackClient.verifyTransaction(external_reference)` and manually post
    the ledger if confirmed (v0.5 admin API will automate this).

**For WITHDRAWAL transactions:** check Paystack dashboard for the transfer status.

- If the transfer **failed silently**: manually reverse the reservation via the
  reversal SQL documented in `runbook-outbox-relay.md`.

---

## If the job stops running (`last_run_at` > 26 hours old)

1. Check the Payments Service is up: `docker ps | grep payments`
2. Check for errors in the job logs: `grep LedgerIntegrityJob /var/log/payments-service.log`
3. Check `spring.task.scheduling.enabled` is not `false` in production config
4. Restart the Payments Service if no other issues found

---

## Manual full-history check

To re-verify all historical transactions (e.g. after a data migration):

```java
// Via Spring shell or admin endpoint (not yet exposed — use bean directly in a test context)
LedgerIntegrityService service = context.getBean(LedgerIntegrityService.class);
IntegrityCheckResult result = service.runFullHistoryCheck();
```

This queries from `Instant.EPOCH` to now. May be slow on large datasets.
