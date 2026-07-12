# Susu: Two Group Types — Traditional Susu & Modern Susu

Status: **concept agreed, not implemented.** This document is a concept
spec, not a build plan — it exists so both models are pinned down in
writing, with what's confirmed and what's still open clearly separated,
before any implementation work starts.

## Why two types, not a redesign

The original conversation started as "the payout model is wrong, replace
it." It landed somewhere better: the existing rotating-payout model and the
proposed fixed-term model solve genuinely different problems for
genuinely different users, and neither one makes the other obsolete.

- **Traditional Susu** (the currently-implemented model) gives one member
  per round early access to a lump sum larger than they've personally
  saved — real interest-free credit. That's the actual definition of a
  ROSCA (rotating savings and credit association) and the reason susu is a
  culturally established financial practice, not just a savings gimmick.
  Its cost is real counterparty trust: the current system has **no
  financial enforcement** against someone taking their lump sum early and
  then defaulting on the rounds after (see §3).
- **Modern Susu** (the new proposal) removes that trust exposure entirely
  by removing the lump sum — nobody profits off anybody else, everyone
  simply gets back what they contributed, on a schedule, at a fixed term
  end. In exchange, it gives up the thing that makes traditional susu
  valuable to people who specifically want early access to a large sum.

Both are legitimate products. The creator picks which one they're setting
up at group-creation time, as a new top-level choice alongside the
existing name/contribution-amount fields.

---

## 1. Traditional Susu (current, live system — documented here for contrast)

This is what's already implemented and running today. Included in full so
this doc is a complete two-model reference, not just half of one.

### 1.1 Creation & setup
- Creator sets: group name, contribution amount, frequency
  (**WEEKLY, BIWEEKLY, or MONTHLY** — no DAILY option), and target member
  count (4–20).
- A join code is generated; members join until the group hits the exact
  target count. The group sits `PENDING` until then.
- Creator explicitly activates once full — reaching the target count does
  not auto-activate.

### 1.2 What activation locks in
- **Rotation order**: organiser is always position 1. Every other member
  is ordered strictly by `joined_at` (earliest joiner after the organiser
  = position 2, and so on). Not creator-configurable, not random — pure
  join-order.
- **N rounds are created up front**, N = member count. Round *K*'s due
  date is `activation date + K weeks/months` (same weekday/day-of-month as
  activation). Round *K*'s recipient is whoever is in rotation position K.
- Only round 1 opens for payment initially; later rounds don't accept
  payment until they become the current round.

### 1.3 Each round
- **Every member pays every round**, including that round's own recipient
  — the recipient isn't exempt from contributing to their own payout
  round.
- A round can only be paid into while it's the single currently-open
  round — no pre-paying future rounds, no paying closed past rounds.
- The round disburses **as soon as the last outstanding contribution
  clears** — this can happen before the scheduled due date if everyone
  pays promptly. It is not strictly locked to the calendar date.
- Missing a contribution: 24-hour grace period, then a flat GHS 5 penalty
  (split GHS 2.50 to top up the pot, GHS 2.50 to the platform). The round
  still disburses on schedule regardless of late payers — it simply pays
  out whatever was actually collected, so a defaulting member's shortfall
  is absorbed by shrinking every subsequent pot, not by delaying anyone.

### 1.4 Duration, worked examples (contribution = GHS 100/round)

| Members | Weekly | Monthly |
|---|---|---|
| 5 | 5 rounds, **5 weeks total**, GHS 500/round pot | 5 rounds, **5 months total**, GHS 500/round pot |
| 10 | 10 rounds, **~2.3 months total**, GHS 1,000/round pot | 10 rounds, **10 months total**, GHS 1,000/round pot |
| 20 | 20 rounds, **~4.6 months total**, GHS 2,000/round pot | 20 rounds, **20 months (1yr 8mo) total**, GHS 2,000/round pot |

The 20-person monthly case is a real practical weakness worth keeping in
mind: it's a 20-month lock-in.

### 1.5 Leaving / default risk — the actual weak point
- Voluntary leaving is **only possible while the group is `PENDING`**.
  Once `ACTIVE`, a member cannot leave through the app at all —
  `SUSU_CANNOT_LEAVE_ACTIVE_GROUP`, admin-mediated removal only.
- This blocks the *leave button*, but does nothing to stop someone from
  simply going silent after receiving their round's payout. There is
  **no deposit, collateral, or credit check** — the only consequences are
  the GHS 5 late penalty (which itself gets waived, and the group flagged
  for admin review, if the defaulter's wallet can't even cover it) and the
  shrinking pot described in §1.3. The system relies entirely on susu
  circles being pre-existing trust networks (family, church, market
  associations) — it provides no technical enforcement of its own.
- **This is the specific gap Modern Susu closes** — see §2.

---

## 2. Modern Susu (new — concept, not yet implemented)

