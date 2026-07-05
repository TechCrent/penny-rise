# Paystack API Reference — Stash Configuration & Integration

Reference for Paystack **Transactions**, **Transaction Splits**, and **Subaccounts** APIs, plus how the Stash **payments-service** uses them locally.

**Base URL:** `https://api.paystack.co`  
**Auth header:** `Authorization: Bearer <SECRET_KEY>`  
**Content-Type:** `application/json`

---

## Stash local configuration

### Required `.env` variables (repo root)

| Variable | Purpose |
|----------|---------|
| `PAYSTACK_SECRET_KEY` | API key — `sk_test_...` (sandbox) or `sk_live_...` (production) |
| `PAYSTACK_WEBHOOK_SECRET` | HMAC secret for `POST /webhooks/paystack` signature verification |
| `PAYSTACK_SETTLEMENT_ACCOUNT_ID` | UUID of the system `PAYSTACK_SETTLEMENT` ledger account (seeded in payments DB) |
| `INTERNAL_SERVICE_TOKEN` | Monolith → payments-service auth |

### Sandbox-only settings (`payments-service` `application.yml`)

```yaml
paystack:
  base-url: https://api.paystack.co
  secret-key: ${PAYSTACK_SECRET_KEY}
  webhook-secret: ${PAYSTACK_WEBHOOK_SECRET}
  sandbox:
    settlement-bank-code: "002"          # NOT "TEST" — Paystack rejects "TEST"
    settlement-account-number: "0000000000"
```

### Sandbox test credentials (Paystack docs)

| Use case | Value |
|----------|-------|
| Subaccount settlement bank | `002` |
| Subaccount account number | `0000000000` |
| MoMo test phone (MTN Ghana) | `0551234987` |
| MoMo provider | `mtn` |
| Test card (no validation) | `4084084084084081`, any future expiry, CVV `408` |

> **Common pitfall:** Bank code `"TEST"` returns `Settlement Bank is invalid`. Use `"002"` in test mode.

### How Stash calls Paystack today

| Stash flow | Paystack endpoint | Class |
|------------|-------------------|-------|
| User signup → wallet | `POST /subaccount` | `PaystackSubaccountService` |
| MoMo deposit | `POST /charge` | `PaystackClient.initiateCharge()` |
| Webhook confirmation | (incoming) `charge.success` | `PaystackWebhookController` |
| Reconciliation | `GET /transaction/verify/:reference` | `PaystackClient.verifyTransaction()` |

Stash does **not** currently use `POST /transaction/initialize` (hosted checkout URL). Deposits use **direct MoMo charge** via `/charge` with the user's subaccount code.

Deposits require a per-user subaccount in `paystack.paystack_sub_accounts`. Without it, `DepositService` returns **409** before calling Paystack.

### Manual sandbox test

```powershell
# Subaccount + charge integration tests (requires PAYSTACK_SECRET_KEY in env)
$env:PAYSTACK_SECRET_KEY = "sk_test_..."   # from .env
.\mvnw.cmd -pl payments-service test -Dtest=PaystackSandboxIT -Dgroups=paystack-sandbox
```

### Re-provision failed subaccounts

If subaccount messages landed in `payments.paystack.subaccount.provision.dlq`:

1. Fix sandbox bank code (see above).
2. Restart payments-service.
3. Re-publish outbox payloads to RabbitMQ exchange `payments.events` with routing key `paystack.subaccount.provision.requested`.

---

## Subaccounts API

Create and manage subaccounts. Stash creates one subaccount per user at wallet provisioning time.

### Create Subaccount

`POST /subaccount`

**Body parameters**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `business_name` | String | Yes | Display name for the subaccount |
| `settlement_bank` | String | Yes | Bank code from List Banks (`GET /bank?currency=GHS`) |
| `account_number` | String | Yes | Settlement account number |
| `percentage_charge` | Float | Yes | % retained by main account (Stash uses `0.0`) |
| `description` | String | No | Free-text description |
| `primary_contact_email` | String | No | Contact email |
| `primary_contact_name` | String | No | Contact name |
| `primary_contact_phone` | String | No | Contact phone |
| `metadata` | String | No | Stringified JSON custom fields |

**cURL**

```sh
curl https://api.paystack.co/subaccount \
  -H "Authorization: Bearer YOUR_SECRET_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "business_name": "Oasis",
    "settlement_bank": "058",
    "account_number": "0123456047",
    "percentage_charge": 30
  }'
```

**Sample response (201)**

```json
{
  "status": true,
  "message": "Subaccount created",
  "data": {
    "subaccount_code": "ACCT_6uujpqtzmnufzkw",
    "business_name": "Oasis",
    "settlement_bank": "Guaranty Trust Bank",
    "account_number": "0123456047",
    "percentage_charge": 30,
    "currency": "NGN",
    "domain": "test",
    "active": true
  }
}
```

### List Subaccounts

`GET /subaccount`

Query: `perPage`, `page`, `from`, `to`

### Fetch Subaccount

`GET /subaccount/:id_or_code`

### Update Subaccount

`PUT /subaccount/:id_or_code`

Body: `business_name`, `description`, `bank_code`, `account_number`, `active`, `percentage_charge`, `settlement_schedule`, etc.

---

## Transactions API

### Initialize Transaction

`POST /transaction/initialize`

Creates a hosted checkout session (returns `authorization_url`). Useful for card/bank checkout flows.

