# start-mobile-lan.ps1
# Starts Expo in LAN mode. Use this when your phone and this machine are
# on the same Wi-Fi network with no client/AP isolation blocking
# device-to-device traffic.
#
# Auto-detects this machine's current Wi-Fi IPv4 and updates
# mobile/.env if it has drifted (Wi-Fi IPs change between sessions/networks).
# Also clears any leftover mobile/.env.local tunnel override, since that
# takes precedence over .env and would otherwise silently keep pointing
# at an old ngrok URL.
#
# Run from the repo root:  .\start-mobile-lan.ps1

$root = $PSScriptRoot
$mobileDir = Join-Path $root "mobile"
$envPath = Join-Path $mobileDir ".env"
$envLocalPath = Join-Path $mobileDir ".env.local"

if (Test-Path $envLocalPath) {
    Write-Host "Removing leftover tunnel override at mobile\.env.local..." -ForegroundColor Cyan
    Remove-Item $envLocalPath
}

$currentIp = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias "Wi-Fi" -ErrorAction SilentlyContinue).IPAddress
if (-not $currentIp) {
    Write-Host "WARNING: could not detect a Wi-Fi IPv4 address. Is Wi-Fi connected?" -ForegroundColor Yellow
    Write-Host "Falling back to whatever is currently in mobile\.env." -ForegroundColor Yellow
} else {
    $envContent = Get-Content $envPath -Raw
    if ($envContent -match 'EXPO_PUBLIC_API_BASE_URL=http://([\d.]+):(\d+)') {
        $existingIp = $matches[1]
        $port = $matches[2]
        if ($existingIp -ne $currentIp) {
            Write-Host "mobile\.env has a stale IP ($existingIp) - updating to current Wi-Fi IP ($currentIp)..." -ForegroundColor Cyan
            $newContent = $envContent -replace 'EXPO_PUBLIC_API_BASE_URL=http://[\d.]+:\d+', "EXPO_PUBLIC_API_BASE_URL=http://$currentIp`:$port"
            Set-Content -Path $envPath -Value $newContent -NoNewline
        } else {
            Write-Host "mobile\.env already has the current Wi-Fi IP ($currentIp)." -ForegroundColor Green
        }
    }
}

Write-Host "`nLAN API URL (mobile\.env):" -ForegroundColor Cyan
Select-String -Path $envPath -Pattern "EXPO_PUBLIC_API_BASE_URL"

$command = "`$host.UI.RawUI.WindowTitle = 'Expo (LAN)'; Set-Location '$mobileDir'; pnpm start"
Start-Process powershell.exe -ArgumentList @('-NoExit', '-Command', $command) -WorkingDirectory $mobileDir

Write-Host "`nLaunched 'Expo (LAN)' window." -ForegroundColor Cyan
Write-Host "Make sure your phone is on the same Wi-Fi network as this machine."
