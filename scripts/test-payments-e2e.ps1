# End-to-end payment flow verification (local dev)
# Usage: .\scripts\test-payments-e2e.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

function Load-Env {
    Get-Content ".\.env" | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') { Set-Item -Path "env:$($matches[1])" -Value $matches[2] }
    }
}

function Wait-Http($url, $timeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
            if ($r.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 3
    }
    throw "Timeout waiting for $url"
}

function Hmac-Paystack($secret, [byte[]]$body) {
    $hmac = [System.Security.Cryptography.HMACSHA512]::new([Text.Encoding]::UTF8.GetBytes($secret))
    return -join ($hmac.ComputeHash($body) | ForEach-Object { $_.ToString("x2") })
}

function Send-PaystackWebhook($paystackRef, $amountPesewas) {
    $eventId = [guid]::NewGuid().ToString()
    $payload = (@{
        event = "charge.success"
        id    = $eventId
        data  = @{
            reference = $paystackRef
            amount    = $amountPesewas
            status    = "success"
        }
    } | ConvertTo-Json -Compress)
    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
    $sig = Hmac-Paystack $env:PAYSTACK_WEBHOOK_SECRET $bytes
    Invoke-WebRequest -Uri "http://localhost:8081/webhooks/paystack" -Method Post `
        -Headers @{ "x-paystack-signature" = $sig; "Content-Type" = "application/json"; "Idempotency-Key" = "webhook-$paystackRef" } `
        -Body $bytes -UseBasicParsing | Out-Null
}

function Get-VerifyLink($email) {
    $msgs = Invoke-RestMethod -Uri "http://localhost:8025/api/v1/messages" -TimeoutSec 10
    $msg = $msgs.messages | Where-Object { $_.To[0].Address -eq $email } | Select-Object -First 1
    if (-not $msg) { throw "No verification email for $email" }
    $full = Invoke-RestMethod -Uri "http://localhost:8025/api/v1/message/$($msg.ID)" -TimeoutSec 10
    if ($full.Text -match '(http://localhost:8080/api/v1/auth/verify-email\?token=[^\s"]+)') {
        return $Matches[1]
    }
    throw "Verify link not found in email for $email"
}

function Register-And-Login($email, $password, $name) {
    $signup = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/signup" -Method Post `
        -ContentType "application/json" `
        -Body (@{ email = $email; password = $password; display_name = $name } | ConvertTo-Json)
    Start-Sleep -Seconds 1
    $link = Get-VerifyLink $email
    Invoke-WebRequest -Uri $link -UseBasicParsing | Out-Null
    $login = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post `
        -ContentType "application/json" `
        -Body (@{ email = $email; password = $password } | ConvertTo-Json)
    return $login.access_token
}

function Approve-Kyc($token) {
    docker exec stash-monolith-db psql -U stash_monolith -d monolith_db -c `
        "UPDATE user_module.users SET kyc_status = 'APPROVED', email_verified_at = NOW() WHERE id = (SELECT id FROM user_module.users ORDER BY created_at DESC LIMIT 1);" | Out-Null
    # caller should pass user-specific - we'll do by email in main
}

Load-Env

