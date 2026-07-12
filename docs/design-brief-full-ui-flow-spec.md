# Stash — Full UI Flow Spec (Home, Navigation & Every Downstream Screen)

Status: **design agreed for Home/Nav (§1–§11), implemented in prototype.**
Sections 12+ are new — they extend the original Home & Navigation spec to
document every other screen that exists in the current prototype
(`Stash Prototype.dc.html`), including screens Home/Explore/Profile lead
into, the auth flow, and the internal admin console. Same rules as before:
this is a flow/behavior spec (what exists, what state it can be in, what
tapping it does) — not a visual spec.

---

## 1. Global structure

Two structural layers persist across the app (except where noted):

1. **Bottom navigation bar** — visible only on the three top-level
   destinations and their direct listing children: **Home, Explore,
   Profile, Vault List, Susu List, Wallet (full screen), Challenges List**.
   Hidden on every other screen (detail screens, flows, modals) — those
   are full-screen pushes with their own back arrow.
2. **No top header settings bar.** Settings access lives entirely in the
   Profile tab.
3. **Auth gate.** Welcome and Sign in are the only screens reachable while
   signed out. Successful sign-in/create-account lands on Home (Total
   state). Signing out (from Settings) returns to Welcome.

---

## 2. Home screen — structure overview

Same three mutually-exclusive states as before: **Total** (default),
**Savings**, **Wallet**.

```
┌─────────────────────────────────────┐
│  Good morning, Akua Mensah            │  ← greeting, top-left
│  [ Total ]   Savings    Wallet        │  ← swipeable/scrubbable state picker
│                                       │
│         GHS  X,XXX.XX                │  ← hero balance card (state-colored)
│                                       │
│   [Deposit]   [Send]   [Withdraw]    │  ← Total state only
│                                       │
│   ─────────── state body ───────     │
│                                       │
└─────────────────────────────────────┘
        [ Home ]  [ Explore ]  [ Profile ]
```

Implementation note vs. the original mockup: the switcher is a vertical
picker wheel (Total / Savings / Wallet cycling), scrubbed by drag, with a
side dash-indicator — functionally identical to a segmented control
(tap/drag changes state in place, active label is scaled up/bright,
inactive labels dimmed). Everything below still swaps completely per
state.

---

## 3. The state switcher (Total / Savings / Wallet)

- Dragging/tapping the picker changes the active state immediately: label
  emphasis updates, the hero card's label/value/footer swap, and the
  state body below is replaced. No route change; back/gesture exits Home
  as normal.
- Hero card is visually distinct per state: **Total** = blue gradient
  card; **Wallet** state's dedicated full screen (§14) uses a **green**
  gradient card instead, visually separating "your money that's landed"
  from "your money that's growing."
- Default on cold open / tapping the Home tab is always **Total**.

---

## 4. Home — Total state (default)

### 4.1 Hero balance
Sum of all vaults + wallet. Has a show/hide toggle (eye icon) that masks
the figure with `••••••` app-wide-style privacy, and a footer line noting
what the figure represents.

### 4.2 Action row: Deposit / Send / Withdraw
Visible only in Total state.
- **Deposit** → vault-only account picker (§16). Zero vaults routes
  straight to Create Vault instead.
- **Send** → Send Money flow (§18) directly, no picker.
- **Withdraw** → vault-only account picker (§16), same zero-vault
  fallback.

### 4.3 Portfolio Summary
Two stat tiles side by side, display-only: vault count, susu group count.

### 4.4 Challenges section
"Challenges" heading + "View all" link → Challenges List (§21). Each
active-challenge card below (icon, name, sub-progress label, % complete,
progress bar) opens Challenge Detail (§22) on tap.

---

## 5. Home — Savings state

- Hero shows Savings total (sum of vault balances) on the same blue card.
- Action row hidden.
- **Your vaults** — heading + "See all" → Vault List (§13). Body lists a
  vault preview (name, sub-label, progress bar, balance); tapping a row
  opens Vault Detail (§13.1).
