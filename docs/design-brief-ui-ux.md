# Stash — UI/UX Design Brief

This is a scoping document, not a design spec. It names every surface that needs
design attention and the specific questions/tensions each one raises. It
deliberately does not prescribe colors, exact spacing, type scales, or icon
shapes — that definition work belongs to the design pass this brief is
handed to. For every page, feature, and experience flow listed below,
produce a detailed design prompt: intended mood, layout approach, states to
cover, motion/interaction notes, and any copy tone guidance. Don't skip any —
treat this as a checklist to work through exhaustively across both surfaces.

## What Stash actually is

A financial app: personal wallet, time-locked savings vaults, group savings
(susu) circles, peer transfers, and KYC-gated onboarding, plus an internal
admin console for KYC review, dispute resolution, and staff/user
administration. Money is genuinely at stake for users on every screen in the
mobile app — trust and clarity are not optional polish, they're the product.

The tone to hit: **smooth and slick, but not sterile or corporate-strict.**
Avoid the two failure modes fintech design usually falls into — (a) cold,
over-engineered "enterprise dashboard" seriousness that feels like a bank
teller's window, and (b) generic consumer-app cheerfulness that undersells
how much trust a money app needs to earn. Somewhere in between: confident,
warm, a little bit alive, but never flippant about numbers.

**Iconography and emoji constraint (hard requirement):** no default emoji
glyphs (the standard Unicode/Apple/Google emoji set) and no default or
commonly-recognized icon libraries (Material Icons, Font Awesome, Feather,
Heroicons, Lucide, Ionicons, etc. — anything a user has seen in a hundred
other apps). Every icon and any emoji-like accent mark needs to be a custom
mark designed specifically for this product. This is a differentiation
requirement, not a nice-to-have — flag anywhere the current app leans on a
stock icon set so it's on the list to replace.

## Foundations to establish first (both surfaces depend on these)

Neither app currently has a real design token system — mobile has an
undeveloped `src/theme` directory and hardcoded per-screen `StyleSheet`
values; the admin console uses bare shadcn/ui primitives (`button`, `card`,
`input`, `label`) with no product-specific skin on top. Establish, as
reusable foundations before or alongside page-level work:

- **Color system**: semantic roles (surface layers, text hierarchy, brand
  accent, money-positive/negative/neutral signaling, status colors for
  KYC/dispute/transaction states) — not just a palette, but rules for when
  each is used. Needs to work across both a consumer mobile context and a
  denser admin-console context without feeling like two unrelated products.
- **Typography scale**: a hierarchy that reads as trustworthy for large
  monetary figures specifically (balance displays, transaction amounts) as
  well as normal UI text — numeral treatment deserves its own attention,
  distinct from prose text.
- **Spacing/layout grid**: consistent rhythm for both a single-column mobile
  flow and a data-dense admin table/queue context.
- **Custom iconography system**: a coherent visual language for the custom
  icon set described above — a consistent stroke weight/fill approach/corner
  language so 40+ distinct icons (nav, actions, status, empty states) feel
  like one family, not one-offs.
- **Motion language**: what kind of easing/duration/transition style signals
  "smooth and slick" here — screen transitions, button press feedback, success
  confirmations, loading states. Needs a point of view, not just "some
  animation."
