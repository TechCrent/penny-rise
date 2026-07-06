#!/usr/bin/env bash
# End-to-end payment flow verification (local dev)
# Usage: ./scripts/test-payments-e2e.sh
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

load_env() {
    if [ -f "$root/.env" ]; then
        set -a
        # shellcheck disable=SC1091
        source "$root/.env"
        set +a
    fi
}

wait_http() {
    local url="$1" timeout_sec="${2:-120}"
    local deadline=$(( $(date +%s) + timeout_sec ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$url" 2>/dev/null)"; then
            if [ "$code" = "200" ]; then
                return 0
            fi
        fi
        sleep 3
    done
    echo "Timeout waiting for $url" >&2
    exit 1
}

hmac_paystack() {
    local secret="$1" body_file="$2"
    openssl dgst -sha512 -hmac "$secret" -r "$body_file" | awk '{print $1}'
}

send_paystack_webhook() {
    local paystack_ref="$1" amount_pesewas="$2"
    local event_id
    event_id="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)"
    local payload_file
    payload_file="$(mktemp)"
    jq -n --arg id "$event_id" --arg ref "$paystack_ref" --argjson amount "$amount_pesewas" \
        '{event:"charge.success", id:$id, data:{reference:$ref, amount:$amount, status:"success"}}' \
        > "$payload_file"
    local sig
    sig="$(hmac_paystack "${PAYSTACK_WEBHOOK_SECRET:-}" "$payload_file")"
    curl -s -X POST "http://localhost:8081/webhooks/paystack" \
        -H "x-paystack-signature: $sig" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: webhook-$paystack_ref" \
        --data-binary "@$payload_file" >/dev/null
    rm -f "$payload_file"
}

get_verify_link() {
    local email="$1"
    local msgs msg_id full text link
    msgs="$(curl -s --max-time 10 "http://localhost:8025/api/v1/messages")"
    msg_id="$(echo "$msgs" | jq -r --arg email "$email" '[.messages[] | select(.To[0].Address == $email)][0].ID // empty')"
    if [ -z "$msg_id" ]; then
        echo "No verification email for $email" >&2
        exit 1
    fi
    full="$(curl -s --max-time 10 "http://localhost:8025/api/v1/message/$msg_id")"
    text="$(echo "$full" | jq -r '.Text')"
    link="$(echo "$text" | grep -oE 'http://localhost:8080/api/v1/auth/verify-email\?token=[^[:space:]"]+' | head -n1)"
    if [ -z "$link" ]; then
        echo "Verify link not found in email for $email" >&2
        exit 1
    fi
    echo "$link"
}

register_and_login() {
    local email="$1" password="$2" name="$3"
    curl -s -X POST "http://localhost:8080/api/v1/auth/signup" \
        -H "Content-Type: application/json" \
        -d "$(jq -n --arg email "$email" --arg password "$password" --arg name "$name" '{email:$email,password:$password,display_name:$name,terms_accepted:true}')" >/dev/null
    sleep 1
    local link
    link="$(get_verify_link "$email")"
    curl -s "$link" >/dev/null
    curl -s -X POST "http://localhost:8080/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "$(jq -n --arg email "$email" --arg password "$password" '{email:$email,password:$password}')" \
        | jq -r '.access_token'
}

load_env

echo -e "\033[36m=== Resetting databases ===\033[0m"
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d >/dev/null
for c in stash-monolith-db stash-payments-db stash-kyc-db stash-rabbitmq; do
    ok=0
    for i in $(seq 1 40); do
        s="$(docker inspect --format='{{.State.Health.Status}}' "$c" 2>/dev/null || true)"
        if [ "$s" = "healthy" ]; then
            ok=1
            break
        fi
        sleep 2
    done
    if [ "$ok" -ne 1 ]; then
        echo "$c not healthy" >&2
        exit 1
    fi
done

echo -e "\033[36m=== Starting backend services ===\033[0m"
for port in 8080 8081 8082; do
    pids="$(lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    for pid in $pids; do
        kill -9 "$pid" 2>/dev/null || true
    done
done
sleep 2

mkdir -p logs
for m in payments-service kyc-service monolith; do
    nohup ./mvnw -pl "$m" spring-boot:run > "logs/${m}.log" 2>&1 &
    sleep 3
done

wait_http "http://localhost:8081/actuator/health" 180
wait_http "http://localhost:8082/actuator/health" 180
wait_http "http://localhost:8080/actuator/health" 300
echo -e "\033[32mServices up\033[0m"

pass="TestPass123!"
email1="alice.payments.e2e@example.com"
email2="bob.payments.e2e@example.com"

echo -e "\033[36m=== Register users ===\033[0m"
token1="$(register_and_login "$email1" "$pass" "Alice Pay")"
token2="$(register_and_login "$email2" "$pass" "Bob Pay")"

docker exec stash-monolith-db psql -U stash_monolith -d monolith_db -c \
    "UPDATE user_module.users SET kyc_status = 'APPROVED' WHERE email IN ('$email1','$email2');" >/dev/null

# Re-login for fresh JWT with KYC claim
login1_json="$(curl -s -X POST "http://localhost:8080/api/v1/auth/login" -H "Content-Type: application/json" -d "$(jq -n --arg email "$email1" --arg password "$pass" '{email:$email,password:$password}')")"
login2_json="$(curl -s -X POST "http://localhost:8080/api/v1/auth/login" -H "Content-Type: application/json" -d "$(jq -n --arg email "$email2" --arg password "$pass" '{email:$email,password:$password}')")"
token1="$(echo "$login1_json" | jq -r '.access_token')"
token2="$(echo "$login2_json" | jq -r '.access_token')"
user1="$(curl -s "http://localhost:8080/api/v1/users/me" -H "Authorization: Bearer $token1" | jq -r '.id')"
user2="$(curl -s "http://localhost:8080/api/v1/users/me" -H "Authorization: Bearer $token2" | jq -r '.id')"

echo -e "\033[36m=== Wallet deposit (user1) ===\033[0m"
dep_body='{"amount":50000,"payment_method":"MOMO","mobile_number":"0551234987","mobile_provider":"mtn"}'
wallet_dep="$(curl -s -X POST "http://localhost:8080/api/v1/users/me/deposits" \
    -H "Authorization: Bearer $token1" -H "Idempotency-Key: e2e-wallet-1" \
    -H "Content-Type: application/json" -d "$dep_body")"
