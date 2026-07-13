# reset-local-data.ps1
# Wipes local dev data so the app starts from a clean slate: truncates every
# table in all 4 service databases (keeping schema/migrations and admin
# accounts intact), re-seeds fixed reference data that Flyway won't reinsert
# on its own (system ledger accounts, challenge templates), clears local KYC
# document storage, purges RabbitMQ queues, and empties the Mailpit test inbox.
#
# Run from the repo root:  .\reset-local-data.ps1
# Skip the confirmation prompt:  .\reset-local-data.ps1 -Force

param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not $Force) {
    Write-Host "This will permanently delete ALL local app data:" -ForegroundColor Yellow
    Write-Host "  - Every table in monolith_db, kyc_db, payments_db, audit_db (admin accounts kept)"
    Write-Host "  - KYC document files on disk (C:\tmp\stash-kyc-storage)"
    Write-Host "  - RabbitMQ queue contents"
    Write-Host "  - Mailpit test inbox"
    Write-Host ""
    $answer = Read-Host "Type 'yes' to continue"
    if ($answer -ne "yes") {
        Write-Host "Aborted." -ForegroundColor Yellow
        exit 0
    }
}

# Truncates every table in every non-system schema of a database, except
# Flyway's own history table and (in monolith_db) admin.admin_accounts, so
# your admin console login survives the reset.
$truncateSql = @'
DO $$
DECLARE
    r RECORD;
    tbls TEXT := '';
BEGIN
    FOR r IN
        SELECT schemaname, tablename
        FROM pg_tables
        WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
          AND tablename NOT LIKE 'flyway_schema_history%'
          AND NOT (schemaname = 'admin' AND tablename = 'admin_accounts')
    LOOP
        tbls := tbls || format('%I.%I, ', r.schemaname, r.tablename);
    END LOOP;

    IF length(tbls) > 0 THEN
        tbls := left(tbls, length(tbls) - 2);
        EXECUTE 'TRUNCATE TABLE ' || tbls || ' RESTART IDENTITY CASCADE';
    END IF;
END $$;
'@

$databases = @(
    @{ Container = "stash-monolith-db"; User = "stash_monolith"; Db = "monolith_db" },
    @{ Container = "stash-kyc-db";      User = "stash_kyc";      Db = "kyc_db" },
    @{ Container = "stash-payments-db"; User = "stash_payments"; Db = "payments_db" },
    @{ Container = "stash-audit-db";    User = "stash_audit";    Db = "audit_db" }
)

# Flyway migrations that seed fixed reference data (system ledger accounts,
# challenge templates) only ever run once — Flyway skips them on every later
# startup because their checksum still matches what's recorded, even though
# the truncate above just deleted the rows they inserted. Without re-seeding
# here, every deposit/withdrawal/challenge feature breaks after a reset
# (found the hard way: two stuck deposits traced back to the missing
# PAYSTACK_SETTLEMENT ledger account after a reset wiped it out).
$paymentsSeedSql = @'
INSERT INTO ledger.ledger_accounts (id, account_type, owner_type, owner_id, external_reference, status, description, created_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'PAYSTACK_SETTLEMENT', 'SYSTEM', NULL, 'paystack-settlement-master', 'ACTIVE', 'Represents funds received by Paystack on behalf of Stash. DEBIT leg for all charge.success deposits.', NOW()),
    ('00000000-0000-0000-0000-000000000002', 'FEE_REVENUE', 'SYSTEM', NULL, NULL, 'ACTIVE', 'Platform fee revenue - early-exit penalties and transaction fees.', NOW()),
    ('00000000-0000-0000-0000-000000000003', 'PENALTY_REVENUE', 'SYSTEM', NULL, NULL, 'ACTIVE', 'Platform penalty revenue - susu late-payment fees (50% share).', NOW()),
    ('00000000-0000-0000-0000-000000000004', 'MOOLRE_SETTLEMENT', 'SYSTEM', NULL, 'moolre-settlement-master', 'ACTIVE', 'Represents funds received by Moolre on behalf of Stash. DEBIT leg for all Moolre payment-success deposits.', NOW())
ON CONFLICT (id) DO NOTHING;
'@

$monolithSeedSql = @'
INSERT INTO challenge.badges (id, badge_code, badge_name, asset_name) VALUES
    ('a1000000-0000-4000-8000-000000000001', 'STARTER_SAVER',    'Starter Saver',    'badge_starter_saver'),
    ('a1000000-0000-4000-8000-000000000002', 'CONSISTENT_SAVER', 'Consistent Saver', 'badge_consistent_saver'),
    ('a1000000-0000-4000-8000-000000000003', 'DEDICATED_SAVER',  'Dedicated Saver',  'badge_dedicated_saver'),
    ('a1000000-0000-4000-8000-000000000004', 'NO_BREAK_30D',     'No-Break 30',      'badge_no_break_30')
ON CONFLICT (id) DO NOTHING;

