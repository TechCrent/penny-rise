# reset-local-data.ps1
# Wipes local dev data so the app starts from a clean slate: truncates every
# table in all 4 service databases (keeping schema/migrations and admin
# accounts intact), clears local KYC document storage, purges RabbitMQ queues,
# and empties the Mailpit test inbox.
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
