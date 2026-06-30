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
| V8       | v0.3-033  | `vault/V8__add_attempts_to_early_exit_requests.sql` | `vault.locked_vault_early_exit_requests` |
| V9       | v0.3-034  | `vault/V9__add_unlocked_at_to_vaults.sql` | `vault.vaults` |
| V10      | audit fix | `vault/V10__add_momo_to_early_exit_requests.sql` | `vault.locked_vault_early_exit_requests` |
| V11      | v0.4-001  | `susu/V11__create_susu_groups.sql` | `susu.susu_groups` |
| V12      | v0.4-002  | `susu/V12__create_susu_memberships.sql` | `susu.susu_memberships` |
| V13      | v0.4-003  | `susu/V13__create_susu_rounds.sql` | `susu.susu_rounds` |
| V14      | v0.4-004  | `susu/V14__create_susu_contributions.sql` | `susu.susu_contributions` |
| V15      | v0.4-008  | `susu/V15__add_ledger_account_to_susu_groups.sql` | `susu.susu_groups` |
| V16      | v0.4-012  | `susu/V16__add_left_status_to_memberships.sql` | `susu.susu_memberships` |
| V17      | v0.4-013  | `transfer/V17__create_transfer_tables.sql` | `transfer.peer_transfers`, `transfer.monthly_transfer_quotas` |
| V18      | v0.4-016  | `user_module/V18__create_beta_allowlist.sql` | `user_module.beta_allowlist` |
| V19      | v0.4-021  | `susu/V19__create_susu_contribution_reminders.sql` | `susu.susu_contribution_reminders` |
| V20      | v0.5-001  | `admin/V20__create_admin_accounts.sql` + `db.migration.admin.V20_1__BootstrapSuperAdmin` | `admin.admin_accounts` |
| V21      | v0.5-002  | `admin/V21__create_admin_audit_actions.sql` | `admin.admin_audit_actions` |
| V21.1    | v0.5-002  | `admin/V21_1__revoke_modify_on_admin_audit_actions.sql` | REVOKE UPDATE/DELETE on `admin.admin_audit_actions` |
| V22      | v0.5-002  | `admin/V22__create_disputes.sql` | `admin.disputes` |
| V23      | v0.5-003  | `admin/V23__create_admin_auth_tables.sql` | `admin.admin_refresh_tokens`, `admin.admin_login_attempts` |

**Next free version:** assign the next row when opening a migration PR. Merge
migration PRs in version order (004 before 005 before 006).

## Filename convention

```
{folder}/V{n}__create_{snake_case_description}.sql
```

## Rollbacks

Forward-only. To undo a migration, add a new forward migration (never rename
an already-merged version).