INSERT INTO challenge.savings_challenges
    (id, name, description, challenge_type, target_amount, target_duration_days, system_owned, creator_user_id, is_active, badge_id) VALUES
    ('b2000000-0000-4000-8000-000000000001', 'Save GHS 50 in 7 Days', 'A quick starter challenge - save GHS 50 across any vault within a week.', 'SAVE_AMOUNT', 5000, 7, true, NULL, true, 'a1000000-0000-4000-8000-000000000001'),
    ('b2000000-0000-4000-8000-000000000002', 'Save GHS 200 in 30 Days', 'Build a saving habit - GHS 200 over a month.', 'SAVE_AMOUNT', 20000, 30, true, NULL, true, 'a1000000-0000-4000-8000-000000000002'),
    ('b2000000-0000-4000-8000-000000000003', 'Save GHS 1000 in 90 Days', 'The long game - GHS 1000 saved over three months.', 'SAVE_AMOUNT', 100000, 90, true, NULL, true, 'a1000000-0000-4000-8000-000000000003'),
    ('b2000000-0000-4000-8000-000000000004', 'No-Withdrawal Streak: 30 Days', 'Go 30 days without a single withdrawal from any vault.', 'NO_WITHDRAWAL', NULL, 30, true, NULL, true, 'a1000000-0000-4000-8000-000000000004')
ON CONFLICT (id) DO NOTHING;
'@

Write-Host "`nTruncating databases..." -ForegroundColor Cyan
foreach ($d in $databases) {
    $running = docker ps --filter "name=$($d.Container)" --format "{{.Names}}" 2>$null
    if (-not $running) {
        Write-Host "  SKIP $($d.Db): container $($d.Container) is not running" -ForegroundColor Yellow
        continue
    }

    $truncateSql | docker exec -i $d.Container psql -U $d.User -d $d.Db -v ON_ERROR_STOP=1 -q 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Cleared $($d.Db)" -ForegroundColor Green
    }
    else {
        Write-Host "  WARNING: failed to clear $($d.Db)" -ForegroundColor Yellow
    }

    $reseedSql = if ($d.Db -eq "payments_db") { $paymentsSeedSql } elseif ($d.Db -eq "monolith_db") { $monolithSeedSql } else { $null }
    if ($reseedSql) {
        $reseedSql | docker exec -i $d.Container psql -U $d.User -d $d.Db -v ON_ERROR_STOP=1 -q 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Re-seeded fixed reference data in $($d.Db)" -ForegroundColor Green
        }
        else {
            Write-Host "  WARNING: failed to re-seed reference data in $($d.Db)" -ForegroundColor Yellow
        }
    }
}

Write-Host "`nClearing local KYC document storage..." -ForegroundColor Cyan
$kycStorageRoot = "C:\tmp\stash-kyc-storage"
if (Test-Path $kycStorageRoot) {
    Get-ChildItem -Path $kycStorageRoot -Force | Remove-Item -Recurse -Force -Confirm:$false
    Write-Host "  Cleared $kycStorageRoot" -ForegroundColor Green
}
else {
    Write-Host "  $kycStorageRoot does not exist, nothing to clear" -ForegroundColor Yellow
}

Write-Host "`nPurging RabbitMQ queues..." -ForegroundColor Cyan
$rabbitRunning = docker ps --filter "name=stash-rabbitmq" --format "{{.Names}}" 2>$null
if ($rabbitRunning) {
    # rabbitmqctl prints status banners ("Timeout: ...", "Listing queues...") to
    # stdout alongside the actual names even with --no-table-headers, so filter
    # to lines that look like an actual queue name (dot/word/hyphen, no spaces).
    $queueNames = docker exec stash-rabbitmq rabbitmqctl list_queues -p stash --no-table-headers name 2>$null |
        ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^[A-Za-z0-9_.\-]+$' }

    if ($queueNames) {
        foreach ($q in $queueNames) {
            docker exec stash-rabbitmq rabbitmqctl purge_queue $q -p stash 2>&1 | Out-Null
            Write-Host "  Purged queue: $q" -ForegroundColor Green
        }
    }
    else {
        Write-Host "  No queues found" -ForegroundColor Yellow
    }
}
else {
    Write-Host "  SKIP: stash-rabbitmq is not running" -ForegroundColor Yellow
}

Write-Host "`nClearing Mailpit inbox..." -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "http://localhost:8025/api/v1/messages" -Method Delete -ErrorAction Stop | Out-Null
    Write-Host "  Cleared Mailpit inbox" -ForegroundColor Green
}
catch {
    Write-Host "  WARNING: could not reach Mailpit at localhost:8025 ($($_.Exception.Message))" -ForegroundColor Yellow
}

Write-Host "`nDone. Backend data is reset to a clean slate." -ForegroundColor Cyan
Write-Host "Note: this only touches server-side state. If the mobile app still shows a" -ForegroundColor DarkGray
Write-Host "logged-in/cached session, log out (or clear the app's storage / reinstall)" -ForegroundColor DarkGray
Write-Host "on the device so it doesn't hold onto tokens for now-deleted accounts." -ForegroundColor DarkGray
