#!/usr/bin/env bash
# Remove only expired Phone Mirror backups and rotated logs.
set -euo pipefail
ROOT=/data/phone-mirror
test -d "$ROOT" || exit 1
find "$ROOT/backups" -type f -name 'phone-mirror-*.db' -mtime +7 -delete 2>/dev/null || true
find "$ROOT/logs" -type f -name '*.log.*' -mtime +14 -delete 2>/dev/null || true