- **Vault activity** — heading + "See all" → History filtered to vault
  scope (§20, `histScope='vault'`). Body lists recent vault-only
  transactions (deposits, withdrawals); tapping a row opens Transaction
  Detail (§23).

---

## 6. Home — Wallet state

- Hero shows Wallet balance only, still on the blue Total-style card
  (the dedicated green wallet styling is reserved for the full Wallet
  screen reached via Explore/"See all", §14).
- Action row hidden.
- **Idle-payout nudge card** (implemented, not just "nice to have"): when
  a susu payout has landed and not been moved, a green-tinted card reads
  "**{amount} is sitting in your wallet** · {source} · move it into a
  vault to keep it safe." Appears above the susu preview. Not tappable
  itself in this build (informational).
- **Your susus** — heading + "See all" → Susu List (§17). Body lists susu
  group previews (avatar initials, name, sub, pot amount); tap → Susu
  Detail (§17.1).
- **Wallet activity** — heading + "See all" → History filtered to wallet
  scope (§20, `histScope='wallet'`). Rows scoped to wallet-only activity
  (transfers, susu payouts, top-ups); a "**Received**" pill badges rows
  the idle-payout nudge refers to. Tap row → Transaction Detail (§23).

---

## 7. Profile tab

Entry: bottom nav "Profile." Always a fresh render (not stateful like
Home's segments).

- **Header**: avatar initials, name, school/handle.
- **Identity card**: KYC status pill (Unverified / Submitted / Verified)
  with state-specific copy:
  - Unverified — explainer + **"Verify identity"** button → KYC flow
    (§19).
  - Submitted — amber "under review" note, no action.
  - Verified — green confirmation note, no action.
- **List group**:
  - **Settings & security** → Settings (§24).
  - **Linked accounts · MTN MoMo** — display row (no destination wired).

> **Decision (2026-07-12): no admin-console entry point in the mobile app,
> at all.** The prototype's "Staff access · Super" row was a design mistake
> — mixing an internal ops surface into the customer-facing binary. Staff
> use the separate `admin-console` web app (already exists) exclusively.
> §25 below documents that surface for completeness (it exists and a staff
> account can reach it via its own login), but nothing in the mobile app
> links to it.

---

## 8. Explore tab

Entry: bottom nav "Explore." A hub of three destination cards, each
showing a live count/summary and chevron:
- **Vaults** — "{n} vaults · GHS {x} saved" → Vault List (§13), same
  destination as Savings state's "See all."
- **Wallet** — "GHS {x} · payouts & transfers" → **Wallet screen** (§14
  — full, green-card view), same destination Wallet state implicitly
  represents.
- **Challenges** — "{n} active · {m} to join" → Challenges List (§21),
  same destination as Total state's "View all."

---

## 9. Bottom navigation bar

Visible on: Home, Explore, Profile, Vault List, Susu List, Wallet
(full), Challenges List. Hidden everywhere else (all detail/flow/modal
screens).
- **Home** → Home, always reset to Total state.
- **Explore** → Explore hub.
- **Profile** → Profile tab.

---

## 10. Send flow

Unchanged: triggered only from Total state's action row. Recipient is
pre-selected in this build (no picker screen implemented yet — jumps
straight to amount entry against a fixed recipient); production should
insert `RecipientPickerScreen` ahead of §18 per the original spec.

---

## 11. Explicitly deferred / out of scope

Same list as the original spec (wallet-initiated deposit/withdraw
promotion, susu-scoped history filter completeness, Portfolio Summary tap
targets, zero-vault empty-state visual polish) — all still true of the
current build. One item is now **resolved**: the idle-susu-payout nudge
card is implemented (§6), not just proposed.

**Decisions made 2026-07-12, applying to the implementation pass:**
1. **No undo window** on Send (§18.1) — was an explored idea, not a
   commitment. Documented, not implemented.
2. **"Modern" susu is a "Coming soon" placeholder only** (§17.1) — the
   Traditional/Modern selector is visible, but Modern doesn't do anything
   real yet. Only Traditional susu is implemented.
3. **No admin-console entry point anywhere in the mobile app** (§7, §25)
   — the prototype's "Staff access" row was a design mistake and is
   removed entirely, not hidden/gated.
4. **Transaction History keeps both filter dimensions** (§20) — scope
   (vault/wallet, set by entry point) and type (deposit/withdrawal/
   transfer/susu, user-toggled chips) both apply together; neither
   replaces the other.

---

## 12. Screen inventory — everything the prototype contains

Every screen below is a full-screen push (back arrow, top-left) unless
marked otherwise. Ordered roughly by where a user first reaches them.

### 12.1 Welcome (signed-out entry)
First screen on cold, signed-out open. Contents: logo mark, "Stash"
wordmark, "Secure Target" tagline, one-line pitch, **"Create account"**
button (primary) and **"I already have one"** button (secondary) — both
go to Sign in (§12.2) in this build (no separate signup form yet), plus
Terms/Privacy fine print.

### 12.2 Sign in
Back arrow returns to Welcome. Contents: logo, "Welcome back" headline,
email field, password field (with visibility toggle + "Forgot password?"
link, both display-only), **"Sign in"** primary button and **"Sign in
with Face ID"** secondary button (both authenticate and land on Home/Total
in this build), divider, and a "New to Stash? Create an account" link
(also authenticates — no distinct signup form implemented).

