# Stash Monorepo

All Stash code lives in this single repository. See `docs/architecture/Stash_Folder_Structure_v3.docx` for the full layout guide.

## Module Layout

| Folder | Type | Description |
|---|---|---|
| `monolith/` | Java / Spring Boot | Core business modules (platform, financial, social, tracker) |
| `payments-service/` | Java / Spring Boot | Double-entry ledger, Paystack integration |
| `kyc-service/` | Java / Spring Boot | KYC submissions and document handling |
| `audit-service/` | Java / Spring Boot | Append-only audit log (event consumer) |
| `mobile/` | React Native / Expo | Customer mobile app |
| `admin-console/` | React / Vite / TS | Operator admin web app |
| `shared/` | Java + TypeScript | Shared libraries across services |
| `infra/` | Docker / IaC | Docker Compose, k8s, Terraform |
| `docs/` | Markdown / Docs | ADRs and architecture documents |

## Getting Started (Backend)

Requires JDK 21 or newer.

```bash
# Windows
.\mvnw.cmd clean install

# macOS/Linux
./mvnw clean install
```

## Reference
- Folder Structure: `docs/architecture/Stash_Folder_Structure_v3.docx`
- Git Workflow: `docs/architecture/Stash_Git_Workflow_v3.docx`
