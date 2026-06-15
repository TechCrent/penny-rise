# Stash Mobile App

React Native + Expo (SDK 56) customer app for the Stash savings platform.

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Node.js | 18+ | https://nodejs.org |
| pnpm | 8+ | `npm install -g pnpm` |
| Expo CLI | latest | `npm install -g expo-cli` |
| iOS Simulator | Xcode 15+ | Mac only — App Store |
| Android Emulator | Android Studio | https://developer.android.com/studio |
| Expo Go (SDK 56) | SDK 56 | **Not** Play Store — install from https://expo.dev/go |

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

This project uses **Expo SDK 56**. The Play Store / App Store Expo Go app is still on an older SDK and **cannot load this project** (you may see `Failed to download remote update`).

1. On your phone, open https://expo.dev/go → **SDK 56** → **Android** → **Install** (direct APK, not Play Store).
2. Start Metro (port 19000 avoids backend port clashes):

```bash
pnpm start
# Scan the QR code with the SDK 56 Expo Go you installed
```

If the phone cannot reach your PC over Wi‑Fi, use the tunnel:

```bash
pnpm run start:tunnel
```

## Linting and type checking

```bash
pnpm lint          # ESLint
pnpm format:check  # Prettier
pnpm typecheck     # TypeScript
```

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