**Body parameters**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `amount` | String | Yes | Amount in currency subunit (pesewas for GHS) |
| `email` | String | Yes | Customer email |
| `channels` | Array | No | e.g. `["card","mobile_money","bank_transfer"]` |
| `currency` | String | No | Defaults to integration currency |
| `reference` | String | No | Unique ref (`-`, `.`, `=`, alphanumeric only) |
| `callback_url` | String | No | Override dashboard callback URL |
| `metadata` | String | No | Stringified JSON |
| `split_code` | String | No | Transaction split code e.g. `SPL_98WF13Eb3w` |
| `subaccount` | String | No | Subaccount code e.g. `ACCT_8f4s1eq7ml6rlzj` |
| `transaction_charge` | Integer | No | Override split amount for one payment |
| `bearer` | String | No | `account` or `subaccount` (who pays fees) |

**cURL**

```sh
curl https://api.paystack.co/transaction/initialize \
  -H "Authorization: Bearer YOUR_SECRET_KEY" \
  -H "Content-Type: application/json" \
  -d '{"email": "customer@email.com", "amount": "20000"}'
```

**Sample response (200)**

```json
{
  "status": true,
  "message": "Authorization URL created",
  "data": {
    "authorization_url": "https://checkout.paystack.com/3ni8kdavz62431k",
    "access_code": "3ni8kdavz62431k",
    "reference": "re4lyvq3s3"
  }
}
```

### Verify Transaction

`GET /transaction/verify/:reference`

Confirm payment status after checkout or charge.

### List Transactions

`GET /transaction`

Query: `perPage`, `page`, `customer`, `status`, `from`, `to`, `amount`

### Fetch Transaction

`GET /transaction/:id`

### Charge Authorization

`POST /transaction/charge_authorization`

Charge a reusable authorization code.

Body: `amount`, `email`, `authorization_code`, optional `reference`, `currency`, `subaccount`, `bearer`, `queue`

### View Transaction Timeline

`GET /transaction/timeline/:id_or_reference`

### Transaction Totals

`GET /transaction/totals`

### Export Transactions

`GET /transaction/export`

### Partial Debit

`POST /transaction/partial_debit`

Body: `authorization_code`, `currency` (`NGN` or `GHS`), `amount`, `email`, optional `reference`, `at_least`

---

## Transaction Splits API

Split settlement across your main account and one or more subaccounts.

### Create Split

`POST /split`

**Body parameters**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Split name |
| `type` | String | Yes | `percentage` or `flat` |
| `currency` | String | Yes | Supported currency |
| `subaccounts` | Array | Yes | `[{subaccount: "ACCT_xxx", share: 50}, ...]` |
| `bearer_type` | String | Yes | `subaccount`, `account`, `all-proportional`, or `all` |
| `bearer_subaccount` | String | No | Required when `bearer_type` is `subaccount` |

**cURL**

```sh
curl https://api.paystack.co/split \
  -H "Authorization: Bearer YOUR_SECRET_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Halfsies",
    "type": "percentage",
    "currency": "NGN",
    "subaccounts": [{"subaccount": "ACCT_6uujpqtzmnufzkw", "share": 50}]
  }'
```

**Sample response (200)**

```json
{
  "status": true,
  "message": "Split created",
  "data": {
    "split_code": "SPL_RcScyW5jp2",
    "name": "Halfsies",
    "type": "percentage",
    "currency": "NGN",
    "active": true
  }
}
```

Use `split_code` on `POST /transaction/initialize` to apply the split.

### List Splits

`GET /split` — query: `name`, `active`, `sort_by`, `perPage`, `page`, `from`, `to`

### Fetch Split

`GET /split/:id`

### Update Split

`PUT /split/:id` — body: `name`, `active`, `bearer_type`, `bearer_subaccount`

### Add/Update Subaccount in Split

`POST /split/:id/subaccount/add` — body: `subaccount`, `share`

### Remove Subaccount from Split

`POST /split/:id/subaccount/remove` — body: `subaccount`

---

## Stash deposit flow (end-to-end)

```
Mobile → Monolith (:8080) → Payments (:8081)
  → DepositService
    1. Validate ledger account + MoMo fields
    2. Resolve user subaccount code (409 if missing)
    3. Create PENDING transaction row
    4. POST /charge (email, amount, mobile_money, currency, subaccount)
    5. Return 202 + paystack_reference
  → Paystack webhook charge.success
    → Credit ledger account
```

### Verified local test (2026-07-04)

After fixing sandbox bank `002` and re-provisioning subaccounts:

```http
POST http://localhost:8081/api/v1/transactions/deposits
Idempotency-Key: e2e-key-002

{
  "ledger_account_id": "<user_wallet_uuid>",
  "user_id": "<user_uuid>",
  "amount": 1000,
  "payment_method": "MOMO",
  "customer_email": "user@example.com",
  "mobile_number": "0551234987",
  "mobile_provider": "mtn",
  "correlation_id": "e2e-deposit-002"
}
```

**Response:** `202` with `paystack_reference` (e.g. `3khixvaumsb8h8k`).

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Deposit 409 — no subaccount | Subaccount provisioning failed | Check DLQ; fix bank code; re-publish provision events |
| `Settlement Bank is invalid` | Using `"TEST"` bank code | Use `"002"` in sandbox |
| Charge 422 — test mobile money | Non-test phone number | Use `0551234987` (MTN) in sandbox |
| Paystack never called | Monolith down or no subaccount | Start monolith; provision subaccounts first |
| Messages in `*.provision.dlq` | Consumer failed after retries | Fix root cause, restart service, re-queue |

---

## References

- [Paystack API docs](https://paystack.com/docs/api/)
- [Paystack test payments](https://paystack.com/docs/payments/test-payments/)
- Stash code: `payments-service/.../paystack/client/PaystackClient.java`
- Stash config: `payments-service/src/main/resources/application.yml`
