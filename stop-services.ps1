# stop-services.ps1
# Stops the four Spring Boot services started by start-services.ps1, and
# closes the PowerShell window each one was launched in.
#
# Run from the repo root:  .\stop-services.ps1

$services = @(
    @{ Name = "Monolith";         Port = 8080 },
    @{ Name = "Payments Service"; Port = 8081 },
    @{ Name = "KYC Service";      Port = 8082 },
    @{ Name = "Audit Service";    Port = 8083 }
)

# Walks the process tree upward from the java.exe holding the port (via
# mvnw.cmd's cmd.exe) to find the PowerShell window start-services.ps1
# launched it in (Start-Process powershell.exe -NoExit ...), so we can
# close that window too instead of leaving a dead-looking shell behind.
function Get-AncestorWindowProcessId {
    param([int]$ProcessId, [int]$MaxDepth = 6)

    $currentId = $ProcessId
    for ($i = 0; $i -lt $MaxDepth; $i++) {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId = $currentId" -ErrorAction SilentlyContinue
        if (-not $current -or -not $current.ParentProcessId) { return $null }

        $parent = Get-CimInstance Win32_Process -Filter "ProcessId = $($current.ParentProcessId)" -ErrorAction SilentlyContinue
        if (-not $parent) { return $null }

        if ($parent.Name -in @('powershell.exe', 'pwsh.exe')) {
            return $parent.ProcessId
        }
        $currentId = $parent.ProcessId
    }
    return $null
}

$confirm = Read-Host "Stop all Stash services (monolith, payments, kyc, audit) and close their windows? (y/N)"
if ($confirm -ne 'y' -and $confirm -ne 'Y') {
    Write-Host "Aborted - no services were stopped." -ForegroundColor Yellow
    exit
}

$stoppedPids = @{}
$windowPidsToClose = @{}

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

        # Find the launching window before killing the process - once the
        # java.exe is gone, its parent chain can no longer be walked.
        $windowPid = Get-AncestorWindowProcessId -ProcessId $owningProcessId
        if ($windowPid -and $windowPid -ne $PID) {
            $windowPidsToClose[$windowPid] = $true
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

if ($windowPidsToClose.Count -gt 0) {
    Write-Host "`nClosing service windows..." -ForegroundColor Cyan
    foreach ($windowPid in $windowPidsToClose.Keys) {
        try {
            Stop-Process -Id $windowPid -Force -ErrorAction Stop
            Write-Host "  Closed window PID $windowPid" -ForegroundColor Green
        }
        catch {
            Write-Host "  WARNING: Failed to close window PID ${windowPid}: $($_.Exception.Message)" -ForegroundColor Yellow
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