- **Dark mode**: whether/how both surfaces support it (mobile is
  consumer-facing and dark mode is near-expected in 2026; admin console is
  internal-tool territory where it's more optional — worth an explicit call).
- **Accessibility baseline**: contrast ratios for financial data specifically
  (numbers must remain legible under all color treatments), tap target sizing
  on mobile, focus states for admin console keyboard navigation.

## Mobile app — every screen/flow

Group by journey, since several of these only make sense in sequence.

**Auth & onboarding**
- `LoginScreen`
- `RegisterScreen` (includes a required terms-acceptance checkbox — the
  legal-consent moment deserves deliberate framing, not a buried checkbox)
- `ForgotPasswordScreen` / `ResetPasswordScreen`
- `EmailVerificationPendingScreen`

**KYC flow** (this is the highest-anxiety flow in the app — a stranger asking
for a government ID and a selfie; the design needs to actively lower
friction/anxiety here, not just be functional)
- `KycDocumentUploadScreen` (camera capture + gallery upload for ID
  documents)
- `KycCardDetailsScreen`
- `KycSubmissionPendingScreen` (a waiting state with no clear end time —
  needs real thought on how to keep this reassuring rather than anxious)

**Home / core navigation**
- `HomeScreen` (the anchor screen — first thing seen after login, likely
  surfaces balance, quick actions, notifications entry point)
- `HomeSkeleton` (loading state for the above — skeleton screens are an
  explicit design opportunity, not just gray boxes)
- `NotificationInboxScreen`
- `AuthenticatedBootstrapScreen` (the in-between screen while session/auth
  state resolves after app launch — an easy screen to neglect, shouldn't be a
  blank flash)

**Wallet & money movement**
- `WalletScreen` (balance visibility is a real UX decision here — consider
  whether/how balance-hiding for privacy in public spaces is designed for)
- `SendMoneyScreen`
- `RecipientPickerScreen`
- `TransferSuccessScreen` (money-sent confirmation moments are worth
  deliberate delight — this is a "did it actually work" trust moment)
- `TransactionHistoryScreen` (dense list of financial records — needs a
  scannable rhythm distinct from the rest of the app, this is where
  information density is legitimately higher)

**Vaults (time-locked savings)**
- `VaultListScreen`
- `CreateVaultScreen` (setting a lock duration/goal — this is a commitment
  moment, design should make the commitment feel intentional, not just a form)
- `VaultDetailScreen` (progress toward a savings goal — a natural home for a
  strong progress-visualization moment)
- `DepositScreen` / `WithdrawScreen`
- `EarlyExitScreen` / `CancelEarlyExitScreen` (breaking a savings commitment
  early — likely has a penalty; this needs careful tone so it reads as
  "here's the real cost" without being punitive or guilt-tripping)

**Susu (group savings circles)**
- `SusuListScreen`
- `CreateSusuScreen`
- `CreateSusuInviteScreen` / `JoinSusuScreen` (invite-and-join is a
  social/trust moment distinct from the rest of the app — group money
  dynamics)
- `SusuDetailScreen` (group contribution status — likely needs to show
  multiple members' standing at once, a different information shape than any
  single-user screen)

**Challenges** (gamified savings, most likely — verify tone fits alongside
the more serious vault/susu flows rather than feeling bolted-on)
- `ChallengesListScreen`
- `ChallengeDetailScreen`

**Subscription**
- `UpgradeScreen` / `DowngradeScreen` (a pricing/upsell moment — needs to
  feel like a genuine value proposition, not a paywall dark-pattern; downgrade
  specifically should not be adversarial)

**Settings & account**
- `SettingsScreen` (hub screen)
- `ProfileScreen`
- `ChangePasswordScreen`
- `AppLockSettingsScreen` (biometric lock toggle — the lock *gate* itself,
  shown on app foreground, is arguably its own screen and deserves specific
  design attention as the very first thing a returning user sees)
- `LegalScreen`
- `DeleteAccountScreen` (an adversarial-to-the-business flow by nature —
  should still feel respectful and clear, not obstructive)

## Admin console — every page

This is an internal tool for staff (KYC reviewers, dispute handlers, staff
admins), so the design register is legitimately different from mobile —
denser, faster-to-scan, optimized for repeated daily use rather than
delight-on-first-open. But "internal tool" shouldn't default to
undifferentiated shadcn defaults either — it's still part of the product's
design identity.

- `LoginPage`
- `DashboardPage` (landing page — aggregates pending KYC count, open
  disputes, flagged accounts/groups; the first-glance information hierarchy
  here matters a lot since it's what staff see every login)
- `KycQueuePage` (a working queue a reviewer processes many times a day —
  speed and scannability are the actual UX metric here; includes bulk-approve
  actions, so multi-select interaction design matters)
- `DisputeQueuePage` / `DisputeDetailPage` (bulk-resolve actions here too —
  consider how queue-triage UX differs from KYC's, if at all)
- `UserSearchPage` / `UserDetailPage` (includes inline KYC document images —
  document review/zoom experience is a real design surface, not just an
  `<img>` tag)
- `FlaggedSusuGroupsPage` (has an all/flagged toggle — filtering/toggle
  pattern should be consistent with however KYC/dispute queues handle
  filtering)
- `StaffPage` (staff account list + invite/create + deactivate — an
  administrative CRUD page, but still deserves a considered empty state and
  a considered "are you sure" moment for deactivation)
- `AuditLogPage` (a read-heavy, dense log view — likely the most
  information-dense page in the whole product; typography/scanning rhythm
  for log-like data is its own problem)

## Cross-cutting experience patterns (apply across many screens above)

Rather than designing these once per screen, design them once as patterns
and apply consistently:

- **Empty states** — every list screen above (`VaultListScreen`,
  `SusuListScreen`, `TransactionHistoryScreen`, `NotificationInboxScreen`,
  `StaffPage`, queue pages when empty) needs a considered empty state, not a
  blank list or a generic "no items" text line.
- **Loading states** — `HomeSkeleton` exists as a pattern already; decide
  whether skeleton screens vs. spinners vs. something else is the house style,
  and apply it consistently rather than mixing approaches per screen.
- **Error states** — network failures, validation errors, and
  KYC-rejection-style "here's what went wrong and what to do next" states all
  need a consistent visual/tonal treatment. The KYC rejection screen
  specifically links out to a support email — that hand-off moment deserves
  design attention.
- **Confirmation/success micro-interactions** — money-movement actions
  (transfer, deposit, withdraw, vault creation, susu contribution) share a
  "did this actually work" trust need. Consider one confirmation pattern
  family used with intentional variation rather than one-off treatments per
  flow.
- **Destructive/high-stakes confirmations** — early vault exit, account
  deletion, staff deactivation, dispute resolution — these need a distinct
  "are you sure, here are the real consequences" pattern, calibrated by
  actual severity (early exit's financial penalty vs. account deletion's
  irreversibility are different kinds of "serious").
- **Biometric app-lock gate** — functions as a de facto splash/lock screen
  for returning users; treat as prime real estate, not an afterthought modal.
- **Balance/number formatting and legibility** — currency figures appear
  everywhere (wallet, vaults, susu, transaction history, admin dispute
  amounts); establish one considered treatment for how monetary values are
  sized, weighted, and colored relative to surrounding text, and apply it
  everywhere money appears.
