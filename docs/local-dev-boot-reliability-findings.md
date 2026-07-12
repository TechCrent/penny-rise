# Local Dev Boot Reliability Findings — 2026-07-08

Investigation into "mobile takes too many tries to boot" and "monolith has
errors" (also checked payments-service, kyc-service, audit-service). Logged
here **before** each fix, per the usual practice (see
`docs/hands-on-testing-findings.md`) — nothing deleted, `**Fix:**` appended
after.

Headline: every symptom traced back to the local Windows/WSL2 environment,
not application code. `mvn compile` is clean across all modules and no
service threw an application-level startup exception once port contention
was ruled out.

---

## Finding 1: Orphaned Expo/Jest/Vite processes squatting ports for days

**Severity:** High — direct cause of "boots take too many tries."

**Problem:** 13 `node.exe` processes were still running from a session dated
July 4 — a `pnpm start` → `expo start --port 19000` chain (4 processes), 6
orphaned `jest-worker` children from the same run, and an orphaned
`admin-console` Vite dev server (3 processes). None had been cleanly shut
down. Every subsequent `expo start` hit "port 19000 is being used by another
process" and had to negotiate a fallback port.

**How found:** `Get-CimInstance Win32_Process -Filter "Name='node.exe'"`,
cross-referenced each PID's command line against `netstat -ano` listeners on
port 19000.

**Fix:** Killed all 13 (user-confirmed). Verified port 19000 free of node
listeners immediately after.

---

## Finding 2: Stale static `netsh portproxy` rules squatting ports 8080-8083 and 19000

**Severity:** Critical — this is the actual root cause of both "mobile boot
friction" and "monolith has errors." It fully explains the intermittent
`svchost.exe` (IP Helper / `iphlpsvc`) ownership of these ports seen
throughout this investigation.

**Problem:** `netsh interface portproxy show all` revealed five static
forwarding rules, each mapping `0.0.0.0:PORT → 127.0.0.1:PORT` for exactly
the five ports this project uses (8080 monolith, 8081 payments, 8082 kyc,
8083 audit, 19000 Metro). These are almost certainly leftovers from an old
LAN/tunnel-exposure experiment and were never cleaned up. `iphlpsvc`
implements `portproxy`, which is why it kept showing up as the port owner no
matter what was tried against WSL2 (killing WSL processes, even
`wsl --terminate Ubuntu`, had zero effect — this was never a WSL issue).

Effect: forwarding `localhost:X` to itself is a no-op for real traffic, but
it intermittently blocks the real application from binding the same port
directly, causing `PortInUseException` / `BindException: Address already in
use` — reproduced identically for all of monolith, payments-service,
kyc-service, and audit-service in clean boot tests (no other Java process
running) once WSL zombies were ruled out.

**How found:** `netsh interface portproxy show all`, after ruling out WSL2,
Docker Desktop, and application-level Rabbit/vhost misconfiguration as
causes (all checked and confirmed correct).

**Fix:** Requires administrator elevation, which this session doesn't have.
User needs to run, in an elevated PowerShell:

```powershell
netsh interface portproxy delete v4tov4 listenport=8080 listenaddress=0.0.0.0
netsh interface portproxy delete v4tov4 listenport=8081 listenaddress=0.0.0.0
netsh interface portproxy delete v4tov4 listenport=8082 listenaddress=0.0.0.0
netsh interface portproxy delete v4tov4 listenport=8083 listenaddress=0.0.0.0
netsh interface portproxy delete v4tov4 listenport=19000 listenaddress=0.0.0.0
```

*(Status: pending as of this writing — see top of doc / session notes for
whether this has since been done and re-verified.)*

---

## Finding 3: Forgotten WSL2 Ubuntu environment running a stale checkout, intercepting the same backend ports

**Severity:** High — actively misleading. Any tool hitting `localhost:8080`
on Windows was silently answered by this stale copy instead of erroring or
reaching the real (not-currently-running) Windows-side service.

**Problem:** All four backend services (`monolith`, `payments-service`,
`kyc-service`, `audit-service`) were running inside the "Ubuntu" WSL2
distro, launched via `mvn spring-boot:run` on **July 6** at
`/home/crent/stash-app/stash` — a separate, stale checkout, not the Windows
working tree this session operates on. WSL2's `wslrelay.exe` auto-forwards
listening ports onto Windows `localhost` via `[::1]` (IPv6 loopback), so
`curl http://localhost:8081/actuator/health` returned real (but broken)
responses — `db: DOWN`, `rabbit: DOWN` — from this stale environment, not
from any Windows-side process (zero `java.exe` processes were running
natively at the time).

