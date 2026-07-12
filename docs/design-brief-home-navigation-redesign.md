# Home & Navigation Redesign — UI Flow Spec

Status: **design agreed, not yet implemented.** This document is the
authoritative description of the new home screen, top-level navigation, and
every screen it leads to. It came out of a design conversation prompted by a
real gap: the wallet (a real, working backend feature — direct MoMo deposit,
susu contribution/payout routing, peer-transfer settlement) had no
discoverable entry point anywhere in the app except a push-notification deep
link. This redesign's core goal is **discoverability without clutter** —
every account (vaults, wallet) and every category of activity (savings,
susu, challenges) needs a path a user can find by looking, not by already
knowing it exists.

This is a flow/behavior spec, not a visual design spec — it does not
prescribe colors, spacing, or iconography (see
`docs/design-brief-ui-ux.md` for that pass). It prescribes: what exists on
screen, what state each element can be in, and exactly what happens when a
user taps each thing.

---

## 1. Global structure

Two structural layers persist across the app (except where noted):

1. **Bottom navigation bar** — always visible on the three top-level
   destinations (Home, Explore, Profile) and their direct children. Not
   shown on modal/flow screens (Deposit, Withdraw, Send, Create Vault,
   etc.) — those are full-screen flows with their own back/close
   affordance, consistent with how `DepositScreen`, `WithdrawScreen`, etc.
   already behave today.
2. **No top header settings bar.** This is a deliberate removal — settings
   access moves entirely to the Profile tab (see §5.3). The top of the
   screen is reserved for balance and account-switching, not utility
   icons.

---

## 2. Home screen — structure overview

The Home screen has **three mutually-exclusive states**: **Total**
(default), **Savings**, and **Wallet**. All three share the same overall
layout skeleton:

```
┌─────────────────────────────────────┐
│  [ Total ]   Savings    Wallet       │  ← segmented switcher
│                                       │
│         GHS  X,XXX.XX                │  ← hero balance (changes per state)
│                                       │
│   [Deposit]   [Send]   [Withdraw]    │  ← only in Total state
│                                       │
│   ─────────── state body ───────     │  ← changes completely per state
│                                       │
└─────────────────────────────────────┘
        [ Home ]  [ Explore ]  [ Profile ]   ← bottom nav, always visible
```

The **segmented switcher** and **hero balance** are always present at the
top of Home regardless of state. Everything below the hero balance
("state body") is entirely state-dependent and is described state-by-state
below.

---

## 3. The segmented switcher (Total / Savings / Wallet)

- Three equal-weight segments, styled as a standard segmented control —
  **not** hidden or de-emphasized text links. All three are always fully
  visible and legible. The **active** segment is visually highlighted
  (filled/bold/accent color); the two inactive segments are styled
  distinctly but not hidden — a user should be able to tell at a glance
  that all three are tappable, even without having tapped one before.
- Tapping a segment:
  - Switches the segment's own active/inactive styling immediately.
  - Replaces the hero balance figure with that state's balance (Total =
    sum of all vaults + wallet; Savings = sum of vault balances only;
    Wallet = wallet balance only).
  - Replaces the entire state body below with that state's content (§4–§6).
  - Does **not** navigate to a new screen — this is an in-place state
    change on the Home screen, not a route change. Back-button/gesture
    behavior on Home should exit the app (or go to whatever Home's normal
    back behavior is today), not step back through segment history.
- Default state on cold app open / tapping the **Home** bottom-nav tab is
  always **Total**, regardless of which segment was last active.

---

## 4. Home screen — Total state (default)

This is what the user sees on app open and whenever they tap the Home
bottom-nav tab.

### 4.1 Hero balance
Sum of every vault's balance plus the wallet balance. Read-only display,
not tappable itself (the segmented switcher above it is the navigation
control).

### 4.2 Action row: Deposit / Send / Withdraw
Three buttons, always visible in Total state only (not shown in Savings or
Wallet state — see §5.1 and §6.1 for why).

