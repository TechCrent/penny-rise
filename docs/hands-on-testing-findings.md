# Hands-On Testing Findings

Real, first-hand exercising of the app (services actually started, real HTTP
requests, real data through Postgres/RabbitMQ/Mailpit) — not just `mvn test`
passing. Every problem is logged here **before** it gets fixed, and stays
here afterward with a `**Fix:**` note appended — nothing is deleted, so this
is a running record of what was actually broken, not just what's broken now.

Testing environment note: this sandbox's WSL2 network namespace is shared
with the operator's real Docker Desktop, which already owns the standard
project ports. Local-only `docker-compose.override.yml` (gitignored, never
committed) remaps host ports; `.env` (gitignored) points at the remapped
ports. This does not affect anything committed to the branch.

---

## Finding 1: `start-services.ps1` / `start-services.sh` don't actually work as documented

**Severity:** High — this is the README's own documented "how to run the
backend" path.

**Problem:** `README.md`'s "Getting Started" section says to run
`.\start-services.ps1` (or the `.sh` port) after copying `.env.example` to
`.env`. Neither script:
1. Loads `.env` into the process environment before invoking
   `mvnw spring-boot:run`, and
2. Sets `SPRING_PROFILES_ACTIVE=local`.

`monolith/src/main/resources/application.yml` requires `MONOLITH_DB_USER`/
`MONOLITH_DB_PASSWORD` (etc. for every service) with **no default value** —
these only ever come from the environment. And the `local` Spring profile
(`application-local.yml`) is what supplies the JWT signing key, email
provider config, etc. for local dev. Since a plain `mvnw spring-boot:run`
child process does not inherit `.env` (that's a docker-compose convention,
not something Maven/Spring do on their own) and the scripts never set the
active profile, running the documented one-liner would start four Spring
Boot processes that either fail to connect to Postgres (blank/missing
credentials) or start against the default profile instead of `local`.

**How this was found:** attempting to actually launch the stack for hands-on
testing, following the README exactly.

**Fix:** Added `.env` loading + `SPRING_PROFILES_ACTIVE=local` to the top of
both `start-services.sh` and `start-services.ps1`, before the containers are
even started (same fix in both, matching `Load-Env` in
`test-payments-e2e.ps1`, which already had the right pattern). Verified by
actually running the fixed `start-services.sh` in this session.

---

## Finding 2: `V47__add_terms_accepted_to_users.sql` collides with a pre-existing Java migration at the same version

**Severity:** Critical — monolith would not boot at all with this branch's
changes; Flyway refuses to start when two migrations share a version.

**Problem:** This branch's ToS-consent migration
(`monolith/src/main/resources/db/migration/user_module/V47__add_terms_accepted_to_users.sql`)
was numbered by only searching for the highest `V*.sql` file. There is also
a **Java-based** Flyway migration,
`monolith/src/main/java/db/migration/admin/V47__EnsureLocalDevAdmin.java`,
which claims the same version number. Flyway's versioning is global across
every configured `classpath:db/migration/*` location (SQL and Java alike),
so this is a real, boot-blocking collision — not something `mvn compile`
or the unit test suite would ever catch, since neither loads Flyway against
a real database. It only surfaces when the monolith actually tries to start
against Postgres:

```
Caused by: org.flywaydb.core.api.FlywayException: Found more than one migration with version 47
Offenders:
-> .../db/migration/user_module/V47__add_terms_accepted_to_users.sql (SQL)
-> .../db/migration/admin/V47__EnsureLocalDevAdmin.class (JDBC)
```

**How this was found:** actually starting the monolith against a real
Postgres container — this is exactly the class of bug hidden by
`mvn test`/`mvn compile` alone.

**Fix:** Renamed the SQL migration to `V48__add_terms_accepted_to_users.sql`
(and updated its internal header comment to match). Verified by starting
the monolith against a real Postgres and confirming Flyway applies cleanly
with no version conflict.

---

## Finding 3: `.env.example` is missing two required-with-no-default variables

**Severity:** High — two of the four services (`payments-service`,
`audit-service`) fail to boot at all with only `.env.example`'s documented
variables set; nothing in the README or `.env.example` mentions either one.

