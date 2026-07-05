#!/usr/bin/env bash
# reset-local-data.sh
# Wipes local dev data so the app starts from a clean slate: truncates every
# table in all 4 service databases (keeping schema/migrations and admin
# accounts intact), clears local KYC document storage, purges RabbitMQ queues,
# and empties the Mailpit test inbox.
#
# Run from the repo root:  ./reset-local-data.sh
# Skip the confirmation prompt:  ./reset-local-data.sh --force

set -euo pipefail

force=0
if [ "${1:-}" = "--force" ] || [ "${1:-}" = "-Force" ]; then
    force=1
fi

if [ "$force" -ne 1 ]; then
    echo -e "\033[33mThis will permanently delete ALL local app data:\033[0m"
    echo "  - Every table in monolith_db, kyc_db, payments_db, audit_db (admin accounts kept)"
    echo "  - KYC document files on disk (/tmp/stash-kyc-storage)"
    echo "  - RabbitMQ queue contents"
    echo "  - Mailpit test inbox"
    echo ""
    read -r -p "Type 'yes' to continue: " answer
    if [ "$answer" != "yes" ]; then
        echo -e "\033[33mAborted.\033[0m"
        exit 0
    fi
fi

# Truncates every table in every non-system schema of a database, except
# Flyway's own history table and (in monolith_db) admin.admin_accounts, so
# your admin console login survives the reset.
read -r -d '' truncate_sql <<'EOF' || true
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
EOF

declare -a containers=(stash-monolith-db stash-kyc-db stash-payments-db stash-audit-db)
declare -a users=(stash_monolith stash_kyc stash_payments stash_audit)
declare -a dbs=(monolith_db kyc_db payments_db audit_db)

echo -e "\n\033[36mTruncating databases...\033[0m"
for i in "${!containers[@]}"; do
    container="${containers[$i]}"
    user="${users[$i]}"
    db="${dbs[$i]}"

    running="$(docker ps --filter "name=$container" --format "{{.Names}}" 2>/dev/null || true)"
    if [ -z "$running" ]; then
        echo -e "\033[33m  SKIP $db: container $container is not running\033[0m"
        continue
    fi

    if echo "$truncate_sql" | docker exec -i "$container" psql -U "$user" -d "$db" -v ON_ERROR_STOP=1 -q >/dev/null 2>&1; then
        echo -e "\033[32m  Cleared $db\033[0m"
    else
        echo -e "\033[33m  WARNING: failed to clear $db\033[0m"
    fi
done

echo -e "\n\033[36mClearing local KYC document storage...\033[0m"
kyc_storage_root="/tmp/stash-kyc-storage"
if [ -d "$kyc_storage_root" ]; then
    rm -rf "${kyc_storage_root:?}"/*
    echo -e "\033[32m  Cleared $kyc_storage_root\033[0m"
else
    echo -e "\033[33m  $kyc_storage_root does not exist, nothing to clear\033[0m"
fi

echo -e "\n\033[36mPurging RabbitMQ queues...\033[0m"
rabbit_running="$(docker ps --filter "name=stash-rabbitmq" --format "{{.Names}}" 2>/dev/null || true)"
if [ -n "$rabbit_running" ]; then
    # rabbitmqctl prints status banners ("Timeout: ...", "Listing queues...") to
    # stdout alongside the actual names even with --no-table-headers, so filter
    # to lines that look like an actual queue name (dot/word/hyphen, no spaces).
    queue_names="$(docker exec stash-rabbitmq rabbitmqctl list_queues -p stash --no-table-headers name 2>/dev/null | \
        sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | grep -E '^[A-Za-z0-9_.-]+$' || true)"

    if [ -n "$queue_names" ]; then
        while IFS= read -r q; do
            docker exec stash-rabbitmq rabbitmqctl purge_queue "$q" -p stash >/dev/null 2>&1 || true
            echo -e "\033[32m  Purged queue: $q\033[0m"
        done <<< "$queue_names"
    else
        echo -e "\033[33m  No queues found\033[0m"
    fi
else
    echo -e "\033[33m  SKIP: stash-rabbitmq is not running\033[0m"
fi

echo -e "\n\033[36mClearing Mailpit inbox...\033[0m"
if curl -sf -X DELETE "http://localhost:8025/api/v1/messages" >/dev/null 2>&1; then
    echo -e "\033[32m  Cleared Mailpit inbox\033[0m"
else
    echo -e "\033[33m  WARNING: could not reach Mailpit at localhost:8025\033[0m"
fi

echo -e "\n\033[36mDone. Backend data is reset to a clean slate.\033[0m"
echo -e "\033[90mNote: this only touches server-side state. If the mobile app still shows a\033[0m"
echo -e "\033[90mlogged-in/cached session, log out (or clear the app's storage / reinstall)\033[0m"
echo -e "\033[90mon the device so it doesn't hold onto tokens for now-deleted accounts.\033[0m"
