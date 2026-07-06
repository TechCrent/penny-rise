# Mobile Hands-On Testing Findings

Companion to `docs/hands-on-testing-findings.md`, same discipline: every
problem is logged here **before** it gets fixed, and stays here afterward
with a `**Fix:**` note appended — nothing is deleted.

Testing environment note: this sandbox is Linux/WSL2. The iOS Simulator is
a macOS-only Apple tool and cannot run here under any circumstances — this
is a hard platform limitation, not a missing package. iOS coverage in this
pass is a **code review** of `Platform.OS === 'ios'` branches and
iOS-relevant native module usage, not a running simulator. Android is
tested hands-on via a real emulator (Android SDK cmdline-tools +
platform-tools + emulator + an `android-34`/`google_apis`/`x86_64` system
image, installed fresh in this session).

`mobile/.env`'s `EXPO_PUBLIC_API_BASE_URL` was repointed from a stale LAN
IP (`10.252.86.45:8080`, left over from a previous physical-device testing
setup) to `10.0.2.2:8080` — the Android emulator's standard alias for the
host machine's own `localhost`. This only works from inside the emulator,
not from a physical device; not a production concern.

---

## Finding 1: `expo-constants` is imported but never installed — crashes push-notification setup

**Severity:** Critical — this isn't a lint nitpick, it's a missing runtime
dependency for code that already exists and already runs at app startup.

**Problem:** `src/features/notifications/pushSetup.ts` and
`src/features/notifications/notificationHandler.ts` both
`import Constants from 'expo-constants'` and use it
(`Constants.appOwnership`, `Constants.executionEnvironment`) to detect
whether the app is running inside Expo Go and skip push-registration
there (since real push tokens require a dev/prod build, not Expo Go).
`expo-constants` is **not** in `package.json` at all and isn't installed
in `node_modules` — surfaced immediately by `pnpm typecheck`:

```
src/features/notifications/notificationHandler.ts(2,23): error TS2307: Cannot find module 'expo-constants' or its corresponding type declarations.
src/features/notifications/pushSetup.ts(2,23): error TS2307: Cannot find module 'expo-constants' or its corresponding type declarations.
```

Metro (the RN bundler) would fail to resolve this import at bundle time —
this isn't a "TypeScript is being pedantic" error, the module genuinely
does not exist in `node_modules`, so any code path that imports
`pushSetup.ts`/`notificationHandler.ts` (push notification setup, which
typically runs on app startup or first authenticated screen) would break
the bundle before a single screen renders.

**How this was found:** running `pnpm typecheck` as a first health check
before even launching the emulator, following the same "verify basic
project health before hands-on driving" pattern used for the backend.

**Fix:** _pending — see below once addressed._

---

## Finding 2: test mock violates the KYC submission response type

**Severity:** Low — test-only, doesn't affect the running app, but breaks
`pnpm typecheck`/`pnpm verify` (both gate CI).

**Problem:** `__tests__/resolvePostAuthNavigation.test.ts` mocks
`kycApi.getMySubmission` with `submitted_at: null`, but both
`src/api/kyc.ts` and `src/screens/DeleteAccount/types.ts` type
`submitted_at` as a required `string` — matching the real backend, which
always sets `submitted_at` the moment a submission is created (confirmed
against the real API in the backend hands-on testing pass:
`"submitted_at":"2026-07-05T18:58:43.042364Z"` on every submission,
never null).

```
__tests__/resolvePostAuthNavigation.test.ts(37,7): error TS2322: Type 'null' is not assignable to type 'string'.
```

**How this was found:** same `pnpm typecheck` run as Finding 1.

**Fix:** _pending — see below once addressed._

---

## Finding 3 (iOS code review — no simulator available): missing camera/photo-library permission strings would crash the KYC document upload flow on iOS

**Severity:** Critical for iOS specifically — this is a hard OS-level
enforcement, not a soft warning: iOS terminates an app that calls a
privacy-sensitive API (camera, photo library) without the corresponding
`Info.plist` usage-description string present. There is no graceful
degradation; the app crashes.

**Problem:** `src/components/DocumentUploadSlot.tsx` — used for KYC
document capture (front of card, back of card, selfie), a core,
must-work flow — calls both:

```ts
await ImagePicker.requestMediaLibraryPermissionsAsync();
await ImagePicker.launchImageLibraryAsync({...});
...
await ImagePicker.requestCameraPermissionsAsync();
await ImagePicker.launchCameraAsync({...});
```

`app.json`'s `ios.infoPlist` had `NSFaceIDUsageDescription` (for App Lock,
correctly present) but no `NSCameraUsageDescription` or
`NSPhotoLibraryUsageDescription`, and `expo-image-picker` wasn't in the
`plugins` array at all (unlike `expo-local-authentication`,
`expo-secure-store`, `expo-notifications`, which all were) — so its config
plugin, which would otherwise inject these strings automatically, never
ran. Every other native-module-using dependency in this app had its
permission strings/config plugin wired up correctly; this one was missed.

**How this was found:** since the iOS Simulator can't run on this Linux
sandbox (hard platform limitation — see the note at the top of this
file), iOS coverage in this pass is a manual read-through of
`Platform.OS === 'ios'` branches and native-module usage rather than
driving a running simulator. Cross-referencing every native module import
against `app.json`'s `plugins`/`ios.infoPlist` surfaced this gap
immediately — every other module had matching config except this one.

**Fix:** Added `expo-image-picker` to `app.json`'s `plugins` array with
its config-plugin `cameraPermission`/`photosPermission` options, matching
the same pattern already used for the splash screen plugin (the
idiomatic Expo way to set these strings, rather than hand-duplicating raw
`Info.plist` keys the way `NSFaceIDUsageDescription` currently is):

```json
[
  "expo-image-picker",
  {
    "cameraPermission": "Stash uses your camera to capture KYC documents (Ghana Card, selfie).",
    "photosPermission": "Stash uses your photo library so you can upload existing KYC documents."
  }
]
```

Not verified on a real iOS build/simulator (impossible in this
environment) — verified only that the config plugin is now present and
wired the same way every other native module's is. A native
build/EAS build on real iOS hardware or a Mac should confirm no crash on
first camera/library access.

---