A currently-running Maven surefire test (`SessionServiceTest`, started
05:43 same day) was also found inside WSL — but `SessionService` does not
exist anywhere in the current Windows `monolith` source, confirming this WSL
checkout has diverged significantly and is not simply "a day or two behind."
Left untouched (recent, possibly active work, out of scope for this pass).

**How found:** `curl` to the four health endpoints showed Linux filesystem
paths (`/home/crent/...`) in the disk-space health check details — impossible
for a native Windows JVM. Cross-referenced via `wsl -- ps aux` and
`wslrelay.exe`'s listening sockets in `netstat -ano`.

**Fix:** Killed the four stale `spring-boot:run` processes inside WSL
(user-confirmed). Did not touch the still-running `SessionServiceTest`
process or investigate the divergent WSL checkout further — flagged for the
user's awareness, not part of this fix.

---

## Finding 4: Expo's built-in ngrok tunnel (`--tunnel`) is still unreliable

**Severity:** Medium — confirms prior findings, no regression, but the retry
requested this session did not succeed.

**Problem:** Per `EXPO_DEBUG=1` output, `expo start --tunnel` generates a
hostname (`*.exp.direct`) but then fails: `CommandError: ngrok tunnel took
too long to connect.` Reproduced twice, consistent with the documented
2026-06-27 findings (`@expo/ngrok` session closures / timeouts) — cleaning up
the zombie processes and port conflicts (Findings 1-2) did not change this;
it's ngrok's anonymous-tier infrastructure, not local environment noise.

**How found:** Ran `pnpm start` (temporarily reconfigured to `--tunnel`)
live, twice; confirmed via `EXPO_DEBUG=1` verbose ngrok logging.

**Fix:** Reverted `mobile/package.json`'s default `start` script back to
`--lan` (reliable, already verified working). Kept `--tunnel` available as
an explicit opt-in (`pnpm start:tunnel`) for when the phone has no LAN path
to the dev machine — same tradeoff as before, just no longer the default.
Also changed Metro's port from 19000 → 8090 (Finding 2 made 19000
unreliable independent of ngrok) and added `EXPO_OFFLINE=1` to the default
script, which sidesteps a separate, previously-diagnosed flaky network call
(`api.expo.dev` native-module version check) unrelated to tunneling.

---

## Finding 5 (pre-existing, not newly discovered): `IntegrationPaymentsClient` stub methods in monolith

**Severity:** Medium — real functional gaps, but already tracked.

**Problem:** Already documented in
`docs/gap-analysis-vendor-dependent-followup.md` (written in a prior
session, PR #402): `getRecentTransactionsForUser`, `getTransactionById`,
`closeLedgerAccount`, and `getLedgerAccountBalance` on monolith's
`IntegrationPaymentsClient` are hardcoded stubs. Concretely:
- Admin user-detail page shows no transaction history for a user.
- Account deletion's ledger-close step (v0.5-019 saga step 3/4) is a no-op —
  deleting an account does not close its ledger account in Payments Service.
- `AdminUserService.toVaultSummary` hardcodes vault balances to `0L` in the
  admin view.

Also documented there: swallowed exception messages in payments-client catch
blocks (e.g. `SusuContributionService.payContribution`) that log a fixed
string instead of the caught exception's message, making failures harder to
diagnose.

**Status:** Not fixed in this pass — these are pre-existing, deliberately
scoped-out follow-up items from a previous gap analysis, not something this
investigation newly found broken. Flagging here for visibility since the
user asked to check payments-service for errors; see the source doc for the
full recommended fix shape before picking these up.

---

## Finding 6: `monolith/application-local.yml` hardcoded a different JWT signing key than every other service — broke all admin-console auth

**Severity:** Critical — every admin-console feature that calls audit-service or
kyc-service (audit log, and likely the KYC review queue) was silently
returning 403, with no indication why.

**Problem:** `monolith/src/main/resources/application.yml` (base) correctly
reads `signing-key: ${JWT_SIGNING_KEY}` from `.env`, matching
`audit-service` and `kyc-service`'s identical `${JWT_SIGNING_KEY}` config —
this is the shared secret that lets those services verify admin JWTs
monolith issues. But `application-local.yml` (active whenever
`SPRING_PROFILES_ACTIVE=local`, i.e. always in this local setup)
**overrode it with a hardcoded placeholder**:
`dGVzdC1zaWduaW5nLWtleS1mb3ItbG9jYWwtZGV2LW9ubHktMzItY2hhcg==` — a leftover
value that doesn't match `.env`. Every admin JWT issued locally was signed
with this placeholder; audit-service/kyc-service verified against the real
`.env` key; signatures never matched;
`AuditAdminJwtAuthenticationFilter`/`AdminJwtVerifier` silently cleared the
auth context on every request. This is a regression of a near-identical bug
`AdminJwtVerifier.java`'s own comment says was already fixed once
(v0.5-033).

**How found:** logged in as the seeded local dev admin
(`admin@stash.local`), called `GET /api/v1/admin/audit-log` directly —
403 both through the monolith proxy and straight against audit-service with
the identical token. Compared `signing-key` config across all three
services; found the override.

**Fix:** removed the hardcoded `signing-key`/`verification-keys` override from
`monolith/application-local.yml`, letting it inherit the base
`application.yml`'s `${JWT_SIGNING_KEY}` (same as audit-service/kyc-service —
no local-only override needed, one shared key for all). Verified:
`GET /api/v1/admin/audit-log` returns 200 after restart, not 403.

