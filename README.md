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

### Running a service locally

The root POM is an aggregator only — it has no `main` class. Run a specific module:

```bash
# Windows — all services
.\start-services.ps1

# Or one service (monolith on :8080)
.\mvnw.cmd -pl monolith spring-boot:run
```

## API Documentation Convention

Every backend service exposes a live, interactive API reference via Swagger UI
in local development:

| Service | Swagger UI | Raw Spec |
|---|---|---|
| Monolith | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| Payments Service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| KYC Service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| Audit Service | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |

Swagger UI is **disabled in production** (`application-prod.yml`).

### Annotation requirement (effective v0.2 onwards)

Every new endpoint must be annotated so the generated OpenAPI spec stays
informative for the mobile and admin console teams, and so TypeScript types
can be generated from it accurately. At minimum:

```java
@Operation(summary = "Create a new vault", description = "Creates a savings vault for the authenticated user.")
@ApiResponse(responseCode = "201", description = "Vault created")
@ApiResponse(responseCode = "400", description = "Validation error")
@ApiResponse(responseCode = "401", description = "Not authenticated")
@PostMapping("/vaults")
public ResponseEntity<VaultResponse> createVault(
        @Parameter(description = "Vault creation request") @Valid @RequestBody CreateVaultRequest request) {
    ...
}
```

This is a Definition of Done item for every endpoint issue from v0.2 onward —
PRs adding endpoints without these annotations will be sent back in review.

## Local Development

See **`docs/v0.2-known-limits.md`** for v0.2 accepted stubs, Tier 3 close-out checklist, and port reference. Schema/Issue Plan mapping: **`docs/v0.2-schema-reconciliation.md`**.

Quick notes:

- **RabbitMQ topology changes** require `docker compose down -v && docker compose up --build -d` so `definitions.json` is reapplied.
- **Payments DB** is exposed on host port **15433** (see `infra/compose/databases.yml`). On Windows, if bind fails with “access permissions”, the port may fall in a Hyper-V excluded range — pick an alternate host port in compose + `payments-service/application.yml`.
- **Mobile** talks to the monolith at `:8080` (includes KYC proxy). **Admin console** talks to kyc-service at `:8082` directly.

## Reference
- Folder Structure: `docs/architecture/Stash_Folder_Structure_v3.docx`
- Git Workflow: `docs/architecture/Stash_Git_Workflow_v3.docx`
