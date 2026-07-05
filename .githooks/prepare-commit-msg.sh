#!/usr/bin/env bash
# Strip Cursor agent attribution from commit messages (Linux/macOS).
set -euo pipefail

commit_msg_path="${1:-}"
if [ -z "$commit_msg_path" ]; then
    exit 0
fi

sed -i \
    -e '/^Co-authored-by: Cursor <cursoragent@cursor\.com>$/d' \
    -e '/^Made with Cursor$/d' \
    -e '/^Made-with: Cursor$/d' \
    "$commit_msg_path"

# Trim trailing blank lines, mirroring the PowerShell version's TrimEnd().
printf '%s' "$(cat "$commit_msg_path")" > "$commit_msg_path"