- **Deposit** — opens an **account picker** (see §7.1). **Vault-only for
  now**: the picker shows the user's vaults and nothing else. Selecting a
  vault opens the existing vault deposit flow (`DepositScreen` with a
  `vaultId` param) exactly as it works today. Wallet is not offered as a
  deposit target from this button at this time.
- **Send** — opens the existing peer-transfer flow directly (recipient
  search → amount → confirm), unchanged from today's behavior. No account
  picker needed: Send is unambiguously wallet-to-wallet already (confirmed
  against `PeerTransferService` — it resolves both parties' `USER_WALLET`
  accounts), so there's nothing to disambiguate.
- **Withdraw** — opens an **account picker** (see §7.1), identical
  vault-only behavior to Deposit. Selecting a vault opens the existing
  vault withdrawal flow (`WithdrawScreen` with a `vaultId` param).

### 4.3 Portfolio Summary
A compact section below the action row. Shows counts, not balances (the
balance is already shown in the hero above):
- Number of vaults the user has (e.g., "3 vaults").
- Number of susu groups the user belongs to (e.g., "2 susu groups").

Not independently tappable as a whole section — it's a glanceable summary,
not a navigation control. (If a future pass wants these tappable
shortcuts into Savings/Wallet state, that's a reasonable addition but is
out of scope for this spec — flag separately if wanted.)

### 4.4 Challenges section
Below Portfolio Summary. Shows a preview of the user's active/available
challenges (existing `ChallengesList`-style content, abbreviated).

- **Section title** with a **"View all"** link/button next to it.
- Tapping **"View all"** navigates to a dedicated, full-screen **Challenges
  list** (all challenges, not abbreviated) — this reuses/extends the
  existing `ChallengesList` screen already reachable today via
  `navigation.navigate('ChallengesList')`.
- Tapping an individual challenge preview card navigates into that
  challenge's detail screen, same as today's behavior.

---

## 5. Home screen — Savings state

Active when the user taps the **Savings** segment.

### 5.1 What's different from Total state
- Hero balance shows **Savings total** (sum of vault balances) instead of
  the combined Total.
- The **Deposit/Send/Withdraw action row is hidden** in this state. Those
  actions live in Total state only per §4.2 — Savings state is a browsing
  view, not an action-initiation view, to keep this state visually simple
  and avoid duplicating entry points to the same flows.

### 5.2 State body: vault preview
- Shows the user's **first 3 vaults** (by whatever ordering the existing
  `VaultListScreen`/Home vault preview uses today — likely most-recently-
  active or creation order; keep consistent with current behavior).
- Each vault preview row/card is tappable and opens that vault's
  **`VaultDetailScreen`** directly (existing screen, existing behavior).
- A **"See all"** link/button below or alongside the preview navigates to
  the full **`VaultListScreen`** (existing screen — all vaults, not just
  3).

### 5.3 State body: recent transactions
Below the vault preview (still within Savings state):
- Shows recent transactions **scoped to vault activity only** — deposits,
  withdrawals, early exits, susu-round vault-adjacent activity if
  applicable. Does **not** include wallet-only activity (peer transfers,
  susu contributions/payouts that haven't touched a vault) — that belongs
  to Wallet state's feed instead (§6.3).
- This is a preview list (a handful of most-recent items), not the full
  history. Tapping an individual transaction row opens that transaction's
  detail (reuse whatever detail view `TransactionHistoryScreen`/statement
  already provides today). Whether this preview needs its own "See all"
  into a vault-scoped full transaction history, or whether the existing
  full `TransactionHistory` screen already supports filtering to
  vault-only, needs a follow-up decision at implementation time — not
  fully specified by the design conversation this doc is based on.

---

## 6. Home screen — Wallet state

Active when the user taps the **Wallet** segment. **Visit-only for now** —
per explicit direction, this state is for browsing/viewing only; no new
deposit/withdraw actions are being wired to it in this pass. The existing
`WalletScreen`'s own Deposit button (wallet-mode `DepositScreen`, no
`vaultId`) continues to exist in the codebase and is not being removed, but
it is not part of what this redesign actively surfaces or promotes right
now — wallet-initiated deposits/withdrawals are deferred to a later pass.

