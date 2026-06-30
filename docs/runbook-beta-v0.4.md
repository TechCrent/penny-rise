# Stash Beta Runbook — v0.4

> **Status:** Living document. Update after every beta session.
> **Companion to:** Module Boundaries doc (§Operational Runbooks), System Design
> **Last reviewed:** 2026-06-29

---

## Table of Contents

1. [Environment Reference](#1-environment-reference)
2. [Tester Onboarding](#2-tester-onboarding)
3. [Seeding a Test Susu](#3-seeding-a-test-susu)
4. [Monitoring a Live Round](#4-monitoring-a-live-round)
5. [Diagnosing a Stuck Contribution](#5-diagnosing-a-stuck-contribution)
6. [Diagnosing a Stuck Disbursement](#6-diagnosing-a-stuck-disbursement)
7. [Known Failure Modes](#7-known-failure-modes)
8. [Runbook Update Log](#8-runbook-update-log)

---

## 1. Environment Reference

### 1.1 Service base URLs

| Service | Internal URL (staging) | Notes |
|---|---|---|
| Monolith | `https://stash-monolith.internal` | All user-facing and internal endpoints |
| Payments Service | `https://stash-payments.internal` | Ledger, transfers, Paystack |
| KYC Service | `https://stash-kyc.internal` | Document submission, decisions |
| Audit Log | `https://stash-audit.internal` | Read-only; append via events |

### 1.2 Auth headers

All internal endpoints require the service token:

```
X-Internal-Service-Token: <INTERNAL_SERVICE_TOKEN from secrets manager>
```

User-facing endpoints require a Bearer JWT obtained via `POST /auth/login`.

### 1.3 Key environment variables

| Variable | Where set | Purpose |
|---|---|---|
| `STASH_BETA_ALLOWLIST_ENABLED` | Monolith | `true` in staging/prod; `false` in dev/test |
| `STASH_INTERNAL_SERVICE_TOKEN` | All services | Shared secret for internal calls |
| `PAYSTACK_SECRET_KEY` | Payments Service | Use test key in staging |
| `PENALTY_REVENUE_ACCOUNT_ID` | Monolith | `00000000-0000-0000-0000-000000000003` |
| `FEE_REVENUE_ACCOUNT_ID` | Monolith | `00000000-0000-0000-0000-000000000002` |

### 1.4 Database connection reference

```bash
# Monolith DB
psql $MONOLITH_DB_URL

# Payments DB
psql $PAYMENTS_DB_URL
```

Schemas:
- `monolith_db` — `user_module`, `susu`, `transfer`, `vault`
- `payments_db` — `ledger`, `transaction`, `idempotency`, `outbox`, `webhook`

---

## 2. Tester Onboarding

### 2.1 Add a user to the beta allowlist

The allowlist gate is enforced at signup and login. A non-allowlisted email receives `403 BETA_ACCESS_REQUIRED`. Add testers before they attempt to sign up.

**Endpoint:** `POST /internal/beta-allowlist`

```bash
curl -X POST https://stash-monolith.internal/internal/beta-allowlist \
  -H "X-Internal-Service-Token: $INTERNAL_SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tester@example.com",
    "added_by": "your-name@stash.app"
  }'
```

**Expected response:** `201 Created`

```json
{
  "email": "tester@example.com",
  "result": "ADDED"
}
```

If the email is already on the list, the endpoint returns `201` silently (no-op).

### 2.2 Verify allowlist status

Check whether a specific email is on the list via the database:

```sql
SELECT email, added_by, added_at
FROM user_module.beta_allowlist
WHERE lower(email) = lower('tester@example.com');
```

Or verify the gate is active:

```bash
curl https://stash-monolith.internal/internal/beta-allowlist/status \
  -H "X-Internal-Service-Token: $INTERNAL_SERVICE_TOKEN"
```

Expected: `{"gateEnabled": true}`

### 2.3 Remove a user from the allowlist

```bash
curl -X DELETE \
  "https://stash-monolith.internal/internal/beta-allowlist/tester%40example.com" \
  -H "X-Internal-Service-Token: $INTERNAL_SERVICE_TOKEN"
```

Note: URL-encode the `@` sign as `%40`.

**Expected response:** `204 No Content`

If the email was not on the list, this is a no-op — still `204`.

### 2.4 Tester account setup checklist

After adding a tester to the allowlist, have them complete:

- [ ] Download the app (TestFlight / Play internal track)
- [ ] Sign up with their allowlisted email
- [ ] Complete KYC submission (Ghana Card + selfie) via the KYC screen
- [ ] Wait for KYC approval (manual in staging unless automated mode is active)
- [ ] Deposit a small test amount to their wallet to verify the Paystack flow

**KYC approval in staging (manual path):**

```bash
# Get pending KYC submissions
curl https://stash-kyc.internal/api/v1/kyc/admin/queue \
  -H "X-Internal-Service-Token: $INTERNAL_SERVICE_TOKEN"

# Approve a submission
curl -X POST \
  "https://stash-kyc.internal/api/v1/kyc/admin/submissions/{submission_id}/decide" \
  -H "X-Internal-Service-Token: $INTERNAL_SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"decision": "APPROVE", "reason": "Beta tester - manual review"}'
```

---

## 3. Seeding a Test Susu

This section walks through creating a live 6-member susu group in staging. All amounts are in pesewas (100 pesewas = GHS 1.00).

### 3.1 Prerequisites

- [ ] 6 allowlisted testers with `kyc_status = APPROVED` accounts
- [ ] Each tester has a USER_WALLET with at least the contribution amount available
- [ ] You have JWTs for each tester (log in as each and save the token)

Verify KYC status for all testers:

```sql
SELECT id, email, kyc_status, subscription_tier
FROM user_module.users
WHERE email IN (
  'tester1@example.com', 'tester2@example.com',
  'tester3@example.com', 'tester4@example.com',
  'tester5@example.com', 'tester6@example.com'
);
```

All rows must show `kyc_status = 'APPROVED'`.

### 3.2 Step 1 — Organiser creates the group

Log in as the organiser (tester 1) and create the group:

```bash
curl -X POST https://stash-monolith.internal/api/v1/susu/groups \
  -H "Authorization: Bearer $ORGANISER_JWT" \
  -H "Idempotency-Key: seed-susu-$(date +%s)" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Beta Test Susu",
    "contribution_amount": 2000,
    "frequency": "MONTHLY",
    "target_member_count": 6
  }'
```

`contribution_amount` of 2000 = GHS 20.00 per member per round. Adjust as needed.

**Save the response values:**

```
GROUP_ID=<id from response>
JOIN_CODE=<join_code from response>
```

Verify the group row:

```sql
SELECT id, status, join_code, target_member_count, current_round_number
FROM susu.susu_groups
WHERE id = '<GROUP_ID>';
-- Expected: status=PENDING, current_round_number=NULL
```

### 3.3 Step 2 — Members join using the join code

Repeat for testers 2–6, each using their own JWT:

```bash
curl -X POST https://stash-monolith.internal/api/v1/susu/groups/join \
  -H "Authorization: Bearer $TESTER_N_JWT" \
  -H "Idempotency-Key: join-susu-$(uuidgen)" \
  -H "Content-Type: application/json" \
  -d "{\"join_code\": \"$JOIN_CODE\"}"
```

After all 5 non-organiser members have joined, verify:

```sql
SELECT count(*) AS member_count
FROM susu.susu_memberships
WHERE susu_group_id = '<GROUP_ID>'
  AND status = 'ACTIVE';
-- Expected: 6
```

### 3.4 Step 3 — Organiser activates the group

```bash
curl -X POST \
  "https://stash-monolith.internal/api/v1/susu/groups/$GROUP_ID/activate" \
  -H "Authorization: Bearer $ORGANISER_JWT" \
  -H "Idempotency-Key: activate-susu-$GROUP_ID" \
  -H "Content-Type: application/json"
```

**Expected response:** `200 OK` with activation details including `ledger_account_id`.

Post-activation verification:

```sql
-- Group is now ACTIVE with round 1 COLLECTING
SELECT status, current_round_number, start_date, ledger_account_id
FROM susu.susu_groups
WHERE id = '<GROUP_ID>';
-- Expected: status=ACTIVE, current_round_number=1

-- 6 rounds generated
SELECT round_number, status, recipient_user_id, expected_pot_amount
FROM susu.susu_rounds
WHERE susu_group_id = '<GROUP_ID>'
ORDER BY round_number;
-- Expected: round 1 = COLLECTING, rounds 2-6 = PENDING

-- 6 contribution rows for round 1
SELECT member_user_id, status, expected_amount
FROM susu.susu_contributions
WHERE susu_round_id = (
  SELECT id FROM susu.susu_rounds
  WHERE susu_group_id = '<GROUP_ID>' AND round_number = 1
);
-- Expected: 6 rows, all status=PENDING

-- SUSU_POT ledger account exists
SELECT id, account_type, status, owner_id
FROM ledger.ledger_accounts  -- run against payments_db
WHERE owner_id = '<GROUP_ID>'
  AND account_type = 'SUSU_POT';
-- Expected: 1 row, status=ACTIVE
```

### 3.5 Step 4 — Members contribute

Each of the 6 members pays their contribution. Get the round ID first:

```sql
SELECT id AS round_id
FROM susu.susu_rounds
WHERE susu_group_id = '<GROUP_ID>' AND round_number = 1;
```

For each tester:

```bash
curl -X POST \
  "https://stash-monolith.internal/api/v1/susu/contributions/$ROUND_ID" \
  -H "Authorization: Bearer $TESTER_N_JWT" \
  -H "Idempotency-Key: contrib-$ROUND_ID-tester-N" \
  -H "Content-Type: application/json"
```

After each contribution, check its status:

```sql
SELECT status, collected_amount, transaction_reference, paid_at
FROM susu.susu_contributions
WHERE susu_round_id = '<ROUND_ID>'
  AND member_user_id = '<TESTER_USER_ID>';
-- Expected: status=PAID, collected_amount=2000
```

After the 6th contribution, the round should auto-advance to `DISBURSING`:

```sql
SELECT status, actual_pot_amount
FROM susu.susu_rounds
WHERE id = '<ROUND_ID>';
-- Expected: status=DISBURSING, actual_pot_amount=12000
```

### 3.6 Step 5 — Disbursement worker fires

The disbursement worker polls for `DISBURSING` rounds every 60 seconds. After at most 60 seconds:

```sql
-- Round should be COMPLETED
SELECT status, disbursed_at, disbursement_transaction_id
FROM susu.susu_rounds WHERE id = '<ROUND_ID>';

-- Round 2 should now be COLLECTING
SELECT round_number, status
FROM susu.susu_rounds
WHERE susu_group_id = '<GROUP_ID>'
ORDER BY round_number;

-- Recipient wallet should be credited
-- (check in payments_db)
SELECT sum(amount) AS total_received
FROM ledger.ledger_entries  -- run against payments_db
WHERE account_id = (
  SELECT id FROM ledger.ledger_accounts
  WHERE owner_id = '<RECIPIENT_USER_ID>'
    AND account_type = 'USER_WALLET'
)
AND direction = 'CREDIT'
ORDER BY created_at DESC
LIMIT 5;
```

---

## 4. Monitoring a Live Round

### 4.1 Prometheus metrics to watch

| Metric | Healthy value | Unhealthy signal |
|---|---|---|
| `susu_pot_integrity_drift_count` | 0 | Any value > 0 is P0 |
| `http_server_requests_seconds{uri="/api/v1/susu/contributions/*"}` | p99 < 3s | p99 > 5s indicates Payments latency |
| `rabbit_queue_messages{queue="susu.disbursement.queue"}` | 0 between rounds | > 0 for more than 2 minutes = worker issue |
| `rabbit_queue_messages{queue="susu.disbursement.dlq"}` | 0 always | Any value = worker failed after 3 retries |

Query the drift metric directly:

```bash
curl http://stash-monolith.internal:8080/actuator/prometheus \
  | grep susu_pot_integrity_drift_count
```

### 4.2 RabbitMQ queues to watch

Access the RabbitMQ management UI at `https://rabbitmq.internal/#/queues`.

| Queue | Binding | Normal state |
|---|---|---|
| `susu.disbursement.queue` | `susu.events` → `susu.round.fully_collected` | Drains to 0 within 60s of a contribution completing the round |
| `susu.disbursement.dlq` | Dead-letter from above | Should always be empty |

**Healthy disbursement lifecycle in the management UI:**

1. Contribution 6 pays → message appears in `susu.disbursement.queue`
2. Within seconds: message consumed, queue drains to 0
3. Within 60s: round transitions to COMPLETED, next round to COLLECTING

**Warning signs:**

- Message stays in `susu.disbursement.queue` for > 2 minutes → worker may be down
- Any message in `susu.disbursement.dlq` → manual intervention needed (see §6)
- `susu_pot_integrity_drift_count` > 0 → P0, page on-call immediately

### 4.3 Key log lines to search during a live round

In the monolith logs, search for these structured log entries by correlation ID:

```
# Contribution received:
grep "SusuContribution PAID" logs/monolith.log | grep "<ROUND_ID>"

# Round fully collected (triggers disbursement):
grep "Round fully collected" logs/monolith.log | grep "<ROUND_ID>"

# Disbursement completed:
grep "DisbursementProcessor: round=<ROUND_ID> COMPLETED"

# Next round opened:
grep "DisbursementProcessor: opened round=" logs/monolith.log | grep "<GROUP_ID>"

# Integrity check passed:
grep "✓ balanced" logs/monolith.log | grep "<GROUP_ID>"
```

In the Payments Service logs:

```
# Transfer from USER_WALLET to SUSU_POT:
grep "transaction_type=SUSU_CONTRIBUTION" logs/payments.log | grep "<ROUND_ID>"

# Transfer from SUSU_POT to recipient:
grep "transaction_type=SUSU_DISBURSEMENT" logs/payments.log | grep "<ROUND_ID>"
```

### 4.4 What healthy looks like end-to-end

```
T+0s    Member 6 taps "Pay GHS 20"
T+1s    POST /api/v1/susu/contributions/{round_id} → 200 OK
T+1s    Log: "SusuContribution PAID round=R group=G member=M"
T+1s    Log: "Round fully collected: round=R actual_pot=12000p"
T+1s    Round status → DISBURSING (DB write)
T+1s    susu.round.fully_collected event published to RabbitMQ
T+2s    DisbursementWorker consumes event
T+3s    Payments transfer SUSU_POT → recipient USER_WALLET (GHS 120)
T+4s    Round status → COMPLETED
T+4s    Next round → COLLECTING, 6 new contribution rows created
T+4s    Log: "DisbursementProcessor: round=R COMPLETED"
T+4s    susu.round.completed event published
T+60s   (Nightly job) susu_pot_integrity_drift_count = 0
```

---

## 5. Diagnosing a Stuck Contribution

A contribution is "stuck" when its status remains `PENDING` past the round's `scheduled_collection_at` and the member reports they tried to pay.

### 5.1 Initial triage

```sql
-- Locate the contribution
SELECT c.id, c.status, c.member_user_id,
       c.transaction_reference, c.paid_at,
       r.scheduled_collection_at, r.status AS round_status
FROM susu.susu_contributions c
JOIN susu.susu_rounds r ON r.id = c.susu_round_id
WHERE c.susu_group_id = '<GROUP_ID>'
  AND c.member_user_id = '<MEMBER_USER_ID>';
```

Possible states and next steps:

| `c.status` | `r.status` | Meaning | Next step |
|---|---|---|---|
| `PENDING` | `COLLECTING` | Member hasn't paid yet | Check idempotency table |
| `PENDING` | `DISBURSING` | Round already disbursed without this member | Round closed early — check §5.4 |
| `LATE` | `COLLECTING` | Late-penalty job ran | Normal; member can still pay |
| `PAID` | any | Already paid successfully | UI may be stale — tell member to refresh |

### 5.2 Check the idempotency table

If the member reports they tapped "Pay" but the contribution is still `PENDING`, the payment may have failed silently or the idempotency key may have collided.

```sql
-- Check in payments_db
SELECT key_value, state, response_status_code,
       response_body, created_at, expires_at
FROM idempotency.idempotency_keys
WHERE request_path LIKE '%contributions%'
  AND created_at > now() - interval '2 hours'
ORDER BY created_at DESC
LIMIT 10;
```

Possible findings:

- **Row exists, `state = COMPLETED`, `response_status_code = 200`** → Payment succeeded in Payments Service but monolith didn't write the PAID row. Check the Payments ledger directly (§5.3).
- **Row exists, `state = FAILED`** → Transfer failed. Check Payments logs for the error code.
- **No row found** → Request never reached the Payments Service. Check monolith logs for the correlation ID the member's app sent.

### 5.3 Check the Payments ledger directly

If the idempotency table shows a successful transfer but the contribution is still PENDING:

```sql
-- Run against payments_db
SELECT lt.transaction_reference, lt.transaction_type,
       le.direction, le.amount, le.created_at
FROM ledger.ledger_transactions lt
JOIN ledger.ledger_entries le ON le.ledger_transaction_id = lt.id
WHERE lt.business_reference_id = '<CONTRIBUTION_ID>'
  AND lt.transaction_type = 'SUSU_CONTRIBUTION'
ORDER BY le.created_at DESC;
```

If the transfer exists in the ledger but the monolith's contribution row shows `PENDING`:

```
SITUATION: Money moved in Payments but monolith didn't write the PAID row.
This means the monolith transaction rolled back AFTER the Payments call succeeded.
```

**Recovery steps:**

1. Verify the pot balance is correct:
   ```sql
   -- In payments_db
   SELECT sum(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END) AS balance
   FROM ledger.ledger_entries
   WHERE account_id = '<SUSU_POT_LEDGER_ACCOUNT_ID>';
   ```

2. If the pot balance equals the sum of the expected contributions, manually mark the contribution PAID:
   ```sql
   -- In monolith_db — only after confirming ledger is correct
   UPDATE susu.susu_contributions
   SET status = 'PAID',
       collected_amount = expected_amount,
       transaction_reference = '<reference from Payments>',
       paid_at = now()
   WHERE id = '<CONTRIBUTION_ID>'
     AND status = 'PENDING';
   ```

3. Check if this was the final contribution (all others PAID):
   ```sql
   SELECT COUNT(*) FROM susu.susu_contributions
   WHERE susu_round_id = '<ROUND_ID>'
     AND status NOT IN ('PAID', 'MISSED', 'WAIVED');
   ```
   If 0, the round must be manually advanced to `DISBURSING` and the disbursement event re-published (see §5.5).

### 5.4 Check the outbox / event publication

If the contribution is PAID but the round hasn't advanced:

```sql
-- In payments_db — check outbox for fully_collected event
SELECT event_type, status, created_at, sent_at, attempts
FROM outbox.outbox_events
WHERE event_type = 'susu.round.fully_collected'
  AND payload::text LIKE '%<ROUND_ID>%'
ORDER BY created_at DESC
LIMIT 5;
```

If `status = PENDING` with many `attempts`: outbox relay is failing to publish to RabbitMQ. Check RabbitMQ connectivity.

If `status = SENT`: the event was published but the worker didn't consume it. Check the DLQ (§6.3).

### 5.5 Manually trigger disbursement for a stuck-COLLECTING round

Use this only after confirming all contributions are PAID and the ledger is balanced:

```sql
-- First verify all contributions are terminal
SELECT status, COUNT(*) FROM susu.susu_contributions
WHERE susu_round_id = '<ROUND_ID>'
GROUP BY status;
-- Should show only PAID, MISSED, or WAIVED rows

-- Mark the round DISBURSING (the worker will pick it up within 60s)
UPDATE susu.susu_rounds
SET status = 'DISBURSING',
    actual_pot_amount = (
      SELECT COALESCE(SUM(collected_amount), 0)
      FROM susu.susu_contributions
      WHERE susu_round_id = '<ROUND_ID>' AND status = 'PAID'
    )
WHERE id = '<ROUND_ID>'
  AND status = 'COLLECTING';
```

The scheduled fallback poller in `SusuDisbursementWorker` runs every 60 seconds and will detect the `DISBURSING` round and process it automatically.

---

## 6. Diagnosing a Stuck Disbursement

A disbursement is "stuck" when a round remains in `DISBURSING` status for more than 5 minutes.

### 6.1 Initial triage

```sql
SELECT r.id, r.round_number, r.status,
       r.actual_pot_amount, r.recipient_user_id,
       r.disbursed_at, r.disbursement_transaction_id,
       g.ledger_account_id AS susu_pot_account
FROM susu.susu_rounds r
JOIN susu.susu_groups g ON g.id = r.susu_group_id
WHERE r.status = 'DISBURSING';
```

If this query returns rows, the round is stuck. Continue below.

### 6.2 Check Payments Service transfer status

The disbursement may have succeeded in Payments but the monolith didn't write the COMPLETED state:

```sql
-- In payments_db
SELECT lt.transaction_reference, lt.transaction_type,
       lt.amount, lt.created_at,
       le.direction, le.amount AS entry_amount
FROM ledger.ledger_transactions lt
JOIN ledger.ledger_entries le ON le.ledger_transaction_id = lt.id
WHERE lt.business_reference_id = '<ROUND_ID>'
  AND lt.transaction_type = 'SUSU_DISBURSEMENT'
ORDER BY lt.created_at DESC;
```

**If transfer exists in Payments:** the disbursement succeeded but the monolith didn't mark it COMPLETED. Manual recovery needed (§6.5).

**If no transfer found:** the Payments call failed. Check the Payments Service logs for the idempotency key `susu-disbursement-<ROUND_ID>`:

```sql
-- In payments_db
SELECT key_value, state, response_status_code, response_body
FROM idempotency.idempotency_keys
WHERE key_value = 'susu-disbursement-<ROUND_ID>';
```

### 6.3 Check the RabbitMQ dead-letter queue

```bash
# Via management UI or CLI
rabbitmqctl list_queues name messages consumers

# Get messages from the DLQ
curl -u guest:guest \
  "http://rabbitmq.internal:15672/api/queues/%2F/susu.disbursement.dlq/get" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"count": 10, "requeue": false, "encoding": "auto"}'
```

Each DLQ message contains the original `round_id` and `correlation_id`. Use these to trace the failure in Payments logs.

### 6.4 Check monolith logs for P0 alerts

```bash
grep "P0_ALERT" logs/monolith.log | grep "<ROUND_ID>"
```

Common P0 patterns and their meanings:

| Log message | Cause | Action |
|---|---|---|
| `DISBURSEMENT_FAILURE] Cannot resolve wallet for recipient=` | Payments Service down or recipient USER_WALLET not provisioned | Check Payments health; re-run disbursement after recovery |
| `DISBURSEMENT_FAILURE] Transfer failed for round=` | Ledger transfer rejected | Check SUSU_POT balance; check Payments logs |
| `SusuPotIntegrityChecker: DRIFT DETECTED` | Pot balance ≠ expected | Do not manually disburse; escalate for financial review |
| `round= exhausted 3 attempts. MANUAL INTERVENTION REQUIRED` | Worker gave up after 3 retries | Investigate root cause, then trigger manual recovery |

### 6.5 Manual recovery: force-complete a stuck disbursement

**Only perform these steps after confirming the Payments transfer succeeded** and the SUSU_POT was correctly debited.

1. **Verify pot is now empty** (all funds have moved to the recipient wallet):
   ```sql
   -- In payments_db
   SELECT sum(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END) AS pot_balance
   FROM ledger.ledger_entries
   WHERE account_id = '<SUSU_POT_LEDGER_ACCOUNT_ID>';
   -- Expected: 0 (all funds disbursed)
   ```

2. **Mark the round COMPLETED** in the monolith:
   ```sql
   UPDATE susu.susu_rounds
   SET status = 'COMPLETED',
       disbursed_at = now(),
       disbursement_transaction_id = '<transaction_id from Payments>'
   WHERE id = '<ROUND_ID>'
     AND status = 'DISBURSING';
   ```

3. **Advance the group** to the next round:
   ```sql
   -- Determine next round number
   SELECT round_number + 1 AS next_round
   FROM susu.susu_rounds
   WHERE id = '<ROUND_ID>';

   -- Update the group's current round
   UPDATE susu.susu_groups
   SET current_round_number = <NEXT_ROUND_NUMBER>
   WHERE id = '<GROUP_ID>';

   -- Open the next round
   UPDATE susu.susu_rounds
   SET status = 'COLLECTING'
   WHERE susu_group_id = '<GROUP_ID>'
     AND round_number = <NEXT_ROUND_NUMBER>
     AND status = 'PENDING';
   ```

4. **Generate contributions for the next round:**
   ```sql
   -- Find the next round ID and all active members
   -- Then INSERT contribution rows
   INSERT INTO susu.susu_contributions
     (id, susu_round_id, susu_group_id, member_user_id,
      expected_amount, status, collection_attempt_count,
      penalty_amount, is_late, created_at)
   SELECT
     gen_random_uuid(),
     '<NEXT_ROUND_ID>',
     '<GROUP_ID>',
     m.user_id,
     g.contribution_amount,
     'PENDING',
     0, 0, false, now()
   FROM susu.susu_memberships m
   JOIN susu.susu_groups g ON g.id = m.susu_group_id
   WHERE m.susu_group_id = '<GROUP_ID>'
     AND m.status = 'ACTIVE';
   ```

5. **Verify integrity after recovery:**
   ```sql
   SELECT round_number, status, recipient_user_id
   FROM susu.susu_rounds
   WHERE susu_group_id = '<GROUP_ID>'
   ORDER BY round_number;
   -- Expected: round N=COMPLETED, round N+1=COLLECTING, rest=PENDING

   SELECT count(*) FROM susu.susu_contributions
   WHERE susu_round_id = '<NEXT_ROUND_ID>'
     AND status = 'PENDING';
   -- Expected: number of active members
   ```

6. **Log the incident** in §8 of this document.

### 6.6 If recipient's membership was REMOVED

If the disbursement stuck because the recipient left or was removed after the round entered COLLECTING, the disbursement worker skip logic should have caught this. If it didn't:

```sql
-- Verify recipient membership status
SELECT status, removed_at
FROM susu.susu_memberships
WHERE susu_group_id = '<GROUP_ID>'
  AND user_id = '<RECIPIENT_USER_ID>';
```

If `status = 'REMOVED'` or `'LEFT'`, the round should be `SKIPPED` not `DISBURSING`. Manual recovery:

```sql
-- Mark round SKIPPED instead of COMPLETED
UPDATE susu.susu_rounds
SET status = 'SKIPPED'
WHERE id = '<ROUND_ID>'
  AND status = 'DISBURSING';

-- Then advance group and open next round as in §6.5 steps 3-5
```

The skipped pot amount rolls into the next round's `expected_pot_amount`:

```sql
UPDATE susu.susu_rounds
SET expected_pot_amount = expected_pot_amount + <SKIPPED_ACTUAL_POT_AMOUNT>
WHERE susu_group_id = '<GROUP_ID>'
  AND round_number = <NEXT_ROUND_NUMBER>;
```

---

## 7. Known Failure Modes

This section grows with each beta session. Add new entries here immediately after resolving an incident.

| Failure | Root cause | Resolution | First seen |
|---|---|---|---|
| Contribution PAID in Payments but PENDING in monolith | Monolith transaction rolled back after Payments call succeeded (integrity check or network issue) | §5.4 recovery steps | — |
| Round stays DISBURSING after worker restart | `attemptCounts` in-memory counter resets; worker retries up to 3 more times | Wait for worker retry; if still stuck after 5 min, use §6.5 | — |
| `susu_pot_integrity_drift_count = 1` on contribution 6 | Race condition: two contributions arrived simultaneously, second contribution's integrity check ran before first settled | Contact engineering — do not disburse until drift is explained | — |
| DLQ message with `DISBURSEMENT_FAILURE: 422` | SUSU_POT balance insufficient for disbursement (pot was short due to MISSED contributions) | Verify `actual_pot_amount` is correct, update it if needed, set round back to DISBURSING | — |

---

## 8. Runbook Update Log

| Date | Updated by | Section updated | What changed |
|---|---|---|---|
| 2026-06-29 | Initial version | All | First version written for v0.4 beta |

---

## Appendix A: Quick SQL reference

All queries tagged `-- monolith_db` run against `monolith_db`. All tagged `-- payments_db` run against `payments_db`.

```sql
-- Check a group's full state (monolith_db)
SELECT g.id, g.name, g.status, g.current_round_number,
       COUNT(m.id) FILTER (WHERE m.status='ACTIVE') AS active_members
FROM susu.susu_groups g
LEFT JOIN susu.susu_memberships m ON m.susu_group_id = g.id
WHERE g.id = '<GROUP_ID>'
GROUP BY g.id;

-- Check all rounds for a group (monolith_db)
SELECT round_number, status, recipient_user_id,
       expected_pot_amount, actual_pot_amount,
       disbursed_at
FROM susu.susu_rounds
WHERE susu_group_id = '<GROUP_ID>'
ORDER BY round_number;

-- Check SUSU_POT balance (payments_db)
SELECT sum(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END) AS balance
FROM ledger.ledger_entries le
JOIN ledger.ledger_accounts la ON la.id = le.account_id
WHERE la.owner_id = '<GROUP_ID>'
  AND la.account_type = 'SUSU_POT';

-- All pending contributions past due (monolith_db)
SELECT c.id, c.member_user_id, c.expected_amount, r.scheduled_collection_at
FROM susu.susu_contributions c
JOIN susu.susu_rounds r ON r.id = c.susu_round_id
WHERE c.status = 'PENDING'
  AND r.status = 'COLLECTING'
  AND r.scheduled_collection_at < now()
ORDER BY r.scheduled_collection_at ASC;

-- Check outbox for any pending susu events (payments_db)
SELECT event_type, status, attempts, created_at
FROM outbox.outbox_events
WHERE event_type LIKE 'susu.%'
  AND status != 'SENT'
ORDER BY created_at DESC
LIMIT 20;
```

---

*This runbook is linked from the Stash Module Boundaries doc §Operational Runbooks. Raise a PR to update it; the on-call engineer reviews and merges.*