### 13. Vault List — `/vaultList`
Reached from: Savings state "See all," Explore → Vaults.
Contents: header with **+ New vault** button (→ Create Vault, §13.2);
"Total saved" + vault count summary card; full list of vault cards (name,
type pill, sub-label, progress bar, balance). Tapping a card opens:

#### 13.1 Vault Detail — `/vaultDetail`
Contents: vault name in header; large circular progress ring showing
saved-so-far amount, a "Locked · N days left" pill if the vault type is
Locked; goal + % line; **Deposit** button (→ Deposit flow, §15, pre-scoped
to this vault) and **Early exit** button (present, not wired to a flow
yet); a stats row (Type / Created / Last deposit).

#### 13.2 Create Vault — `/createVault`
Contents: Vault name text input; Vault type selector (**Standard** —
withdraw anytime — vs. **Locked** — commits until date or amount, shown
with a cut-corner "locked" card treatment); if Locked is selected, reveals
"Unlock by date" and "Unlock at amount" fields plus a warning callout
("This is a commitment…"); primary button label changes contextually
(e.g. "Create vault"). Submitting returns to Vault List with the new
vault added.

### 14. Wallet screen (full) — `/walletScreen`
Reached from: Explore → Wallet, Home/Wallet state's "See all" paths.
Bottom nav visible here (it's a top-level-equivalent destination).
Contents: green gradient hero card ("Wallet balance," big figure, footer
copy "Available to send, receive susu payouts, or move into a vault");
idle-payout nudge card (same as §6, shown if applicable); **Susu groups**
section with "See all" → Susu List; **Wallet activity** section with "See
all" → History (wallet scope) — a longer preview list than Home's Wallet
state (4 rows vs 3), with the same "Received" badge treatment.

### 15. Deposit — `/deposit`
Reached from: Total-state Deposit → Account Picker → here; or Vault
Detail's Deposit button (skips the picker, vault pre-selected).
Contents: "Into · {vault name}" label; large tappable amount display
(GHS + typed digits); "New balance" preview line; custom numeric keypad
(0–9, ., ⌫); **"Confirm deposit"** button. Confirming updates the vault
balance and returns toward Home/Vault Detail with a success toast (no
dedicated deposit success screen — Success (§18.1) is used for Send
only).

### 16. Account Picker — `/accountPicker`
Reached from: Total state's Deposit or Withdraw buttons only. Title and
helper copy swap based on mode ("Deposit"/"Choose which vault to deposit
into." vs. "Withdraw"/"...withdraw from."). Contents: list of vault rows
(name, type pill, balance, chevron). Tapping a row proceeds into Deposit
(§15) or Withdraw (§16.1) pre-scoped to that vault. Wallet is never an
option here. Zero vaults skips this screen entirely and opens Create
Vault instead.

#### 16.1 Withdraw — `/withdraw`
Mirrors Deposit's layout: "From · {vault name}"; amount display; "New
balance" reframed as "Remaining"; numeric keypad; **"Confirm withdrawal"**
button.

### 17. Susu List — `/susuList`
Reached from: Wallet state's "See all," Wallet screen's "See all,"
Explore is not a direct path here (Explore's Wallet card goes to the
Wallet screen, not directly to Susu List).
Contents: header with **+ New** button → Join Susu (§17.2); pending
**invite cards** (if any) with Accept/Decline actions inline; full list
of susu group cards — avatar, name, a **Traditional** type pill, sub-label,
a progress bar + round label, and a right-aligned pot figure. Tapping a
card opens:

#### 17.1 Susu Detail — `/susuDetail`
- **Traditional** (rotating-payout) — "Round N of M · collecting" banner
  card showing the current pot amount and who it goes to this round; a
  stats row (Per round / Frequency / Members); a **Rotation order** list
  (position, avatar, name, status pill e.g. "Paid," "Up next"); an
  explainer callout about interest-free rotating credit. Bottom action
  button: **"Contribution paid"** (disabled/confirmed state, green) if
  already paid this round, or a **"Pay this week's / Pay my GHS X.00"**
  primary button if unpaid.

> **Decision (2026-07-12): "Modern" susu is not being built yet — it was
> an idea being explored, not a committed feature.** The group-type
> selector (Traditional / Modern) still appears at group-creation time so
> the concept is visible, but selecting **Modern** shows an empty
> **"Coming soon"** placeholder screen instead of a working fixed-term
> group — no circular progress ring, no payment-day strip, no `Modern`
> group actually gets created. Only **Traditional** (rotating-payout,
> already fully implemented end-to-end today) is real. Do not build the
> `SusuGroupEntity` group-type field, the fixed-term payout logic, or the
> Modern-specific Susu Detail layout described in earlier prototype notes
> until this becomes a real product decision.

#### 17.2 Join Susu — `/joinSusu`
Contents: "Enter the join code" headline + helper copy; an 8-character
code-entry box row; below it, a preview card of the group the code
resolves to (name, "Pending" pill, organiser, member count, per-round
amount, frequency); **"Request to join"** button + note that the
organiser approves requests before the user is in.

### 18. Send Money — `/send`
Reached only from Total state's Send button. Contents: recipient card
(avatar initials, name, "Verified" pill, handle); "You're sending" amount
display; numeric keypad; fee line ("Fee GHS 0.00 · N of 5 free sends left
this month"); primary **Send** button (label reflects amount).

#### 18.1 Success — `/success`
Reached only after confirming a Send. Contents: animated checkmark badge;
"Sent." headline; amount sent; "is on its way to {name}"; reference code;
**"Done"** button returns to Home.

> **Decision (2026-07-12): no undo window — this was an idea being
> explored, not a commitment.** Documented here for the record since it
> was part of the original design conversation, but it is explicitly
> **not scheduled for implementation**. Do not build a countdown UI, a
> reversal endpoint, or any "cancel this transfer" mechanism as part of
> this redesign. If picked up later, note the two very different possible
> designs it could mean (a client-side delay before the API call actually
> fires, vs. a real reversing ledger transaction after the transfer has
> already settled) — that ambiguity was never resolved and would need its
> own scoping pass.

### 19. KYC — Verify identity — `/kyc`
Reached from Profile's "Verify identity" button (only when Unverified).
Contents: progress header "GHANA CARD · N OF 3" with a progress bar;
explainer callout on why verification is required; three upload rows —
**Front of card**, **Back of card**, **Selfie holding card** — each
tappable to simulate a capture, showing a status tag (Add / Added /
etc.); **"Submit for review"** button (disabled until all 3 are captured)
with a "usually decided in under 2 hours" note. Submitting moves KYC
status to Submitted, reflected back on Profile.

### 20. History (Transaction list) — `/history`
Reached from: Savings state's vault-activity "See all," Wallet
state/screen's wallet-activity "See all," Explore (generic, no scope).
Title adapts to scope ("Vault activity" / "Wallet activity" / generic
"History").

> **Decision (2026-07-12): both filter dimensions are kept, not one
> replacing the other.** `histScope` (`vault` / `wallet` / unset) is set by
> the entry point and controls *which account's activity appears at all* —
> it's not a chip the user toggles inside the screen. The existing
> type-based filter chips (**All / Deposit / Withdrawal / Transfer /
> Susu**, per the original spec's `TransactionHistoryScreen`) still work
> *within* whatever scope was entered with. E.g. entering from Savings
> state's "See all" opens History pre-scoped to vault activity, with the
> existing type chips available to further narrow it to just deposits,
> just withdrawals, etc. Entering from Explore (no scope) shows both
> vaults' and wallet's activity together with all type chips available,
> matching today's unscoped behavior exactly.

Contents: day-grouped sections, each a header ("TODAY," "YESTERDAY · 6
JUL," etc.) followed by a card list of transaction rows (icon, title,
sub-label, signed amount). Tapping a row opens Transaction Detail (§23).

### 21. Challenges List — `/challengesList`
Reached from: Total state's "View all," Explore → Challenges. Contents:
one-line pitch copy; full list of challenge cards — icon, name, sub,
% complete (only for joined ones) or a **"Join"** pill (for
not-yet-joined ones), progress bar (only if joined). Tapping any card
opens Challenge Detail.

#### 22. Challenge Detail — `/challengeDetail`
Contents: large icon badge; tag line; if already active, a progress card
(label + %  + bar); a description/blurb card; a two-tile stats row
(Reward / Community — "{n} joined"); bottom **CTA button** whose label
changes by state (e.g. "Join challenge" vs. an already-joined
confirmation state).

### 23. Transaction Detail — `/txnDetail`
Reached from any transaction row across Home, History, Wallet screen.
Contents: icon badge, large signed amount (color-coded in/out), title,
status pill (Completed / Failed / etc.); a detail card with Type, When,
Details, Reference rows.

### 24. Settings — `/settings`
Reached from Profile's "Settings & security" row. Contents, grouped under
mono-caps section labels:
- **Appearance** — Theme row with an inline Dark/Light segmented toggle;
  Light navigates to the sibling `Stash Prototype Light.dc.html` file
  (a full light-theme reskin of the same app).
- **Account** — Personal details, Security & PIN, Notifications rows
  (display rows, not wired to sub-screens yet).
- **Support** — Help center row; **Log out** row (red, signs out →
  Welcome).

### 25. Admin console (internal, desktop-only view)
**Not reachable from the mobile app in any way** (see §7's decision note)
— this is the standalone `admin-console` web app, with its own login,
entirely separate from the mobile screens. The prototype file happens to
bundle both a mobile `view` and this desktop `view` in one HTML document,
which is why it's documented here, but that's a prototyping-tool artifact,
not a statement about how staff actually reach it in the real product.

Sidebar: Stash mark + "Admin console" label; nav items **Dashboard**,
Users (static), **KYC Queue** (badge = pending count), Disputes (static,
badge = 6), **Transactions**, Audit Log (static); footer shows the
signed-in staffer + a **"← Back to app"** link that returns to the mobile
view.

- **25.1 Dashboard** — search bar; 4 stat tiles (Active users, Deposits
  today, KYC pending, Open disputes); a 30-day deposit-volume bar chart;
  a Recent Alerts feed (severity-tagged rows); a KYC-queue summary card
  with an "Open queue →" shortcut into §25.2.
- **25.2 KYC Queue** — filter chips (All / Auto-flagged / Oldest first);
  list of applicant rows (avatar, name, flag pill, email, submitted-time,
  reason) each with a **Review** button that opens the **KYC Review
  modal**: side-by-side Front/Back/Selfie image placeholders, Name-match
  and Selfie-confidence result tiles, and **Reject** / **Later** /
  **Approve KYC** actions.
- **25.3 Transactions** — scoped to one user (shown: Akua Mensah ·
  U-30412); a table of transactions (Description, When, Status, Amount).

---

## 26. Summary table — every button and where it goes

| Location | Button/element | Destination / behavior |
|---|---|---|
| Welcome | Create account / I already have one | Sign in |
| Sign in | Sign in / Sign in with Face ID / Create an account | Home (Total), authenticated |
| Home, top | Total / Savings / Wallet picker | Switch Home state in place |
| Home, Total | Deposit | Account picker (vault-only) → Deposit |
| Home, Total | Send | Send Money flow |
| Home, Total | Withdraw | Account picker (vault-only) → Withdraw |
| Home, Total | Challenges "View all" / challenge card | Challenges List / Challenge Detail |
| Home, Savings | Vault card / "See all" | Vault Detail / Vault List |
| Home, Savings | Vault activity row / "See all" | Transaction Detail / History (vault scope) |
| Home, Wallet | Susu card / "See all" | Susu Detail / Susu List |
| Home, Wallet | Wallet activity row / "See all" | Transaction Detail / History (wallet scope) |
| Bottom nav | Home / Explore / Profile | Home (reset Total) / Explore hub / Profile |
| Explore | Vaults / Wallet / Challenges | Vault List / Wallet screen / Challenges List |
| Profile | Verify identity | KYC flow |
| Profile | Settings & security | Settings |
| Vault List | + New vault / vault card | Create Vault / Vault Detail |
| Vault Detail | Deposit | Deposit (pre-scoped) |
| Create Vault | Create vault | Back to Vault List |
| Wallet screen | Susu "See all" / susu card | Susu List / Susu Detail |
| Wallet screen | Wallet activity "See all" / row | History (wallet) / Transaction Detail |
| Susu List | + New / invite Accept-Decline / susu card | Join Susu / accept-decline in place / Susu Detail |
| Susu Detail | Pay this round / Pay my GHS X | Marks contribution paid |
| Join Susu | Request to join | Submits join request |
| Send | Send button | Success |
| Success | Done | Home |
| KYC | Capture rows / Submit for review | Marks doc captured / sets status Submitted |
| History | Type filter chips / transaction row | Filters within scope / Transaction Detail |
| Challenges List | Challenge card | Challenge Detail |
| Challenge Detail | CTA button | Joins challenge |
| Settings | Theme toggle | Switches Dark/Light prototype file |
| Settings | Log out | Welcome, signed out |

Admin console rows omitted — not reachable from the mobile app (see §7,
§25).

---

## 27. Notes on gaps vs. the original spec / open questions

- **Recipient picker** for Send is not implemented as a separate step —
  the prototype jumps straight to amount entry against one fixed
  recipient. Production should insert the search step ahead of §18.
- **No signup-specific form** — both "Create account" and "I already have
  one" funnel into the same Sign in screen in this build.
- **Early exit** (Vault Detail) and several Settings rows (Personal
  details, Security & PIN, Notifications, Linked accounts, Help center)
  are present as UI but not wired to their own screens yet.
- **Deposit has no dedicated success screen** (unlike Send → Success) —
  confirming just returns with a toast. Flag if a consistent
  confirmation pattern across Deposit/Withdraw/Send is wanted.
- **Wallet's own top-up entry** (the "legacy" path called out in the
  original spec) doesn't exist as separate UI in this prototype — the
  Wallet screen only shows balance + susu + activity, no deposit button
  of its own, consistent with "wallet deposits deferred."
