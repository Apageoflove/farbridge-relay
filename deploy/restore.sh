#!/usr/bin/env bash
# Restore an explicitly named backup only while the Phone Mirror service is stopped.
set -euo pipefail
ROOT=/data/phone-mirror
source_path=${1:-}
[[ "$source_path" == "$ROOT/backups/"*.db ]] && test -f "$source_path" || { echo 'provide a backup under /data/phone-mirror/backups' >&2; exit 2; }
if docker compose -p phone-mirror -f "$ROOT/deploy/compose.yml" ps --status running -q 2>/dev/null | grep -q .; then echo 'stop Phone Mirror before restore' >&2; exit 1; fi
PY=$ROOT/.venv/bin/python
test -x "$PY" || { echo 'project Python runtime missing' >&2; exit 1; }
mkdir -p "$ROOT/data"
"$PY" - "$source_path" "$ROOT/data/phone-mirror.db" <<'PY'
"""Restore through SQLite's backup API and reject a corrupt source."""
import sqlite3
import sys

with sqlite3.connect(sys.argv[1]) as source:
    if source.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
        raise SystemExit("source backup integrity check failed")
    with sqlite3.connect(sys.argv[2]) as target:
        source.backup(target)
        if target.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise SystemExit("restored database integrity check failed")
PY
chmod 600 "$ROOT/data/phone-mirror.db"
