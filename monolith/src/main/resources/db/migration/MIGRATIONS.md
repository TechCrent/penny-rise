# Monolith Flyway migrations

Flyway version numbers are **global** across every folder listed in
`application.yml` (`user_module/`, `auth/`, `vault/`, etc.). Folders are
organizational only — there is exactly one V1, one V2, one V3, and so on
for the whole monolith.

**Do not** number migrations per folder or by issue id (v0.2-004 is Flyway **V3**,
not V4).

## Sequence

| Global V | Issue     | File | Table / schema |
|----------|-----------|------|----------------|
| V1       | v0.2-001  | `user_module/V1__create_user_module_users.sql` | `user_module.users` |
| V2       | v0.2-003  | `auth/V2__create_auth_refresh_tokens.sql` | `auth.refresh_tokens` |
| V3       | v0.2-004  | `user_module/V3__create_email_verification_tokens.sql` | `user_module.email_verification_tokens` |
| V4       | v0.2-005  | `user_module/V4__create_password_reset_tokens.sql` | `user_module.password_reset_tokens` |
| V5       | v0.2-006  | `user_module/V5__create_deletion_requests.sql` | `user_module.deletion_requests` |
| V6       | v0.3-025  | `vault/V6__create_vaults.sql` | `vault.vaults` |
| V7       | v0.3-026  | `vault/V7__create_locked_vault_early_exit_requests.sql` | `vault.locked_vault_early_exit_requests` |

**Next free version:** assign the next row when opening a migration PR. Merge
migration PRs in version order (004 before 005 before 006).

## Filename convention

```
{folder}/V{n}__create_{snake_case_description}.sql
```

## Rollbacks

Forward-only. To undo a migration, add a new forward migration (never rename
an already-merged version).
