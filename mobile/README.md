# Stash Mobile App

React Native + Expo (SDK 54) customer app for the Stash savings platform.

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Node.js | 18+ | https://nodejs.org |
| pnpm | 8+ | `npm install -g pnpm` |
| Expo CLI | latest | `npm install -g expo-cli` |
| iOS Simulator | Xcode 15+ | Mac only — App Store |
| Android Emulator | Android Studio | https://developer.android.com/studio |
| Expo Go (SDK 54) | SDK 54 | Play Store / App Store, or https://expo.dev/go |

## Setup

```bash
cd mobile
pnpm install
cp .env.example .env.local
```

## Running

**iOS Simulator (Mac only):**
```bash
pnpm ios
```

**Android Emulator:**
```bash
pnpm android
```

**Expo Go (physical device):**

This project uses **Expo SDK 54**, which matches the current Play Store / App Store Expo Go app.

1. Install **Expo Go** from the Play Store or App Store (SDK 54).
2. Start Metro (port 19000 avoids backend port clashes):

```bash
pnpm start
# Scan the QR code with Expo Go
```

If the phone cannot reach your PC over Wi‑Fi, use the tunnel:

```bash
pnpm run start:tunnel
```

## Linting, formatting, and tests

CI runs these in order (see `.github/workflows/mobile-ci.yml`). **Run all of them before pushing** — not just `pnpm test`:

```bash
pnpm format        # fix Prettier (src + __tests__)
pnpm format:check  # verify formatting (CI gate)
pnpm lint          # ESLint
pnpm typecheck     # TypeScript
pnpm test          # Jest unit tests

# or run the full CI sequence locally:
pnpm verify
```

### Common CI failures (mobile)

| Failure | Cause | Fix |
|---------|-------|-----|
| `format:check` | Prettier not run on new/edited files | `pnpm format` before push |
| ESLint `no-inline-styles` | Inline `style={{...}}` in JSX | Extract to `StyleSheet.create` |
| Jest transform / module not found | Missing or wrong `babel.config.js` / `jest-expo` | Use `babel-preset-expo` ~54, `jest` ~29, `jest-expo` ~54; `jest.config.js` preset only — do not override `transformIgnorePatterns` |
| Tests render empty tree | Missing `SafeAreaProvider` metrics in test wrapper | Pass `initialMetrics` in test helper |
| `screen.getBy*` not found | `@testing-library/react-native` v14 API | Stay on v12; use `render()` return queries |
| 409 error test fails | Plain object mock | Use real `axios.AxiosError` for `extractApiError` |
| Double-submit test fails | Async `useState` guard only | Add synchronous `useRef` guard |
| Loading test fails | Button text still visible while loading | Assert `ActivityIndicator` via `UNSAFE_getAllByType` |

New screen tests: mock `expo-secure-store`; wrap with `SafeAreaProvider` + navigation test harness.

## Folder structure

```
mobile/
├── src/
│   ├── App.tsx          # Root component, navigation, providers
│   ├── api/             # Backend HTTP client
│   ├── auth/            # Auth flow and token management
│   ├── navigation/      # Navigation graph
│   ├── screens/         # One folder per screen
│   ├── features/        # Self-contained feature modules
│   ├── components/      # Reusable UI components
│   ├── hooks/           # Reusable React hooks
│   ├── theme/           # Colors, typography, spacing
│   ├── storage/         # Secure storage wrappers
│   ├── utils/           # Pure helper functions
│   └── types/           # Mobile-specific TypeScript types
├── assets/
│   ├── images/
│   ├── icons/
│   └── fonts/
└── __tests__/
```

## Environment variables

All env vars are prefixed with `EXPO_PUBLIC_` to be accessible in the app bundle.
See `.env.example` for all required variables.
