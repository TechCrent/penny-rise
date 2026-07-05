#!/usr/bin/env bash
# stop-services.sh
# Stops the four Spring Boot services started by start-services.sh.
#
# Run from the repo root:  ./stop-services.sh

set -uo pipefail

declare -a names=("Monolith" "Payments Service" "KYC Service" "Audit Service")
declare -a ports=(8080 8081 8082 8083)

declare -A stopped_pids=()

echo -e "\033[36mStopping Spring Boot services...\033[0m"
for i in "${!ports[@]}"; do
    name="${names[$i]}"
    port="${ports[$i]}"

    pids="$(lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null || true)"

    if [ -z "$pids" ]; then
        echo -e "\033[33m  $name port $port is already free\033[0m"
        continue
    fi

    for pid in $pids; do
        if [ -n "${stopped_pids[$pid]:-}" ]; then
            continue
        fi

        pname="$(ps -p "$pid" -o comm= 2>/dev/null || echo unknown)"
        echo -e "\033[32m  Stopping $name on port $port (PID $pid, $pname)\033[0m"
        if kill -9 "$pid" 2>/dev/null; then
            stopped_pids[$pid]=1
        else
            echo -e "\033[33m  WARNING: Failed to stop PID $pid on port $port\033[0m"
        fi
    done
done

sleep 2

echo -e "\n\033[36mVerifying service ports are free...\033[0m"
for i in "${!ports[@]}"; do
    name="${names[$i]}"
    port="${ports[$i]}"
    still_listening="$(lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [ -n "$still_listening" ]; then
        echo -e "\033[33m  WARNING: $name is still listening on port $port\033[0m"
    else
        echo -e "\033[32m  $name port $port is free\033[0m"
    fi
done

echo -e "\n\033[36mDone.\033[0m"
