# Tier 1 E2E smoke: signup -> KYC via monolith proxy -> auto approve -> kyc_status APPROVED
$ErrorActionPreference = "Stop"

$MonolithBase = "http://localhost:8080"
$KycBase = "http://localhost:8082"
$AdminToken = "local-dev-admin-token-not-for-production"
$Signature = "local-dev-signature-do-not-use-in-prod"

function Wait-Healthy($url, $label, $maxSec = 180) {
    $deadline = (Get-Date).AddSeconds($maxSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 15
            if ($r.StatusCode -eq 200) {
                Write-Host "OK: $label"
                return
            }
        } catch { }
        Start-Sleep -Seconds 3
    }
    throw "$label not healthy at $url"
}

Wait-Healthy "$MonolithBase/actuator/health" "monolith"
Wait-Healthy "$KycBase/actuator/health" "kyc-service"

$email = "tier1-e2e-$(Get-Random)@example.com"
$password = "Str0ng!Pass1"
$ghanaCard = "GHA-000000001-1"

Write-Host "1. Signup $email"
$signupBody = @{ email = $email; password = $password; displayName = "Tier1 E2E"; terms_accepted = $true } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$MonolithBase/api/v1/auth/signup" -ContentType "application/json" -Body $signupBody | Out-Null

Write-Host "2. Fetch verification link from Mailpit"
Start-Sleep -Seconds 2
$messages = Invoke-RestMethod -Uri "http://localhost:8025/api/v1/messages?limit=10"
$msg = $messages.messages | Where-Object {
    ($_.To | ForEach-Object { $_.Address }) -contains $email
} | Select-Object -First 1
if (-not $msg) { throw "No verification email in Mailpit for $email" }
$full = Invoke-RestMethod -Uri "http://localhost:8025/api/v1/message/$($msg.ID)"
$text = $full.Text
if ($text -match '(https?://[^\s"]+/api/v1/auth/verify-email\?token=[A-Za-z0-9_-]+)') {
    $verifyUrl = $Matches[1]
} else {
    throw "Could not parse verification URL from email"
}

Write-Host "3. Verify email"
Invoke-WebRequest -Uri $verifyUrl -UseBasicParsing | Out-Null

Write-Host "4. Login"
$loginBody = @{ email = $email; password = $password; deviceId = "e2e-device"; deviceLabel = "E2E" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$MonolithBase/api/v1/auth/login" -ContentType "application/json" -Body $loginBody
$token = $login.access_token
$headers = @{ Authorization = "Bearer $token" }

if ($login.user.kyc_status -ne "PENDING") {
    throw "Expected PENDING kyc_status after login, got $($login.user.kyc_status)"
}

Write-Host "5. Create KYC submission via monolith proxy"
$kycBody = @{ ghana_card_number = $ghanaCard; full_name = "Tier1 E2E User" } | ConvertTo-Json
$submission = Invoke-RestMethod -Method Post -Uri "$MonolithBase/api/v1/kyc/submissions" -ContentType "application/json" -Headers $headers -Body $kycBody
$submissionId = $submission.id
Write-Host "   submissionId=$submissionId status=$($submission.status)"

Write-Host "6. Confirm three document uploads"
$docTypes = @(
    @{ type = "FRONT_OF_CARD"; key = "submissions/$submissionId/front-of-card.jpg" },
    @{ type = "BACK_OF_CARD"; key = "submissions/$submissionId/back-of-card.jpg" },
    @{ type = "SELFIE"; key = "submissions/$submissionId/selfie.jpg" }
)
$i = 0
foreach ($doc in $docTypes) {
    $i++
    $confirmBody = @{
        provider_event_id = "e2e-event-$submissionId-$i"
        document_type = $doc.type
        storage_key = $doc.key
        content_type = "image/jpeg"
        size_bytes = 1024
        sha256_hash = ("a" * 64)
    } | ConvertTo-Json
    $docHeaders = $headers.Clone()
    $docHeaders["X-Storage-Signature"] = $Signature
    Invoke-RestMethod -Method Post -Uri "$MonolithBase/api/v1/kyc/submissions/$submissionId/documents" -ContentType "application/json" -Headers $docHeaders -Body $confirmBody | Out-Null
}

Write-Host "7. Wait for stub provider auto-approval"
$status = $null
for ($n = 0; $n -lt 30; $n++) {
    $poll = Invoke-RestMethod -Uri "$MonolithBase/api/v1/kyc/submissions/$submissionId" -Headers $headers
    $status = $poll.status
    if ($status -eq "APPROVED") { break }
    Start-Sleep -Seconds 2
}
Write-Host "   status=$status"
if ($status -ne "APPROVED") {
    throw "Expected APPROVED after auto provider; status=$status"
}

Write-Host "8. Wait for monolith KYC event consumer"
Start-Sleep -Seconds 5

Write-Host "9. Re-login and verify kyc_status APPROVED"
$login2 = $null
for ($n = 0; $n -lt 15; $n++) {
    $login2 = Invoke-RestMethod -Method Post -Uri "$MonolithBase/api/v1/auth/login" -ContentType "application/json" -Body $loginBody
    if ($login2.user.kyc_status -eq "APPROVED") { break }
    Start-Sleep -Seconds 2
}
if ($login2.user.kyc_status -ne "APPROVED") {
    throw "Expected APPROVED after KYC decision event, got $($login2.user.kyc_status)"
}

$profile = Invoke-RestMethod -Uri "$MonolithBase/api/v1/users/me" -Headers @{ Authorization = "Bearer $($login2.access_token)" }
if ($profile.kyc_status -ne "APPROVED") {
    throw "Expected APPROVED on /users/me, got $($profile.kyc_status)"
}

Write-Host ""
Write-Host "TIER1 E2E PASSED: KYC decision synced kyc_status=APPROVED for $email"