---

## Finding 7: Every service's outbox events were 100% rejected by audit-service — DLQ had 204 messages, `audit_log_entries` had 0 rows

**Severity:** Critical — the entire audit trail was non-functional; this is
also very likely why the admin console's audit tab looked broken even after
Finding 6's auth fix, and is related to (but distinct from) why activity
wasn't showing up where expected.

**Problem:** `AuditEventConsumer.onMessage()` read `eventId`/`eventType` from
the AMQP standard message **properties** (`props.getMessageId()` /
`props.getType()`). But every publisher — `OutboxRelay` (payments-service)
and monolith's direct publishers (`UserCreatedEventPublisher`,
`SusuEventPublisher`, `VaultUnlockedEventPublisher`,
`SusuRoundFullyCollectedEventPublisher`) — sets `event_id`/`event_type` as
custom message **headers**, never the AMQP properties. So
`eventId`/`eventType` were always null, tripping the consumer's own
null-check guard and throwing on literally every message, which RabbitMQ
then routed to `audit.events.dlq` after retries. 204 messages had
accumulated there; `audit_log_entries` had zero rows ever written. The
consumer's own doc comment anticipated exactly this mismatch scenario
("If OutboxRelay wraps things differently... change this parsing only").

Separately, four of monolith's direct publishers
(`UserCreatedEventPublisher`, `SusuEventPublisher`,
`VaultUnlockedEventPublisher`, `SusuRoundFullyCollectedEventPublisher`)
never set an `event_id` header at all (only `correlation_id`/`event_type`) —
would have kept failing even after the consumer fix, just with a different
null field.

**How found:** RabbitMQ management API showed `audit.events.dlq` with 204
ready messages and `audit.events.incoming` with 0 (fully drained by a
connected-but-failing consumer). Peeked one dead-lettered message via the
`/queues/.../get` API — `x-death` header showed `reason: rejected`; message
properties had no `message_id`/`type` fields, only custom headers including
`event_type`.

**Fix:**
1. `audit-service/src/main/java/com/stash/audit/consumer/AuditEventConsumer.java` —
   read `event_id`/`event_type`/`correlation_id` from `message.getMessageProperties().getHeaders()`
   instead of AMQP properties.
2. Added a missing `event_id` header (`UUID.randomUUID()`) to:
   `UserCreatedEventPublisher.java`, `SusuEventPublisher.java`,
   `VaultUnlockedEventPublisher.java`, `SusuRoundFullyCollectedEventPublisher.java`.

Verified end-to-end: fresh signup → `user.created` and
`payments.paystack.subaccount.provision.requested` events both appear in
`GET /api/v1/admin/audit-log` within seconds.

**Follow-up (fixed in a later pass, 2026-07-11):**
- `challenge/event/ChallengeEventPublisher.java` embedded `event_id` inside
  the JSON **body** instead of a header — fixed by adding
  `.setHeader("event_id", UUID.randomUUID().toString())` and
  `.setHeader("event_type", ROUTING_KEY)` to the `MessageBuilder` chain,
  same pattern as the four publishers above.
