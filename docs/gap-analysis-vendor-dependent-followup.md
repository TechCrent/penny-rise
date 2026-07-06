# Gap Analysis — Vendor-Dependent Follow-Up

This tracks the items from the v0.5 gap analysis that were **not** implemented in
`feature/v0.5-gap-analysis-fixes` because they require a third-party vendor
account/credentials, a product decision only Stash can make, or are large
enough in scope to warrant their own dedicated pass. Everything else from the
gap analysis was implemented — see that branch's commits for what shipped
(KYC reviewer identity fix, mobile account/security surface, admin dashboard/
staff/bulk-actions, ToS consent, rate limiting, statement export, GDPR data
export).

For each item: what's missing, why it's blocked locally, and what decision or
resource needs to land before it can be scheduled as real work.

---

## Already fixed — no action needed

The gap analysis report claimed the admin-console login page was still a
hardcoded shared-token form. That's stale: `admin-console/src/routes/login/LoginPage.tsx`
is already a real per-admin email/password form hitting
`POST /api/v1/admin/auth/login`. No work needed there — this note exists so a
future reader doesn't re-flag it.

---

## 1. AML / sanctions / PEP screening

**Gap:** KYC only verifies the Ghana Card + selfie match. No watchlist or
politically-exposed-person check exists anywhere in `kyc-service`.

**Why blocked:** Requires a screening data vendor — e.g. ComplyAdvantage,
Refinitiv World-Check, or a Ghana-specific provider. No local, credential-free
substitute exists; watchlist data itself is the product.

**Decision needed:** Which vendor, and whether screening runs synchronously
at KYC submission time (blocking) or asynchronously as a post-approval check
(the KYC stub-provider pattern in `AutomatedDecisionService` gives a template
for wiring a new provider client either way). Relevant given v3.0 already
anticipates SEC Ghana engagement — this is likely to become a hard
requirement, not an optional hardening item.

---

## 2. SMS channel (OTP / alerts)

**Gap:** Notifications are push/email/in-app only (v0.5-013). No SMS-based
OTP or transaction alerts, unusual for a Ghana-market mobile-money-adjacent
product where SMS is often the most reliable channel.

**Why blocked:** Requires an SMS provider account — Twilio, Hubtel,
Africa's Talking, or a local aggregator — plus a Ghana sender-ID
registration, which is a business/compliance process, not just an API key.

**Decision needed:** Provider choice (cost per SMS in Ghana varies
significantly between Twilio and local aggregators like Hubtel), and which
events warrant SMS (OTP only, or also large-transaction alerts). The existing
`EmailSender`/`stash.email.provider` switch pattern
(`monolith/src/main/resources/application.yml`) is a reasonable template for
an `SmsSender` interface with a pluggable provider.

---

## 3. Device fingerprinting / fraud & anomaly detection

**Gap:** No fraud signal beyond the KYC provider's own document check. No
device fingerprinting, velocity checks, or anomaly scoring on
deposits/transfers/susu contributions.

**Why blocked:** This isn't one feature but an open-ended capability —
"fraud detection" can mean anything from a simple device-ID + IP velocity
rule to a full ML risk-scoring pipeline. Building something real requires
either a vendor SDK (e.g. Sardine, Seon, or a fingerprinting library like
FingerprintJS Pro) or a bespoke rules engine informed by real fraud
patterns Stash hasn't observed yet (no meaningful transaction volume to
learn from).

**Decision needed:** Whether to start with a cheap, local-only rule (e.g.
flag >N transfers from one device/IP in an hour — buildable without a
vendor, using the same Bucket4j infrastructure this pass added for rate
limiting) versus committing to a vendor SDK. Recommend the local rule as a
v1.0-ish follow-up; defer the vendor conversation until there's real
transaction volume to justify it.

---

## 4. Recurring billing / Premium subscription auto-renewal

**Gap:** `SubscriptionPaystackClient.initializeTestSubscription` /
`createTestSubscription` both `throw UnsupportedOperationException` — Premium
upgrade is a one-time charge with no renewal job, and there's no persisted
payment/invoice history a user could review (confirmed while researching this:
`Subscription` is a mutable single-row-per-user table with no history table,
per its own class javadoc).

**Why blocked:** Recurring billing isn't just a missing feature — it's a
vendor API integration decision. Paystack offers two different subscription
models (their native "Plans + Subscriptions" API, versus rolling your own
via stored authorization + a scheduled re-charge job), each with different
webhook handling, proration, and failure/retry semantics. Building either
without picking one first would mean building the wrong thing.

**Decision needed:** Which Paystack billing model to adopt, and whether a
`subscription_payments` history table is added at the same time (needed
regardless of which model is chosen, since "view payment history" has
nothing to query today).

---

## 5. KYC document storage — Supabase Storage migration

**Gap:** `LocalFilesystemObjectStorage` is the only `ObjectStorage`
implementation in `kyc-service`. Real Supabase Storage integration
(v1.0-003) is still open on the tracker.

**Why blocked:** Requires a Supabase project and credentials — this is
already correctly tracked as v1.0-003, not a gap in the roadmap. No action
needed here beyond the existing awareness that local storage only works
because dev runs locally; this must land before any hosted environment can
review a real KYC submission.

