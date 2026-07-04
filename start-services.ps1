# start-services.ps1
# Starts Docker infra (Postgres x4, RabbitMQ, Mailpit) plus all four Spring
# Boot services, each in its own PowerShell window so you can watch each
# one's startup/logs directly.
#
# Run from the repo root:  .\start-services.ps1

$root = $PSScriptRoot

Write-Host "Starting infra (Postgres x4, RabbitMQ, Mailpit)..." -ForegroundColor Cyan

# Remove any containers with our expected names that were started outside this
# compose project (e.g. by a manual docker run or a differently-named compose
# session). docker compose up -d errors with "Conflict" in that case.
$expectedContainers = @("stash-monolith-db","stash-payments-db","stash-kyc-db","stash-audit-db","stash-rabbitmq","stash-mailpit")
foreach ($c in $expectedContainers) {
    $project = docker inspect $c --format '{{index .Config.Labels "com.docker.compose.project"}}' 2>$null
    if ($project -and $project -ne "stash") {
        Write-Host "  Removing orphan container '$c' (compose project: '$project')..." -ForegroundColor Yellow
        docker rm -f $c 2>$null | Out-Null
    }
}

docker compose up -d

$containers = @(
    "stash-monolith-db",
    "stash-payments-db",
    "stash-kyc-db",
    "stash-audit-db",
    "stash-rabbitmq"
)

Write-Host "Waiting for containers to report healthy..." -ForegroundColor Cyan
foreach ($c in $containers) {
    $status = ""
    for ($i = 0; $i -lt 30; $i++) {
        $status = docker inspect --format='{{.State.Health.Status}}' $c 2>$null
        if ($status -eq "healthy") { break }
        Start-Sleep -Seconds 2
    }
    if ($status -eq "healthy") {
        Write-Host "  $c is healthy" -ForegroundColor Green
    } else {
        Write-Host "  WARNING: $c did not report healthy after 60s (status: $status) - check 'docker ps'." -ForegroundColor Yellow
    }
}

$services = @(
    @{ Title = "Monolith (:8080)";         Module = "monolith" },
    @{ Title = "Payments Service (:8081)"; Module = "payments-service" },
    @{ Title = "KYC Service (:8082)";       Module = "kyc-service" },
    @{ Title = "Audit Service (:8083)";     Module = "audit-service" }
)

Write-Host "`nLaunching services..." -ForegroundColor Cyan
foreach ($svc in $services) {
    $title = $svc.Title
    $module = $svc.Module
    $command = "`$host.UI.RawUI.WindowTitle = '$title'; Set-Location '$root'; .\mvnw.cmd -pl $module spring-boot:run"
    Start-Process powershell.exe -ArgumentList @('-NoExit', '-Command', $command) -WorkingDirectory $root
    Write-Host "  Launched $title" -ForegroundColor Green
    Start-Sleep -Seconds 2
}

Write-Host "`nAll four service windows are launching - each takes ~20-50s to fully start." -ForegroundColor Cyan
Write-Host "Swagger UIs once up:"
Write-Host "  Monolith:         http://localhost:8080/swagger-ui.html"
Write-Host "  Payments Service: http://localhost:8081/swagger-ui.html"
Write-Host "  KYC Service:      http://localhost:8082/swagger-ui.html"
Write-Host "  Audit Service:    http://localhost:8083/swagger-ui.html"
Write-Host "`nMobile (Expo) is not started by this script - run 'npx expo start' in mobile/ separately."
