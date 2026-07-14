# PennyRise Admin Console

Operator-facing web app for KYC review, support, reconciliation, and ops dashboards.

Built with Vite + React + TypeScript + Tailwind CSS + shadcn/ui + React Router.

## Prerequisites

| Tool | Version |
|------|---------|
| Node.js | 18+ |
| pnpm | 8+ |

## Setup

```bash
cd admin-console
pnpm install
cp .env.example .env.local
```

## Running locally

```bash
pnpm dev
```

Serves at `http://localhost:5173`.

## Linting and type checking

```bash
pnpm lint          # ESLint
pnpm format:check  # Prettier
pnpm typecheck     # TypeScript
```

## Building for production

```bash
pnpm build       # outputs to dist/
pnpm preview     # preview the production build locally
```

Output is a static SPA — deployed to Cloudflare Pages with no Node.js runtime required.

## Folder structure

```
admin-console/
├── src/
│   ├── main.tsx          # React entry point
│   ├── App.tsx            # Router setup, providers
│   ├── api/                # Backend API client
│   ├── auth/               # Auth flow and token management
│   ├── routes/             # One folder per route
│   ├── components/         # Reusable UI (ui/ = shadcn primitives)
│   ├── features/           # Self-contained feature modules
│   ├── hooks/               # Reusable React hooks
│   ├── lib/                  # Utility wrappers (cn(), date, format)
│   ├── styles/              # Global Tailwind layer
│   ├── types/                # Admin-specific TypeScript types
│   └── utils/                # Pure helper functions
├── tests/                    # Vitest unit tests
└── e2e/                       # Playwright e2e tests
```

## Environment variables

All client-visible env vars are prefixed `VITE_`. See `.env.example`.

**Never put secrets in `.env.local`** — anything `VITE_`-prefixed ships in the
client JS bundle and is visible in browser dev tools.