- kyc-service's `SubmissionReadyForReviewEventPublisher.java` (the only
  publisher in that service — no siblings) set no custom headers at all,
  since it uses `rabbitTemplate.convertAndSend(exchange, key, pojo)` rather
  than `MessageBuilder`. Fixed with a `MessagePostProcessor` lambda on the
  `convertAndSend` call that sets `event_id`/`event_type`/`correlation_id`
  headers from the already-constructed event POJO's fields.

**Not fixed / follow-up needed:**
- The 204 pre-existing DLQ messages were left in place (not replayed) —
  historical audit data before this fix is permanently missing unless
  someone writes a one-off script to replay `audit.events.dlq` through the
  fixed consumer.

---

## Finding 8: `kyc.events` RabbitMQ exchange is still typed `direct` in the live broker, but code declares it `topic`

**Severity:** Medium — narrower than Finding 7; doesn't block the general
audit pipeline (only bindings/publishes specifically through `kyc.events`),
but is a live, currently-reproducing error.

**Problem:** On monolith startup:
```
Shutdown Signal: channel error; protocol method: #method<channel.close>
(reply-code=406, reply-text=PRECONDITION_FAILED - inequivalent arg 'type'
for exchange 'kyc.events' in vhost 'stash': received 'topic' but current is
'direct', class-id=40, method-id=10)
```
`infra/docker/rabbitmq/definitions.json` declares `kyc.events` as `type: topic`
(matching what the code expects), but the **running broker's persisted
Docker volume** still has the exchange as `direct` from before that fix was
made — `definitions.json` only applies to a fresh/empty volume, it doesn't
retroactively redefine an already-existing exchange. This is the same
underlying issue `docs/hands-on-testing-findings.md` Finding 5 documents as
already fixed in code — the code fix never propagated to this machine's
already-provisioned RabbitMQ volume.

**Effect observed:** the channel used for this declaration is closed by the
broker; Spring AMQP's `CachingConnectionFactory` recovers on a new channel
and the rest of the app continues normally (confirmed via
`/actuator/health` showing `rabbit: UP` immediately after), but anything
specifically bound through `kyc.events` on that failed channel doesn't get
declared correctly.

**Fix:** Deleted and recreated the `kyc.events` exchange as `topic` via the
RabbitMQ management API (`DELETE`/`PUT` on
`/api/exchanges/stash/kyc.events`), then restored all 7 bindings that
existed beforehand (captured via `GET .../bindings/source` before deleting)
so nothing was lost: `audit.events.incoming` and `notification.events.incoming`
(both `#`), `kyc.provider.ready_for_review.queue`
(`kyc.submission.ready_for_review`), and the approved/rejected pairs for
both `kyc.deletion_scheduler.*.queue` and `monolith.kyc.*.queue`. Verified
via `GET /api/exchanges/stash/kyc.events` that `type` is now `topic` and via
`GET .../bindings/source` that all 7 bindings are back. User confirmed this
path (over the fresh-volume alternative) since it doesn't touch any other
exchange's queued/unacked messages.

---

## Summary

| Area | Root cause | Status |
|---|---|---|
| Mobile boot friction | Orphaned processes + stale portproxy rules | Fixed |
| Mobile tunnel mode | `@expo/ngrok` unreliable (confirmed again) | Reverted to LAN default |
| "Monolith has errors" | Same stale portproxy rules + stale WSL zombie | Fixed (user ran elevated netsh cleanup) |
| Payments/KYC/Audit compile & boot | No code-level errors found | Verified clean once env fixed |
| `IntegrationPaymentsClient` stubs | Pre-existing, already tracked | Not in scope this pass — flagged |
| Admin console 403s (audit tab, likely KYC queue too) | JWT signing-key override mismatch in `application-local.yml` | **Fixed** |
| Audit log always empty | Consumer read AMQP properties; publishers use headers | **Fixed** (verified end-to-end) |
| `kyc.events` exchange type mismatch | Stale RabbitMQ volume vs. current code | **Fixed** — exchange deleted/recreated as topic, bindings restored |
| Transaction history | Backend confirmed working (real data returned directly from payments-service) | Likely was infra-down at time of testing; re-verify now that services are healthy |
| "Send" money flow | Wallet-to-wallet peer transfer (`POST /api/v1/transfers`, keyed by `recipient_user_id`) — not vault-to-vault | Explained, not a bug |