send_paystack_webhook "$(echo "$wallet_dep" | jq -r '.paystack_reference')" 50000
sleep 2
bal1="$(curl -s "http://localhost:8080/api/v1/users/me/wallet-balance" -H "Authorization: Bearer $token1")"
bal1_pesewas="$(echo "$bal1" | jq -r '.balance_pesewas')"
if [ "$bal1_pesewas" -lt 50000 ]; then
    echo "Wallet deposit failed: balance=$bal1_pesewas" >&2
    exit 1
fi
echo -e "\033[32mWallet balance: $(echo "$bal1" | jq -r '.balance_cedis') GHS\033[0m"

echo -e "\033[36m=== STANDARD vault deposit ===\033[0m"
std_vault="$(curl -s -X POST "http://localhost:8080/api/v1/vaults" \
    -H "Authorization: Bearer $token1" -H "Idempotency-Key: e2e-std-vault" \
    -H "Content-Type: application/json" -d '{"name":"Standard Savings","vault_type":"STANDARD"}')"
std_vault_id="$(echo "$std_vault" | jq -r '.id')"
std_dep="$(curl -s -X POST "http://localhost:8080/api/v1/vaults/$std_vault_id/deposits" \
    -H "Authorization: Bearer $token1" -H "Idempotency-Key: e2e-std-dep" \
    -H "Content-Type: application/json" -d "$dep_body")"
send_paystack_webhook "$(echo "$std_dep" | jq -r '.paystack_reference')" 50000
sleep 2
vaults1="$(curl -s "http://localhost:8080/api/v1/vaults" -H "Authorization: Bearer $token1")"
std_vault_bal="$(echo "$vaults1" | jq -r --arg id "$std_vault_id" '.vaults[] | select(.id == $id) | .balance_pesewas')"
if [ "$std_vault_bal" -lt 50000 ]; then
    echo "Standard vault deposit failed: balance=$std_vault_bal" >&2
    exit 1
fi
echo -e "\033[32mStandard vault deposit OK - balance ${std_vault_bal}p\033[0m"

