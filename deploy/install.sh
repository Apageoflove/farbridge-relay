#!/usr/bin/env bash
# Prepare only Phone Mirror-owned runtime directories and validate prerequisites.
set -euo pipefail
ROOT=/data/phone-mirror
test "$(cd "$(dirname "$0")/.." && pwd -P)" = "$ROOT" || { echo 'wrong project root' >&2; exit 1; }
command -v docker >/dev/null || { echo 'docker is required' >&2; exit 1; }
docker compose version >/dev/null
install -d -m 700 "$ROOT/data" "$ROOT/logs" "$ROOT/backups" "$ROOT/.cache"
# The image uses an unprivileged fixed uid; ownership is limited to this project.
chown -R 65532:65532 "$ROOT/data" "$ROOT/logs" "$ROOT/backups" "$ROOT/.cache"
echo 'runtime directories ready; create deploy/secrets.env manually with mode 600'