---

## 6. Mobile crash reporting (Sentry or equivalent)

**Gap:** No crash reporting SDK anywhere in the mobile app.

**Why blocked:** Requires a Sentry (or Bugsnag/Crashlytics) account and DSN.
Trivial to wire in once a vendor is chosen (`@sentry/react-native` + a few
lines in `App.tsx`), but the account itself is the blocker, not the code.

**Decision needed:** Vendor choice (Sentry is the common default for Expo
apps) and whether error reporting should be opt-in given no
privacy-policy/consent framework exists yet beyond the placeholder ToS added
in this pass.

---

## 7. Mobile dark mode & localization

**Gap:** No dark mode, no localization/i18n — `userInterfaceStyle: "light"`
is hardcoded in `app.json`, and all UI copy is inline English strings.

**Why blocked — not vendor-blocked, but deliberately deferred:** Unlike the
items above, these don't need any third party. They were left out of this
pass because they're open-ended in scope, not a single well-defined change:
dark mode touches every screen's hardcoded hex colors (no theme/token file
exists — confirmed `mobile/src/theme/` is empty except `.gitkeep`), and
localization requires extracting every inline string into a translation
catalog across ~30 screens. The original gap-analysis report itself flagged
both as "lower priority... worth flagging before wider release," not
blocking.

**Decision needed:** None — these are shovel-ready whenever there's
capacity, no vendor or product decision required. Recommend dark mode first
if picked up, since it's more self-contained (a theme token file + a
find-and-replace pass) than localization (needs a translation vendor or
in-house translator regardless of the code work).

---

## Discovered during hands-on testing (not vendor-blocked, just out of scope for this pass)

These surfaced while actually running the app end-to-end for
`docs/hands-on-testing-findings.md` — real gaps, no vendor needed, but each
is either a separate pre-existing feature (not something this branch
touched) or bigger than the specific bug it was found alongside.

### `IntegrationPaymentsClient`'s remaining stub methods

Finding 8 fixed `getUnifiedTransactionHistory` (it was blocking this
branch's own statement-export feature). The other four methods on that
same class are still hardcoded stubs, each serving a real, separate
tracked gap:

- `getRecentTransactionsForUser` / `getTransactionById` — used by the admin
  user-detail page; an admin looking at a user's account today sees no
  transaction history at all.
- `closeLedgerAccount` — v0.5-019's account-deletion saga's step 3/4 is a
  no-op; deleting an account today does not actually close its ledger
  account in Payments Service.
- `getLedgerAccountBalance` — `AdminUserService.toVaultSummary` hardcodes
  vault balances to `0L` in the admin user-detail view for the same reason.

Each needs its own real Payments Service endpoint, following the same
pattern Finding 8 established (`CallerContext.isInternal()`-gated, called
via `RestClient`/`WebClient` with the shared internal-service-token
convention). Recommend doing these as one follow-up pass rather than
piecemeal, since they're all the same shape of fix.

### Transaction history has no narrative or vault/susu-name enrichment

Finding 8's real transaction-history endpoint returns `narrative`,
`business_reference_type`, and `business_reference_id` as `null` for every
row. The data exists (`ledger.ledger_transactions.narrative` /
`business_reference_id`), but that table's repository is intentionally
restricted to `LedgerService` only (enforced by
`LedgerWriteArchitectureTest`), and wiring enrichment through it cleanly
needs a proper batch-lookup method added to `LedgerService` itself rather
than a quick join. Every other field (type, amount, direction, status,
counterparty *name*, date) is real and correct today — this is polish, not
a blocker, but worth closing so a transfer's "why" isn't blank in a user's
exported statement.

### Swallowed exception messages in payments-client catch blocks

`SusuContributionService.payContribution` (and the equivalent pattern
elsewhere) logs `"Could not resolve USER_WALLET for user={} correlation={}"`
without including the caught exception's message or the exception object
itself. This made Finding 9 much slower to diagnose than it should have
been — the real cause (`IDEMPOTENCY_KEY_REUSED`) was sitting in Payments
Service's log the whole time, but monolith's own log gave no hint. Worth a
sweep across similar `catch (SusuPaymentsException e)` /
`catch (TransferPaymentsException e)` blocks to make sure the underlying
message is always logged (`log.error("...: {}", e.getMessage(), e)`), not
just a hardcoded string.

---

## Suggested order if picked up

1. **ToS/consent-adjacent:** nothing left here — done in this pass.
2. **AML/PEP screening** — highest regulatory relevance given v3.0's SEC
   Ghana engagement; start the vendor conversation early since procurement
   takes longer than the integration itself.
3. **Recurring billing** — needed for Premium to be a real, sustainable
   revenue feature rather than a one-time charge.
4. **SMS channel** — mostly a "when we have Ghana users at scale" nice-to-have
   than a blocker.
5. **Device fingerprinting** — start with the local, vendor-free velocity
   rule; revisit vendor SDKs once there's real fraud data to justify one.
6. **Crash reporting, dark mode, localization** — pick up opportunistically;
   none of these block anything else.