echo -e "\033[36m=== LOCKED vault deposit (date + amount, BOTH conditions/AND) ===\033[0m"
unlock_at="$(date -u -d '+30 days' '+%Y-%m-%dT%H:%M:%S.000Z' 2>/dev/null || date -u -v+30d '+%Y-%m-%dT%H:%M:%S.000Z')"
locked_vault="$(curl -s -X POST "http://localhost:8080/api/v1/vaults" \
    -H "Authorization: Bearer $token1" -H "Idempotency-Key: e2e-lock-vault" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg unlock_at "$unlock_at" '{name:"Locked Goal", vault_type:"LOCKED", unlock_at:$unlock_at, unlock_amount:100000, unlock_condition_logic:"AND"}')")"
locked_vault_id="$(echo "$locked_vault" | jq -r '.id')"
lock_dep="$(curl -s -X POST "http://localhost:8080/api/v1/vaults/$locked_vault_id/deposits" \
    -H "Authorization: Bearer $token1" -H "Idempotency-Key: e2e-lock-dep" \
    -H "Content-Type: application/json" -d "$dep_body")"
send_paystack_webhook "$(echo "$lock_dep" | jq -r '.paystack_reference')" 50000
sleep 2
vaults1b="$(curl -s "http://localhost:8080/api/v1/vaults" -H "Authorization: Bearer $token1")"
locked_vault_bal="$(echo "$vaults1b" | jq -r --arg id "$locked_vault_id" '.vaults[] | select(.id == $id) | .balance_pesewas')"
if [ "$locked_vault_bal" -lt 50000 ]; then
    echo "Locked(AND) vault deposit failed: balance=$locked_vault_bal" >&2
    exit 1
fi
echo -e "\033[32mLocked vault (AND) deposit OK - balance ${locked_vault_bal}p\033[0m"

echo -e "\033[36m=== LOCKED vault deposit (amount-only condition, user2) ===\033[0m"
# Free tier allows only 1 LOCKED vault per user (SubscriptionPolicy.FREE_LOCKED_VAULT_LIMIT),
# so the single-condition path is exercised under user2.
locked_vault2="$(curl -s -X POST "http://localhost:8080/api/v1/vaults" \
    -H "Authorization: Bearer $token2" -H "Idempotency-Key: e2e-lock-vault-2" \
    -H "Content-Type: application/json" -d '{"name":"Locked Amount Only","vault_type":"LOCKED","unlock_amount":100000}')"
locked_vault2_id="$(echo "$locked_vault2" | jq -r '.id')"
lock_dep2="$(curl -s -X POST "http://localhost:8080/api/v1/vaults/$locked_vault2_id/deposits" \
    -H "Authorization: Bearer $token2" -H "Idempotency-Key: e2e-lock-dep-2" \
    -H "Content-Type: application/json" -d "$dep_body")"
send_paystack_webhook "$(echo "$lock_dep2" | jq -r '.paystack_reference')" 50000
sleep 2
vaults2="$(curl -s "http://localhost:8080/api/v1/vaults" -H "Authorization: Bearer $token2")"
locked_vault2_bal="$(echo "$vaults2" | jq -r --arg id "$locked_vault2_id" '.vaults[] | select(.id == $id) | .balance_pesewas')"
if [ "$locked_vault2_bal" -lt 50000 ]; then
    echo "Locked(amount-only) vault deposit failed: balance=$locked_vault2_bal" >&2
    exit 1
fi
echo -e "\033[32mLocked vault (amount-only) deposit OK - balance ${locked_vault2_bal}p\033[0m"

echo -e "\033[36m=== Peer transfer user1 -> user2 ===\033[0m"
xfer="$(curl -s -X POST "http://localhost:8080/api/v1/transfers" \
    -H "Authorization: Bearer $token1" -H "Idempotency-Key: e2e-xfer-1" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg rid "$user2" '{recipient_user_id:$rid, amount:10000, narrative:"test"}')")"
xfer_status="$(echo "$xfer" | jq -r '.status')"
if [ "$xfer_status" != "COMPLETED" ]; then
    echo "Transfer status $xfer_status" >&2
    exit 1
fi
bal2="$(curl -s "http://localhost:8080/api/v1/users/me/wallet-balance" -H "Authorization: Bearer $token2")"
bal2_pesewas="$(echo "$bal2" | jq -r '.balance_pesewas')"
if [ "$bal2_pesewas" -lt 10000 ]; then
    echo "Recipient balance wrong: $bal2_pesewas" >&2
    exit 1
fi
echo -e "\033[32mTransfer OK - recipient balance $(echo "$bal2" | jq -r '.balance_cedis') GHS\033[0m"

echo ""
echo -e "\033[32m=== ALL PAYMENT E2E CHECKS PASSED ===\033[0m"
