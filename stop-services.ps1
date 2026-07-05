# stop-services.ps1
# Stops the four Spring Boot services started by start-services.ps1.
#
# Run from the repo root:  .\stop-services.ps1

$services = @(
    @{ Name = "Monolith";         Port = 8080 },
    @{ Name = "Payments Service"; Port = 8081 },
    @{ Name = "KYC Service";      Port = 8082 },
    @{ Name = "Audit Service";    Port = 8083 }
)

$stoppedPids = @{}

Write-Host "Stopping Spring Boot services..." -ForegroundColor Cyan
foreach ($svc in $services) {
    $name = $svc.Name
    $port = $svc.Port

    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique

    if (-not $connections) {
        Write-Host "  $name port $port is already free" -ForegroundColor Yellow
        continue
    }

    foreach ($owningProcessId in $connections) {
        if ($stoppedPids.ContainsKey($owningProcessId)) {
            continue
        }

        try {
            $process = Get-Process -Id $owningProcessId -ErrorAction Stop
            Write-Host "  Stopping $name on port ${port} (PID $owningProcessId, $($process.ProcessName))" -ForegroundColor Green
            Stop-Process -Id $owningProcessId -Force -ErrorAction Stop
            $stoppedPids[$owningProcessId] = $true
        }
        catch {
            Write-Host "  WARNING: Failed to stop PID $owningProcessId on port ${port}: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}

Start-Sleep -Seconds 2

Write-Host "`nVerifying service ports are free..." -ForegroundColor Cyan
foreach ($svc in $services) {
    $port = $svc.Port
    $name = $svc.Name
    $stillListening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($stillListening) {
        Write-Host "  WARNING: $name is still listening on port $port" -ForegroundColor Yellow
    }
    else {
        Write-Host "  $name port $port is free" -ForegroundColor Green
    }
}

Write-Host "`nDone." -ForegroundColor Cyan
