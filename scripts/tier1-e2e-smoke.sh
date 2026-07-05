#!/usr/bin/env bash
# Tier 1 E2E smoke: signup -> KYC via monolith proxy -> auto approve -> kyc_status APPROVED
set -euo pipefail

MONOLITH_BASE="http://localhost:8080"
KYC_BASE="http://localhost:8082"
SIGNATURE="local-dev-signature-do-not-use-in-prod"

wait_healthy() {
    local url="$1" label="$2" max_sec="${3:-180}"
    local deadline=$(( $(date +%s) + max_sec ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$url" 2>/dev/null)"; then
            if [ "$code" = "200" ]; then
                echo "OK: $label"
                return 0
            fi
        fi
        sleep 3
    done
    echo "$label not healthy at $url" >&2
    exit 1
}

wait_healthy "$MONOLITH_BASE/actuator/health" "monolith"
wait_healthy "$KYC_BASE/actuator/health" "kyc-service"

email="tier1-e2e-$RANDOM@example.com"
password="Str0ng!Pass1"
ghana_card="GHA-000000001-1"

echo "1. Signup $email"
curl -s -X POST "$MONOLITH_BASE/api/v1/auth/signup" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg email "$email" --arg password "$password" '{email:$email,password:$password,displayName:"Tier1 E2E"}')" >/dev/null

echo "2. Fetch verification link from Mailpit"
sleep 2
messages_json="$(curl -s "http://localhost:8025/api/v1/messages?limit=10")"
msg_id="$(echo "$messages_json" | jq -r --arg email "$email" '[.messages[] | select(.To[]?.Address == $email)][0].ID // empty')"
if [ -z "$msg_id" ]; then
    echo "No verification email in Mailpit for $email" >&2
    exit 1
fi
full_json="$(curl -s "http://localhost:8025/api/v1/message/$msg_id")"
text="$(echo "$full_json" | jq -r '.Text')"
verify_url="$(echo "$text" | grep -oE 'https?://[^[:space:]"]+/api/v1/auth/verify-email\?token=[A-Za-z0-9_-]+' | head -n1)"
if [ -z "$verify_url" ]; then
    echo "Could not parse verification URL from email" >&2
    exit 1
fi

echo "3. Verify email"
curl -s "$verify_url" >/dev/null

echo "4. Login"
login_body="$(jq -n --arg email "$email" --arg password "$password" '{email:$email,password:$password,deviceId:"e2e-device",deviceLabel:"E2E"}')"
login_json="$(curl -s -X POST "$MONOLITH_BASE/api/v1/auth/login" -H "Content-Type: application/json" -d "$login_body")"
token="$(echo "$login_json" | jq -r '.access_token')"

kyc_status="$(echo "$login_json" | jq -r '.user.kyc_status')"
if [ "$kyc_status" != "PENDING" ]; then
    echo "Expected PENDING kyc_status after login, got $kyc_status" >&2
    exit 1
fi

echo "5. Create KYC submission via monolith proxy"
kyc_body="$(jq -n --arg gc "$ghana_card" '{ghana_card_number:$gc, full_name:"Tier1 E2E User"}')"
submission_json="$(curl -s -X POST "$MONOLITH_BASE/api/v1/kyc/submissions" \
    -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d "$kyc_body")"
submission_id="$(echo "$submission_json" | jq -r '.id')"
echo "   submissionId=$submission_id status=$(echo "$submission_json" | jq -r '.status')"

echo "6. Confirm three document uploads"
i=0
sha256_hash="$(printf 'a%.0s' $(seq 1 64))"
for doc_type in FRONT_OF_CARD BACK_OF_CARD SELFIE; do
    i=$((i + 1))
    case "$doc_type" in
        FRONT_OF_CARD) key="submissions/$submission_id/front-of-card.jpg" ;;
        BACK_OF_CARD) key="submissions/$submission_id/back-of-card.jpg" ;;
        SELFIE) key="submissions/$submission_id/selfie.jpg" ;;
    esac
    confirm_body="$(jq -n \
        --arg pid "e2e-event-$submission_id-$i" \
        --arg dt "$doc_type" \
        --arg key "$key" \
        --arg hash "$sha256_hash" \
        '{provider_event_id:$pid, document_type:$dt, storage_key:$key, content_type:"image/jpeg", size_bytes:1024, sha256_hash:$hash}')"
    curl -s -X POST "$MONOLITH_BASE/api/v1/kyc/submissions/$submission_id/documents" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -H "X-Storage-Signature: $SIGNATURE" \
        -d "$confirm_body" >/dev/null
done

echo "7. Wait for stub provider auto-approval"
status=""
for n in $(seq 1 30); do
    poll_json="$(curl -s "$MONOLITH_BASE/api/v1/kyc/submissions/$submission_id" -H "Authorization: Bearer $token")"
    status="$(echo "$poll_json" | jq -r '.status')"
    if [ "$status" = "APPROVED" ]; then
        break
    fi
    sleep 2
done
echo "   status=$status"
if [ "$status" != "APPROVED" ]; then
    echo "Expected APPROVED after auto provider; status=$status" >&2
    exit 1
fi

echo "8. Wait for monolith KYC event consumer"
sleep 5

echo "9. Re-login and verify kyc_status APPROVED"
login2_json=""
kyc_status2=""
for n in $(seq 1 15); do
    login2_json="$(curl -s -X POST "$MONOLITH_BASE/api/v1/auth/login" -H "Content-Type: application/json" -d "$login_body")"
    kyc_status2="$(echo "$login2_json" | jq -r '.user.kyc_status')"
    if [ "$kyc_status2" = "APPROVED" ]; then
        break
    fi
    sleep 2
done
if [ "$kyc_status2" != "APPROVED" ]; then
    echo "Expected APPROVED after KYC decision event, got $kyc_status2" >&2
    exit 1
fi

token2="$(echo "$login2_json" | jq -r '.access_token')"
profile_json="$(curl -s "$MONOLITH_BASE/api/v1/users/me" -H "Authorization: Bearer $token2")"
profile_kyc_status="$(echo "$profile_json" | jq -r '.kyc_status')"
if [ "$profile_kyc_status" != "APPROVED" ]; then
    echo "Expected APPROVED on /users/me, got $profile_kyc_status" >&2
    exit 1
fi

echo ""
echo "TIER1 E2E PASSED: KYC decision synced kyc_status=APPROVED for $email"