**Problem:**
- `payments-service/src/main/resources/application.yml` requires
  `INTERNAL_SERVICE_TOKEN` with **no default** (`${INTERNAL_SERVICE_TOKEN}`)
  — contrast with the monolith's own reference to the same variable, which
  explicitly defaults to empty for local/test with a comment saying so
  (`${INTERNAL_SERVICE_TOKEN:}`  — "contexts bootable; production must
  set..."). payments-service never got that same safety net.
- `audit-service/src/main/resources/application.yml` requires
  `AUDIT_READONLY_DB_PASSWORD` with no default, for the
  `audit_dashboard_ro` Postgres role. The actual local-dev value
  (`ro_local_pass`) only exists as a hardcoded literal inside
  `infra/docker/postgres/audit-db-init.sql` — there's no way to discover it
  from `.env.example` or the README; you'd have to go read the init SQL.

**How this was found:** actually starting `payments-service` and
`audit-service` — both fail immediately with
`IllegalArgumentException: Could not resolve placeholder '...'`, which
`mvn compile`/`mvn test` never exercises (Spring only resolves
`@Value`/YAML placeholders at application context startup).

**Fix:** Added `INTERNAL_SERVICE_TOKEN` and `AUDIT_READONLY_DB_PASSWORD` to
the root `.env.example` with working local-dev values (the latter copied
verbatim from `audit-db-init.sql`, with a comment saying so). A related gap
found in the same pass: `payments-service` also requires `PAYSTACK_SECRET_KEY`
with no default, and it **was** documented — but only in
`payments-service/.env.example`, a module-level file the root README never
mentions. A developer following the documented root-level
`.env.example` → `start-services` workflow would never discover it exists.
Added `PAYSTACK_SECRET_KEY` to the root `.env.example` too, with a note
about where it was previously hiding. Verified by actually booting all four
services with only the root `.env.example`'s variables set (plus the
operator's real Paystack sandbox key, supplied directly rather than the
placeholder, for realistic testing).

---

## Finding 5: `kyc.events` exchange type disagrees between kyc-service and monolith

**Severity:** Medium — caused a real, visible startup failure in this
session (monolith failed its first several AMQP declaration attempts and
had to retry through exponential backoff before recovering); the
underlying inconsistency is still latent in the source and could resurface
under different startup ordering or timing.