### 6.1 What's different from Total state
- Hero balance shows **Wallet balance only** instead of the combined
  Total.
- The **Deposit/Send/Withdraw action row is hidden** in this state, same
  reasoning as Savings state (§5.1) — actions live in Total state only.

### 6.2 State body: susu preview
- Mirrors the vault preview pattern from Savings state (§5.2), but for
  susu: shows the user's susu group memberships (first 3, or however many
  fit the same preview pattern as vaults).
- Each susu group preview row/card is tappable and opens that group's
  detail screen (existing `SusuDetailScreen`).
- A **"See all"** link/button navigates to the full susu groups list
  (existing susu list screen, if one exists today, or the equivalent full
  view).

### 6.3 State body: recent transactions
Below the susu preview:
- Shows recent transactions **scoped to wallet activity** — peer transfers
  sent/received, susu contributions paid out of the wallet, susu round
  payouts landing in the wallet, and any direct wallet top-ups (even
  though wallet top-up isn't promoted by this redesign's UI yet, existing
  top-ups via the legacy `WalletScreen` entry point still show up here
  since they're real wallet transactions).
- **Important, tied to the reason this redesign exists**: susu payouts
  that have landed in the wallet and haven't been moved anywhere should be
  visually distinguishable in this feed — not just another row, since the
  original problem was money silently sitting in the wallet unnoticed. At
  minimum this means a clear "received" treatment; a stronger version
  (e.g., a highlighted nudge card above the feed: "GHS X from your Susu
  group is in your wallet") was discussed favorably in the design
  conversation but is not mandatory for a first pass — flag as a
  nice-to-have if not implemented immediately.

---

## 7. Account picker (Deposit / Withdraw from Total state)

Triggered by tapping **Deposit** or **Withdraw** in Total state (§4.2)
only — Savings and Wallet state don't show these buttons at all.

### 7.1 Behavior
- Opens a **vault selection list** — every vault the user has, each row
  showing at minimum the vault name and current balance.
- **Wallet is not an option in this picker** — confirmed explicitly: the
  picker is vault-only for now. There is no "Wallet" row/entry in this
  list.
- Selecting a vault immediately proceeds into the existing vault
  deposit/withdrawal flow for that vault (`DepositScreen`/`WithdrawScreen`
  with the chosen `vaultId`), identical to how tapping Deposit/Withdraw
  from inside `VaultDetailScreen` already works today.
- If the user has zero vaults, this picker should route into vault
  creation instead (`CreateVaultScreen`) rather than showing an empty
  list — exact copy/empty-state treatment not specified here, flag for
  the visual design pass.

### 7.2 Not in scope for this picker
- No wallet option (explicitly deferred).
- No "create a new vault from here" shortcut beyond the zero-vaults empty
  state above — if the user has existing vaults, this picker is
  selection-only, not a combined selection+creation surface. (Open to
  revisiting if it turns out to be a common need, but not part of this
  spec.)

---

## 8. Send flow

Unchanged from today's behavior, triggered only from Total state's action
row (§4.2). No account picker — always wallet-to-wallet, recipient chosen
via the existing recipient search flow
(`RecipientPickerScreen` → `SendMoneyScreen`).

---

## 9. Bottom navigation bar

Three destinations, always visible except inside modal/flow screens (see
§1). Persistent across Home and its two sibling top-level tabs.

### 9.1 Home
Navigates to the Home screen, **always resetting to Total state**
regardless of what state was last active before leaving Home. (I.e.,
switching to Explore and back to Home does not preserve a prior
Savings/Wallet selection — Home always opens on Total.)

### 9.2 Explore
A **hub/chooser screen**, distinct from Home's segmented switcher — this is
a second, independent path to the same three content areas (vaults,
wallet, challenges), for a user who wants to jump directly into one of them
without going through Home first. Presents three clear options:
- **Vaults** → navigates to the full `VaultListScreen` (same destination
  as Savings state's "See all," §5.2).
- **Wallet** → navigates to the full `WalletScreen` (same destination as
  Wallet state's implicit "full view" — i.e., Explore's Wallet option and
  Home's Wallet segment should feel like two doors into the same room, not
  two different rooms).
- **Challenges** → navigates to the full Challenges list (same destination
  as §4.4's "View all").

This is intentionally redundant with Home's segmented switcher and
Home's "See all"/"View all" links — multiple paths to the same
destinations is acceptable and common (this was discussed explicitly and
accepted), but implementers should ensure Explore's three options and
Home's equivalent paths land on the literal same screens, not near-
duplicate ones, to avoid the app feeling inconsistent.

### 9.3 Profile
Replaces the removed top-header settings bar entirely. Navigates to the
user's profile/settings screen — existing account settings, security
(App Lock, change password), legal, logout, etc. (reuse whatever the
current Settings screen tree already provides; this redesign only changes
*how it's reached*, not what's in it).

---

## 10. Explicitly deferred / out of scope for this pass

Called out here so nothing in this spec is mistaken for a decision to
build these now:

- **Wallet-initiated deposits/withdrawals** — the existing wallet-mode
  `DepositScreen` (no `vaultId`) and its entry point on the legacy
  `WalletScreen` are not being removed, but this redesign does not add any
  *new* promoted path to them. The Total-state account picker is
  vault-only. Wallet's own top-up affordance stays as-is until a later
  pass.
- **Susu-scoped "See all" transaction history filtering** — whether the
  existing transaction history screen already supports filtering to
  wallet-only or vault-only, or needs new filter support, is unresolved
  (§5.3, §6.3).
- **Portfolio Summary tap targets** — counts are currently display-only,
  not shortcuts into Savings/Wallet state. Could be added later.
- **The "idle susu payout" nudge card** in Wallet state (§6.3) — discussed
  favorably, not mandatory for v1 of this redesign.
- **Zero-vault empty state copy/treatment** in the account picker (§7.1) —
  behavior (route to vault creation) is specified, exact presentation is
  not.

---

## 11. Summary table — every button and where it goes

| Location | Button/element | Destination / behavior |
|---|---|---|
| Home, top | Total segment | Switch Home to Total state |
| Home, top | Savings segment | Switch Home to Savings state |
| Home, top | Wallet segment | Switch Home to Wallet state |
| Home, Total state | Deposit | Vault-only account picker → `DepositScreen(vaultId)` |
| Home, Total state | Send | `RecipientPickerScreen` → `SendMoneyScreen` |
| Home, Total state | Withdraw | Vault-only account picker → `WithdrawScreen(vaultId)` |
| Home, Total state | Challenges "View all" | Full Challenges list screen |
| Home, Total state | Individual challenge card | Challenge detail screen |
| Home, Savings state | Vault preview card | `VaultDetailScreen(vaultId)` |
| Home, Savings state | "See all" (vaults) | `VaultListScreen` (full) |
| Home, Savings state | Recent transaction row | Transaction detail |
| Home, Wallet state | Susu group preview card | `SusuDetailScreen(groupId)` |
| Home, Wallet state | "See all" (susu) | Full susu groups list |
| Home, Wallet state | Recent transaction row | Transaction detail |
| Bottom nav | Home | Home screen, reset to Total state |
| Bottom nav | Explore | Explore hub screen |
| Explore | Vaults | `VaultListScreen` (full) |
| Explore | Wallet | `WalletScreen` (full) |
| Explore | Challenges | Full Challenges list screen |
| Bottom nav | Profile | Profile/settings screen |
