#!/usr/bin/env bash
# start-services.sh
# Starts Docker infra (Postgres x4, RabbitMQ, Mailpit) plus all four Spring
# Boot services, each in its own terminal/background process with logs
# written to logs/*.log so you can tail each one's startup/logs directly.
#
# Run from the repo root:  ./start-services.sh

set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root"

echo -e "\033[36mStarting infra (Postgres x4, RabbitMQ, Mailpit)...\033[0m"

# Remove any containers with our expected names that were started outside this
# compose project (e.g. by a manual docker run or a differently-named compose
# session). docker compose up -d errors with "Conflict" in that case.
expected_containers=(stash-monolith-db stash-payments-db stash-kyc-db stash-audit-db stash-rabbitmq stash-mailpit)
for c in "${expected_containers[@]}"; do
    project="$(docker inspect "$c" --format '{{index .Config.Labels "com.docker.compose.project"}}' 2>/dev/null || true)"
    if [ -n "$project" ] && [ "$project" != "stash" ]; then
        echo -e "\033[33m  Removing orphan container '$c' (compose project: '$project')...\033[0m"
        docker rm -f "$c" >/dev/null 2>&1 || true
    fi
done

docker compose up -d

containers=(stash-monolith-db stash-payments-db stash-kyc-db stash-audit-db stash-rabbitmq)

echo -e "\033[36mWaiting for containers to report healthy...\033[0m"
for c in "${containers[@]}"; do
    status=""
    for i in $(seq 1 30); do
        status="$(docker inspect --format='{{.State.Health.Status}}' "$c" 2>/dev/null || true)"
        if [ "$status" = "healthy" ]; then
            break
        fi
        sleep 2
    done
    if [ "$status" = "healthy" ]; then
        echo -e "\033[32m  $c is healthy\033[0m"
    else
        echo -e "\033[33m  WARNING: $c did not report healthy after 60s (status: $status) - check 'docker ps'.\033[0m"
    fi
done

mkdir -p logs

declare -a titles=("Monolith (:8080)" "Payments Service (:8081)" "KYC Service (:8082)" "Audit Service (:8083)")
declare -a modules=("monolith" "payments-service" "kyc-service" "audit-service")

echo -e "\n\033[36mLaunching services...\033[0m"
for i in "${!modules[@]}"; do
    title="${titles[$i]}"
    module="${modules[$i]}"
    logfile="logs/${module}.log"
    nohup ./mvnw -pl "$module" spring-boot:run > "$logfile" 2>&1 &
    echo -e "\033[32m  Launched $title (PID $!, log: $logfile)\033[0m"
    sleep 2
done

echo -e "\n\033[36mAll four services are launching - each takes ~20-50s to fully start.\033[0m"
echo "Tail logs with: tail -f logs/monolith.log (or payments-service.log, kyc-service.log, audit-service.log)"
echo "Swagger UIs once up:"
echo "  Monolith:         http://localhost:8080/swagger-ui.html"
echo "  Payments Service: http://localhost:8081/swagger-ui.html"
echo "  KYC Service:      http://localhost:8082/swagger-ui.html"
echo "  Audit Service:    http://localhost:8083/swagger-ui.html"
echo -e "\nMobile (Expo) is not started by this script - run 'npx expo start' in mobile/ separately."