Write-Host "=== Resetting databases ===" -ForegroundColor Cyan
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
docker compose down -v | Out-Null
docker compose up -d | Out-Null
$ErrorActionPreference = $prevEap
foreach ($c in @("stash-monolith-db","stash-payments-db","stash-kyc-db","stash-rabbitmq")) {
    $ok = $false
    for ($i = 0; $i -lt 40; $i++) {
        $s = docker inspect --format='{{.State.Health.Status}}' $c 2>$null
        if ($s -eq "healthy") { $ok = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ok) { throw "$c not healthy" }
}

Write-Host "=== Starting backend services ===" -ForegroundColor Cyan
Get-NetTCPConnection -LocalPort 8080,8081,8082 -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
    Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

$modules = @("payments-service","kyc-service","monolith")
foreach ($m in $modules) {
    $cmd = "Get-Content '$root\.env' | ForEach-Object { if (`$_ -match '^([^#=]+)=(.*)$') { Set-Item -Path env:`$(`$matches[1]) -Value `$matches[2] } }; Set-Location '$root'; .\mvnw.cmd -pl $m spring-boot:run"
    Start-Process powershell.exe -ArgumentList @('-NoExit', '-Command', $cmd) -WorkingDirectory $root
    Start-Sleep -Seconds 3
}

Wait-Http "http://localhost:8081/actuator/health" 180
Wait-Http "http://localhost:8082/actuator/health" 180
Wait-Http "http://localhost:8080/actuator/health" 300
Write-Host "Services up" -ForegroundColor Green

$pass = "TestPass123!"
$email1 = "alice.payments.e2e@example.com"
$email2 = "bob.payments.e2e@example.com"

Write-Host "=== Register users ===" -ForegroundColor Cyan
$token1 = Register-And-Login $email1 $pass "Alice Pay"
$token2 = Register-And-Login $email2 $pass "Bob Pay"

docker exec stash-monolith-db psql -U stash_monolith -d monolith_db -c `
    "UPDATE user_module.users SET kyc_status = 'APPROVED' WHERE email IN ('$email1','$email2');" | Out-Null

# Re-login for fresh JWT with KYC claim
$login1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -ContentType "application/json" -Body (@{ email=$email1; password=$pass } | ConvertTo-Json)
$login2 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -ContentType "application/json" -Body (@{ email=$email2; password=$pass } | ConvertTo-Json)
$token1 = $login1.access_token
$token2 = $login2.access_token
$user1 = (Invoke-RestMethod -Uri "http://localhost:8080/api/v1/users/me" -Headers @{ Authorization = "Bearer $token1" }).id
$user2 = (Invoke-RestMethod -Uri "http://localhost:8080/api/v1/users/me" -Headers @{ Authorization = "Bearer $token2" }).id

Write-Host "=== Wallet deposit (user1) ===" -ForegroundColor Cyan
$depBody = @{ amount = 50000; payment_method = "MOMO"; mobile_number = "0551234987"; mobile_provider = "mtn" } | ConvertTo-Json
$walletDep = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/users/me/deposits" -Method Post `
    -Headers @{ Authorization = "Bearer $token1"; "Idempotency-Key" = "e2e-wallet-1" } `
    -ContentType "application/json" -Body $depBody
Send-PaystackWebhook $walletDep.paystack_reference 50000
Start-Sleep -Seconds 2
$bal1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/users/me/wallet-balance" -Headers @{ Authorization = "Bearer $token1" }
if ($bal1.balance_pesewas -lt 50000) { throw "Wallet deposit failed: balance=$($bal1.balance_pesewas)" }
Write-Host "Wallet balance: $($bal1.balance_cedis) GHS" -ForegroundColor Green

Write-Host "=== STANDARD vault deposit ===" -ForegroundColor Cyan
$stdVault = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults" -Method Post `
    -Headers @{ Authorization = "Bearer $token1"; "Idempotency-Key" = "e2e-std-vault" } `
    -ContentType "application/json" -Body (@{ name = "Standard Savings"; vault_type = "STANDARD" } | ConvertTo-Json)
$stdDep = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults/$($stdVault.id)/deposits" -Method Post `
    -Headers @{ Authorization = "Bearer $token1"; "Idempotency-Key" = "e2e-std-dep" } `
    -ContentType "application/json" -Body $depBody
Send-PaystackWebhook $stdDep.paystack_reference 50000
Start-Sleep -Seconds 2
$vaults1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults" -Headers @{ Authorization = "Bearer $token1" }
$stdVaultAfter = $vaults1.vaults | Where-Object { $_.id -eq $stdVault.id }
if ($stdVaultAfter.balance_pesewas -lt 50000) { throw "Standard vault deposit failed: balance=$($stdVaultAfter.balance_pesewas)" }
Write-Host "Standard vault deposit OK - balance $($stdVaultAfter.balance_pesewas)p" -ForegroundColor Green

Write-Host "=== LOCKED vault deposit (date + amount, BOTH conditions/AND) ===" -ForegroundColor Cyan
$unlockAt = (Get-Date).AddDays(30).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
$lockedVault = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults" -Method Post `
    -Headers @{ Authorization = "Bearer $token1"; "Idempotency-Key" = "e2e-lock-vault" } `
    -ContentType "application/json" -Body (@{
        name = "Locked Goal"; vault_type = "LOCKED"
        unlock_at = $unlockAt; unlock_amount = 100000; unlock_condition_logic = "AND"
    } | ConvertTo-Json)
$lockDep = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults/$($lockedVault.id)/deposits" -Method Post `
    -Headers @{ Authorization = "Bearer $token1"; "Idempotency-Key" = "e2e-lock-dep" } `
    -ContentType "application/json" -Body $depBody
Send-PaystackWebhook $lockDep.paystack_reference 50000
Start-Sleep -Seconds 2
$vaults1b = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults" -Headers @{ Authorization = "Bearer $token1" }
$lockedVaultAfter = $vaults1b.vaults | Where-Object { $_.id -eq $lockedVault.id }
if ($lockedVaultAfter.balance_pesewas -lt 50000) { throw "Locked(AND) vault deposit failed: balance=$($lockedVaultAfter.balance_pesewas)" }
Write-Host "Locked vault (AND) deposit OK - balance $($lockedVaultAfter.balance_pesewas)p" -ForegroundColor Green

Write-Host "=== LOCKED vault deposit (amount-only condition, user2) ===" -ForegroundColor Cyan
# Free tier allows only 1 LOCKED vault per user (SubscriptionPolicy.FREE_LOCKED_VAULT_LIMIT),
# so the single-condition path is exercised under user2.
$lockedVault2 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults" -Method Post `
    -Headers @{ Authorization = "Bearer $token2"; "Idempotency-Key" = "e2e-lock-vault-2" } `
    -ContentType "application/json" -Body (@{ name = "Locked Amount Only"; vault_type = "LOCKED"; unlock_amount = 100000 } | ConvertTo-Json)
$lockDep2 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults/$($lockedVault2.id)/deposits" -Method Post `
    -Headers @{ Authorization = "Bearer $token2"; "Idempotency-Key" = "e2e-lock-dep-2" } `
    -ContentType "application/json" -Body $depBody
Send-PaystackWebhook $lockDep2.paystack_reference 50000
Start-Sleep -Seconds 2
$vaults2 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/vaults" -Headers @{ Authorization = "Bearer $token2" }
$lockedVault2After = $vaults2.vaults | Where-Object { $_.id -eq $lockedVault2.id }
if ($lockedVault2After.balance_pesewas -lt 50000) { throw "Locked(amount-only) vault deposit failed: balance=$($lockedVault2After.balance_pesewas)" }
Write-Host "Locked vault (amount-only) deposit OK - balance $($lockedVault2After.balance_pesewas)p" -ForegroundColor Green

Write-Host "=== Peer transfer user1 -> user2 ===" -ForegroundColor Cyan
$xfer = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transfers" -Method Post `
    -Headers @{ Authorization = "Bearer $token1"; "Idempotency-Key" = "e2e-xfer-1" } `
    -ContentType "application/json" -Body (@{ recipient_user_id = $user2; amount = 10000; narrative = "test" } | ConvertTo-Json)
if ($xfer.status -ne "COMPLETED") { throw "Transfer status $($xfer.status)" }
$bal2 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/users/me/wallet-balance" -Headers @{ Authorization = "Bearer $token2" }
if ($bal2.balance_pesewas -lt 10000) { throw "Recipient balance wrong: $($bal2.balance_pesewas)" }
Write-Host "Transfer OK - recipient balance $($bal2.balance_cedis) GHS" -ForegroundColor Green

Write-Host ""
Write-Host "=== ALL PAYMENT E2E CHECKS PASSED ===" -ForegroundColor Green