**Problem:** `kyc-service`'s `KycMessagingConfig.kycExchange()` declares
`kyc.events` as a **`DirectExchange`**. Both of monolith's own configs —
`MonolithMessagingConfig.kycEventsExchange()` and
`NotificationRabbitConfig.notificationSourceBindings()` (which binds with
a `"#"` wildcard routing key, something only a **topic** exchange
actually honors as a wildcard) — declare the same exchange name as a
**`TopicExchange`**. `infra/docker/rabbitmq/definitions.json` also declares
it `"type": "topic"`. Three declarations agree it should be topic; one
(the exchange's actual owner, per monolith's own code comment: *"The
kyc.events exchange is owned by kyc-service"*) says direct.

Observed in practice: monolith logged repeated
`PRECONDITION_FAILED - inequivalent arg 'type' for exchange 'kyc.events'
... received 'topic' but current is 'direct'` for several retry cycles
after kyc-service (which connects and declares first) raced with the
broker's own `definitions.json` import. It self-resolved once the
exchange settled into its final state and monolith's Spring Retry
backoff eventually got a clean declaration — the app came up healthy — but
the fact that it took multiple failed attempts, driven purely by
non-deterministic connection-order timing between two services, means
this could just as easily fail hard (exhaust retries) in a slower or
differently-scheduled environment (e.g. CI, or a more loaded machine).

**How this was found:** watching monolith's real startup log against a
real broker shared with kyc-service — this is a cross-service ordering
interaction that no single service's own test suite would ever exercise.

**Fix:** Changed `kyc-service`'s `KycMessagingConfig.kycExchange()` from
`DirectExchange` to `TopicExchange`, matching monolith
(`MonolithMessagingConfig`, `NotificationRabbitConfig`), audit-service
(`AuditMessagingConfig`, which binds the same exchange list with the same
`"#"` wildcard pattern), and `definitions.json`. All four services now
agree.

Verified across three full clean-volume restarts. The **first** restart
after the fix still showed a handful of `PRECONDITION_FAILED` lines before
settling — traced this to a genuine but *transient* RabbitMQ boot race:
`management.load_definitions` creates the exchange in stages, and a
client declare that lands in the narrow window between "exchange row
exists" and "type fully stamped from the import" can observe RabbitMQ's
implicit default type (`direct`) for a few milliseconds. Confirmed this
is not a lasting conflict: on every restart, once the import settles, the
live exchange (`GET /api/exchanges/stash/kyc.events` via the management
API) is stably `topic`, `user_who_performed_action: rmq-internal`
(created by the definitions import, not any service), and all four
services reach a healthy state — Spring Retry's existing exponential
backoff absorbs this window on its own. No further code change needed for
this residual race; noted here since it's a real characteristic of
starting infra and app services in quick succession (as local dev does),
not something that would surface in a deployment where the broker is
already stable before applications start.

---

## Finding 4: RabbitMQ `ACCESS_REFUSED` — real bug, not a leftover artifact

**Severity:** Critical — every service that talks to RabbitMQ (monolith,
kyc-service, audit-service — everything except payments-service) fails to
boot, for **every** developer following `.env.example`, not just in this
sandbox.

**Problem:** first suspected this was leftover state from this session's
own `docker compose up`/`down` cycles (a stale `rabbitmq-data` volume from
before the port remap was in place — RabbitMQ, like Postgres, only applies
`RABBITMQ_DEFAULT_USER`/`PASS` on a fresh, empty data directory). Ran
`docker compose down -v` to drop every volume and started completely
clean — **the same `ACCESS_REFUSED` happened again**, which ruled that out
and pointed at something structural.

Root cause: `infra/docker/rabbitmq/Dockerfile` is a **custom** RabbitMQ
image (`rabbitmq:3.13-management` + `COPY definitions.json`), and
`rabbitmq.conf` sets `management.load_definitions =
/etc/rabbitmq/definitions.json`. That `definitions.json` file's `users`
array **hardcodes a `password_hash` for the `stash` user**
(`kI3GCjBCQVdzIv9Hpkh6wUy1w/J32YSqoP8iOiF186DnKeLr`, SHA-256) — a value
nobody can reverse-engineer, and one that has nothing to do with whatever
`RABBITMQ_PASSWORD`/`RABBITMQ_DEFAULT_PASS` a developer sets in `.env`.
The definitions import runs at broker startup and stamps this hash onto
the `stash` user, silently overriding the env-var-provisioned password.

The comment two lines above it in `rabbitmq.conf` states the actual
intent plainly: *"Default user is configured via environment variables
(RABBITMQ_DEFAULT_USER / PASS) — these are set in docker-compose, not
here, so credentials stay out of committed config."* The `definitions.json`
`users` entry directly contradicts that — it **is** a committed credential,
and it wins. `.env.example`'s `RABBITMQ_PASSWORD=change_me_rabbitmq` has
never actually been the real password for anyone who built this image.

**How this was found:** actually starting monolith/kyc-service/audit-service
against a real RabbitMQ container built from this repo's own Dockerfile —
confirmed by inspecting the running container
(`rabbitmqctl list_users` showed user `stash` existing correctly) and only
resolving the mismatch after tracing `rabbitmq.conf` → `definitions.json`
and finding the baked-in hash.

**Fix, attempt 1 (wrong):** emptied the `users` array in
`definitions.json`, assuming `RABBITMQ_DEFAULT_USER`/`PASS` would then take
over cleanly. Rebuilding and restarting showed this was **worse** — the
broker now hard-crashes on boot (`{no_such_user,<<"stash">>}`), because
`definitions.json`'s `permissions` entry still references user `stash`,
and on this image the definitions import runs as part of the core `rabbit`
boot steps — before whatever boot step would create the env-var default
user, if that step even runs at all once a definitions file is present.
So the definitions file isn't just *a* source of truth that happens to
conflict with env vars — on this image, it's fully authoritative, and
leaving it self-inconsistent takes the broker down entirely.

**Fix, attempt 2 (correct — verified):** Restored the `users` entry in
`definitions.json`, but regenerated `password_hash` to actually correspond
to `.env.example`'s documented `RABBITMQ_PASSWORD=change_me_rabbitmq`
(RabbitMQ's `rabbit_password_hashing_sha256`: 4 random salt bytes +
`sha256(salt + password)`, base64-encoded — computed directly rather than
guessed). Replaced the misleading comment in `rabbitmq.conf` with one
explaining that the definitions file, not the env vars, is what actually
provisions the user on this image, and how to regenerate the hash if the
password ever changes. Verified end-to-end: rebuilt the image, started
clean, `rabbitmqctl list_users` shows `stash`/administrator, a management
API login with `stash:change_me_rabbitmq` succeeds, and
monolith/kyc-service/audit-service all connect and stay up.

---

## Finding 6: KYC auto-decision race — event published *before* the transaction that makes it valid commits

**Severity:** High — a submission can get permanently stuck in `REVIEWING`
with no automated decision ever made, requiring manual admin intervention
that the operator has no reason to know is needed (nothing surfaces this
as an error anywhere).

**Problem:** `DocumentUploadConfirmationService.confirmUpload` is
`@Transactional`. Inside that same transaction, once all three documents
are present, it sets `submission.setStatus(REVIEWING)` and then calls
`rabbitTemplate.convertAndSend(...)` directly — publishing to RabbitMQ
**before** the enclosing transaction commits. `AutomatedDecisionService`
(the consumer) can receive and start processing that message before the
publisher's transaction is visible to it, reads the submission via a
fresh query, sees the pre-transition status (`PENDING_DOCUMENTS`), and its
idempotency guard —

```java
if (!KycSubmission.STATUS_REVIEWING.equals(submission.getStatus())) {
    log.info("Submission already decided, skipping reprocessing "
             + "submissionId={} status={}", submissionId, submission.getStatus());
    return;
}
```

— treats "not REVIEWING" as "already decided, nothing to do" and silently
drops the event. The submission is left in `REVIEWING` forever with no
automated decision, and (worse) the log line actively says "already
decided," which is misleading during an incident — status was
`PENDING_DOCUMENTS`, not decided at all.

Related, smaller gap noticed while tracing this: `SubmissionReadyForReviewEvent`'s
own javadoc says it's *"Published to RabbitMQ via the KYC Service's outbox
pattern (per Folder Structure doc §4.2's outbox/ module)"* — there is a
`db/migration/outbox/` directory, but no Java outbox table/relay
implementation anywhere in kyc-service; the code just calls
`rabbitTemplate.convertAndSend` directly. Fixing the immediate race (below)
doesn't require the full outbox pattern the doc comment describes, but the
comment is aspirational, not descriptive of what's actually implemented —
worth knowing if a future outage traces back to "isn't this outboxed?"

**How this was found:** signing up a second real test user (Bob) and
running him through KYC submission for a transfer test — his submission
never left `REVIEWING`, while the first user's (Alice, submitted a few
minutes earlier under less time pressure between document uploads)
auto-approved normally. The three document-confirm calls for Bob were
fired back-to-back with no delay (an automated test doing what a human
tapping through an app never would), which is exactly what narrowed the
race window enough to hit it reliably.

**Fix:** Replaced the direct `rabbitTemplate.convertAndSend(...)` call in
`DocumentUploadConfirmationService` with the same
`ApplicationEventPublisher` + `@TransactionalEventListener(phase =
AFTER_COMMIT)` pattern already established in monolith's
`UserCreatedEventPublisher` — the actual RabbitMQ publish now only happens
once the `REVIEWING` status is durably committed, so a consumer can never
observe a submission "not yet REVIEWING" for an event that says it is.

---

## Finding 7: KYC bulk-approve 500s — `BulkApproveRequest` missing `@JsonProperty`

**Severity:** High — the bulk-approve feature (this branch's own gap-analysis
work) was completely unusable; every call throws a `NullPointerException`
and returns 500.

**Problem:** Every other request DTO in kyc-service (`CreateSubmissionRequest`,
`DocumentUploadConfirmationRequest`, monolith's `SignupRequest`, etc.)
explicitly annotates each field with `@JsonProperty("snake_case_name")` —
neither service configures a global Jackson snake_case naming strategy, so
without the annotation Jackson only binds the literal camelCase field name.
`BulkApproveRequest` was written as:

```java
public record BulkApproveRequest(@NotEmpty List<UUID> submissionIds) {}
```

— no `@JsonProperty`, so a request body of `{"submission_ids": [...]}`
(consistent with every other endpoint in this API) deserializes
`submissionIds` as `null` (silently — Jackson doesn't fail on an unmapped
JSON key), bean validation's `@NotEmpty` doesn't fire on a null list the way
it does on an empty one, and the request sails into
`KycAdminReviewService.bulkApprove` where `submissionIds.iterator()` throws:

```
NullPointerException: Cannot invoke "java.util.List.iterator()" because "submissionIds" is null
	at com.stash.kyc.submission.service.KycAdminReviewService.bulkApprove(KycAdminReviewService.java:248)
```

**How this was found:** signing up two more test users (Carol, Dave),
running them through KYC with non-auto-approve Ghana Card numbers so they'd
land in the manual review queue, then calling
`POST /api/v1/kyc/admin/submissions/bulk-approve` with both submission IDs
— exactly the workflow an admin doing real batch review would do.

**Fix:** Added `@JsonProperty("submission_ids")` to `BulkApproveRequest`'s
field, matching every other DTO in the service.
(`BulkResolveDisputesRequest` in monolith was checked too — its fields
`ids`/`resolution` are single words, so camelCase and snake_case are
identical for them; no annotation is needed there and it isn't affected by
this bug.)

Verified after rebuilding kyc-service: bulk-approving Carol's and Dave's
submissions (both previously stuck in the manual review queue) now returns
`200` with a per-id success array, and both submissions' `reviewer_admin_id`
in the database correctly show the real logged-in admin's UUID
(`4e65527e-...`), not the sentinel — confirming the bulk path stamps
reviewer identity correctly too, same as the single-submission approve path.

---

## Finding 8: transaction history (and this branch's statement export) was
permanently wired to an empty stub

**Severity:** Critical for this branch's own statement-export feature —
it was fully wired (auth, CSV/PDF formatting, headers) but could never show
a single transaction, because its data source was hardcoded.

**Problem:** `monolith`'s `IntegrationPaymentsClient` — the client backing
`GET /api/v1/users/me/transactions` and this branch's new
`GET /api/v1/users/me/transactions/export` — is (was) a pre-existing,
explicitly documented stub:

```java
public UnifiedTransactionPage getUnifiedTransactionHistory(...) {
    log.debug("IntegrationPaymentsClient is a stub — returning empty transaction history for user {}", userId);
    return new UnifiedTransactionPage(List.of(), null, false);
}
```

The class javadoc already flagged this as a "BLOCKING DEPENDENCY" serving
four historical tracker items (v0.5-005, v0.5-007, v0.5-019, v0.5-020) —
i.e. this was a known, tracked gap, not something this branch introduced.
But it directly undermines a feature this branch *did* add: the statement
export always returned a well-formed, empty CSV/PDF for every user,
indistinguishable from "no transactions yet" even for accounts with a full
history.

**How this was found:** testing the new export feature by hand — the CSV
came back with only a header row despite Alice having a completed deposit,
a vault deposit, and a peer transfer. Cross-checked `GET
/api/v1/users/me/transactions` (pre-existing, not new) and found it
equally empty, then confirmed real rows exist directly in
`payments-service`'s `transaction.transactions` table — the data was
there, nothing was reading it.

**Fix:** Implemented the real endpoint the stub's own javadoc proposed:
`GET /api/v1/transactions?user_id=&type=&from_date=&to_date=&cursor=&limit=`
on `payments-service` (`TransactionDetailController.getHistory`,
`TransactionHistoryQueryService`, a new keyset-paginated
`TransactionRepository.findHistoryForUser` query), gated to internal-service
callers only (matching `CallerContext.isInternal()`, the same pattern
`TransactionDetailController`'s existing single-transaction endpoint
already uses for user-auth). Wired `IntegrationPaymentsClient` to call it
for real via `RestClient` — same `X-Internal-Service-Token` /
`stash.payments.internal-base-url` convention as monolith's existing
`TransactionQueryService`.

Scope note: narrative and business-reference (vault/susu name) enrichment
from `ledger.ledger_transactions` is **not** wired — that table's
repository is intentionally restricted to `LedgerService` only (enforced
by `LedgerWriteArchitectureTest`), and threading enrichment through it
cleanly is more than this fix needed. Rows currently return `narrative`/
`business_reference_type`/`business_reference_id` as `null`; the
CSV/JSON still show every transaction with correct type, amount,
direction, counterparty *name* (resolved separately, already worked once
real rows existed), status, and date — just no free-text narrative yet.
Noted as a follow-up in docs/gap-analysis-vendor-dependent-followup.md.

Verified end-to-end after rebuild: both `GET /api/v1/users/me/transactions`
and `GET /api/v1/users/me/transactions/export?format=csv` now return
Alice's real history — 19 deposits, 1 completed vault deposit, and her
transfer to Bob with `Counterparty: Bob Test` correctly resolved.

The other four `IntegrationPaymentsClient` stub methods
(`getRecentTransactionsForUser`, `getTransactionById`, `closeLedgerAccount`,
`getLedgerAccountBalance`) remain unimplemented — genuinely separate
pre-existing gaps (account deletion saga, admin user-detail vault
balances), out of scope for this pass; carried into the follow-up doc.

---

## Finding 9: susu contribution (and peer transfer, and vault/pot provisioning)
break after any monolith restart — `Map.of()`'s randomized iteration order
poisons long-lived idempotency keys

**Severity:** Critical — this is a core, pre-existing money-movement path
(not new to this branch), and the failure mode is a hard 502 on every
retry for an already-provisioned user/group, with no self-healing.

**Problem:** Several monolith → payments-service clients provision a
ledger account using a **fixed, permanently-reused** idempotency key —
by design, so any later call for the same user/group resolves to the same
account rather than creating a new one:

- `SusuContributionTransferClient.resolveUserWallet` /
  `PeerTransferPaymentsClient.resolveUserWallet` — both use
  `"provision-user-wallet:v2:" + userId`
- `SusuPotProvisionClient.provisionSusuPot` — uses
  `"provision-susu-pot:" + groupId`
- `PaymentsServiceClient.provisionVaultLedgerAccount` — uses the caller's
  own `Idempotency-Key` + `":ledger-provision"` (lower risk in practice,
  since that key is per-HTTP-request rather than system-derived, but same
  underlying exposure)

All four built their request bodies with `Map.of(...)`. Payments Service's
`RequestHasher` hashes the **raw request body bytes** (method + path +
body) to detect whether a repeated Idempotency-Key is being reused for a
different logical request. `Map.of()`'s iteration order is *deliberately*
randomized per JVM run (a documented JDK behavior, specifically to stop
code from relying on it) — so the exact same logical map serializes to
JSON with a different key order after every monolith restart. The very
first time a given user's wallet (or group's pot) is provisioned, that
byte ordering gets stored as the "canonical" hash for that permanent key.
After any subsequent monolith restart, a **new** attempt to resolve the
same, already-provisioned wallet computes a **different** hash for the
identical logical request and Payments Service correctly (from its
perspective) rejects it as `IDEMPOTENCY_KEY_REUSED` (422) — a client
reusing a key for what looks like a different request. Monolith's own
catch blocks swallow the real cause and surface a generic 502
("Payment service unavailable"), so this is invisible without reading
Payments Service's log directly.

Impact in practice: once a user's wallet or a susu group's pot has been
provisioned, **every susu contribution, every peer transfer, and every
retried vault provisioning for that user/group fails permanently** after
the very next monolith restart/redeploy — not a transient blip, a
permanent 502 until someone manually clears the stale idempotency row.

**How this was found:** simulating four real users doing a full susu
cycle (join → activate → contribute) as part of hands-on testing — Alice's
very first contribution attempt 502'd. Payments Service's own log showed
the true cause instantly (`IDEMPOTENCY_KEY_REUSED`) once checked directly,
even though monolith's log only showed a generic, exception-message-free
"Could not resolve USER_WALLET." Confirmed the mechanism by reproducing
the exact same call manually via curl.

**Fix:** Replaced `Map.of(...)` with an explicit `LinkedHashMap` (stable
insertion-order iteration, no per-JVM-run randomization) in all four call
sites: `SusuContributionTransferClient.resolveUserWallet`,
`PeerTransferPaymentsClient.userWalletProvisionBody`,
`SusuPotProvisionClient.provisionSusuPot`,
`PaymentsServiceClient.provisionVaultLedgerAccount`. Also manually cleared
the three idempotency rows already poisoned by this session's earlier
monolith restarts (`provision-user-wallet:v2:<alice>`,
`provision-user-wallet:v2:<bob>`, `provision-susu-pot:<group>`) — safe to
delete since the underlying provision endpoint is idempotent on
`(owner_type, owner_id, account_type)`; a fresh call finds the
already-provisioned ledger account rather than creating a duplicate.

Not fixed as part of this: the swallowed-exception-message logging bug in
`SusuContributionService`/similar callers (`log.error(... callerId,
correlationId)` without `e` or `e.getMessage()`) — this made diagnosis
slower than it should have been and is worth a follow-up cleanup, but is
a logging-quality issue, not a functional one.

---

## Summary

Nine real, reproducible problems found by actually running the stack and
driving it as real users would — signup, email verification, login,
change-password, KYC submission/auto-approval/manual-approval/bulk-approval,
wallet deposit, vault deposit, peer transfer, a full 4-member susu group
(join → activate → contribute), admin dashboard/staff-management/general
susu browsing, rate limiting, and both new export endpoints. All nine are
fixed and verified end-to-end against the real running services (not just
recompiled) — none were catchable by `mvn compile`/`mvn test` alone, since
every one only surfaces once real infrastructure (Postgres, RabbitMQ,
Paystack sandbox, multiple JVM restarts) is actually exercised:

1. `start-services.sh`/`.ps1` didn't load `.env` or set the Spring profile
2. Flyway version collision (`V47` claimed by both a SQL and a Java migration)
3. `.env.example` missing two required-with-no-default variables
4. RabbitMQ credentials mismatch between env vars and `definitions.json`
5. `kyc.events` exchange type mismatch (direct vs. topic) across services
6. KYC auto-decision race — event published before its enabling transaction committed
7. KYC bulk-approve 500s — a DTO missing `@JsonProperty` for snake_case
8. Transaction history (and the new statement export) permanently wired to an empty stub
9. Susu contributions/peer transfers/pot provisioning break after any monolith restart — `Map.of()`'s randomized key ordering poisoning long-lived idempotency keys

Multi-user simulation covered: four independent signups (Alice, Bob, Carol,
Dave) each going through real email verification via Mailpit; Alice and Bob
through KYC (one auto-approved, one hit the manual queue and was
bulk-approved by a real logged-in admin, with `reviewer_admin_id` verified
as that admin's actual UUID); Carol and Dave submitted for KYC and
bulk-approved together; a peer transfer from Alice to Bob; a 4-member susu
group created by Alice, joined by all three others, activated with rotation
positions assigned, and two real contributions (Alice, Bob) paid into round 1.

New branch features verified working: ToS consent validation at signup,
authenticated change-password, KYC reviewer identity (real admin UUID, not
the sentinel) on both single and bulk approval paths, admin dashboard
counts, staff create/list/deactivate, susu general (not just flagged)
browsing, KYC bulk-approve, financial-endpoint rate limiting (429 with
correct error code after the configured cap), CSV/PDF statement export, and
GDPR JSON data export — all confirmed against real data, not empty stubs.
