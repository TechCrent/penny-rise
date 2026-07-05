#!/usr/bin/env bash
# start-mobile-lan.sh
# Starts Expo in LAN mode. Use this when your phone and this machine are
# on the same Wi-Fi network with no client/AP isolation blocking
# device-to-device traffic.
#
# Auto-detects this machine's current LAN IPv4 (via the default route
# interface) and updates mobile/.env if it has drifted (Wi-Fi IPs change
# between sessions/networks). Also clears any leftover mobile/.env.local
# tunnel override, since that takes precedence over .env and would
# otherwise silently keep pointing at an old ngrok URL.
#
# Run from the repo root:  ./start-mobile-lan.sh

set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mobile_dir="$root/mobile"
env_path="$mobile_dir/.env"
env_local_path="$mobile_dir/.env.local"

if [ -f "$env_local_path" ]; then
    echo -e "\033[36mRemoving leftover tunnel override at mobile/.env.local...\033[0m"
    rm -f "$env_local_path"
fi

current_ip=""
if command -v ip >/dev/null 2>&1; then
    current_ip="$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' || true)"
fi
if [ -z "$current_ip" ] && command -v ifconfig >/dev/null 2>&1; then
    current_ip="$(ifconfig 2>/dev/null | grep -A1 -E '^(wlan0|en0|wlp)' | grep -oE 'inet [0-9.]+' | awk '{print $2}' | head -n1)"
fi

if [ -z "$current_ip" ]; then
    echo -e "\033[33mWARNING: could not detect a LAN IPv4 address. Is Wi-Fi connected?\033[0m"
    echo -e "\033[33mFalling back to whatever is currently in mobile/.env.\033[0m"
elif [ -f "$env_path" ]; then
    existing_line="$(grep -E 'EXPO_PUBLIC_API_BASE_URL=http://[0-9.]+:[0-9]+' "$env_path" || true)"
    if [ -n "$existing_line" ]; then
        existing_ip="$(echo "$existing_line" | sed -E 's#.*http://([0-9.]+):([0-9]+).*#\1#')"
        port="$(echo "$existing_line" | sed -E 's#.*http://([0-9.]+):([0-9]+).*#\2#')"
        if [ "$existing_ip" != "$current_ip" ]; then
            echo -e "\033[36mmobile/.env has a stale IP ($existing_ip) - updating to current LAN IP ($current_ip)...\033[0m"
            sed -i -E "s#EXPO_PUBLIC_API_BASE_URL=http://[0-9.]+:[0-9]+#EXPO_PUBLIC_API_BASE_URL=http://${current_ip}:${port}#" "$env_path"
        else
            echo -e "\033[32mmobile/.env already has the current LAN IP ($current_ip).\033[0m"
        fi
    fi
fi

echo -e "\n\033[36mLAN API URL (mobile/.env):\033[0m"
grep "EXPO_PUBLIC_API_BASE_URL" "$env_path" || true

(
    cd "$mobile_dir" && exec pnpm start
) &

echo -e "\n\033[36mLaunched Expo (LAN) in the background (PID $!).\033[0m"
echo "Make sure your phone is on the same Wi-Fi network as this machine."