### 2.1 Core idea
No per-round lump sum. Everyone contributes on a recurring schedule for a
**fixed term** the creator chooses; at the end of the term, **each member
receives back what they personally contributed** — not a shared pot paid
to one recipient. This was confirmed through discussion (the case for it
was specifically that removing the lump sum removes the run-off/default
risk described in §1.5 — there is no pot to take early, so there's nothing
to run off with).

### 2.2 Creator-set conditions
- Group name, contribution amount, target member count — same as
  Traditional.
- **Frequency: DAILY, WEEKLY, or MONTHLY.** DAILY is new to this model
  (Traditional's BIWEEKLY option isn't part of Modern Susu — the two
  models simply have their own independent frequency sets).
- **Term length: creator chooses 90, 180, or 360 days.** This replaces
  "N members = N rounds" as the thing that determines how long the group
  runs — Modern Susu's duration is fixed up front regardless of member
  count.

### 2.3 Contribution scheduling, per frequency

**DAILY** — no slot/position assignment at all. Every member owes a
contribution every calendar day for the full term, and can pay any time
within that day. Join order plays no role whatsoever in DAILY mode — it's
the one frequency with no rotation concept left in it.

**WEEKLY** — members are distributed across the 7 weekdays, in join order
(organiser first, same ordering rule as Traditional), as evenly as
possible. Worked rule: `base = N ÷ 7`, `extra = N mod 7`; the first `extra`
weekdays get `base + 1` members, the rest get `base` members. Example: 7
members → exactly 1 per weekday. 10 members → 3 weekdays get 2 members, 4
weekdays get 1. Each member then owes their contribution on their assigned
weekday, every week, for the whole term.

**MONTHLY** — same even-distribution logic as WEEKLY, applied to
weeks-of-the-month instead of days-of-the-week. **Not fully specified
yet** — see open questions (§4).

### 2.4 Paying — no turn-gating
Unlike Traditional Susu's single-open-round gate, a member can pay their
own obligation whenever they choose — there's no shared resource being
contested, so there's nothing to gate. Paying simply marks that
occurrence as paid against that member's own record. Whether a member can
pay *ahead* of their next due occurrence, or only the currently-due one,
is still open (§4).

### 2.5 Term end
When the chosen term (90/180/360 days) elapses, each member's accumulated
contributions are released back to them. No single recipient, no shared
pot distribution — this is the piece that eliminates Traditional Susu's
§1.5 default-and-run risk entirely, since there's no early lump sum for
anyone to take and disappear with.

### 2.6 Penalties
Same shape as Traditional (§1.3): grace period, then a flat penalty split
between pot top-up and platform revenue, assumed unchanged unless stated
otherwise. Since Modern Susu has no per-round recipient, the "pot top-up"
half of a penalty presumably now goes toward the general term pool rather
than a specific round — not yet fully specified (§4).

---

## 3. Side-by-side comparison

| | Traditional Susu | Modern Susu |
|---|---|---|
| Payout | One recipient per round gets the full pot | Everyone gets back their own contributions at term end |
| Duration driver | N members = N rounds | Creator-chosen fixed term (90/180/360 days) |
| Frequency options | WEEKLY, BIWEEKLY, MONTHLY | DAILY, WEEKLY, MONTHLY |
| Position determines | Which round you receive the pot | Which recurring day/week is "yours" (WEEKLY/MONTHLY only — DAILY has no positions) |
| Payment gating | Only the current open round accepts payment | Pay your own obligation any time, no shared gate |
| Early access to a lump sum | Yes — the entire point of the model | No — by design |
| Counterparty/default risk | Real — no deposit/collateral, only a leave-button block and a shrink-the-pot fallback | None — nobody receives more than their own contributions |
| Can't-leave-once-active rule | Yes, applies | Presumably doesn't need to apply the same way, since there's no lump sum to protect against — not yet explicitly decided (§4) |

---

## 4. Open questions before implementation

1. **MONTHLY's week-distribution for Modern Susu** — automatic even
   distribution across ~4 week-buckets (mirroring WEEKLY's 7-bucket
   logic), or creator/manual assignment of specific weeks? Still
   unresolved.
2. **Can a Modern Susu member pay ahead** of their own next due occurrence,
   or strictly one at a time?
3. **Penalty mechanics for Modern Susu** — confirmed to exist in the same
   shape, but the "top up the pot" half of the split needs a concrete
   target now that there's no per-round pot (§2.6).
4. **Does Modern Susu need Traditional's "can't leave once active" rule at
   all?** Since there's no lump sum to protect, the case for locking
   members in is weaker — worth an explicit decision rather than
   inheriting the rule by default.
5. **Naming/UI**: how "Traditional Susu" vs "Modern Susu" is presented as
   a choice at group creation isn't designed yet — out of scope for this
   doc (belongs with the visual/UX pass), but flagged so it isn't
   forgotten.
