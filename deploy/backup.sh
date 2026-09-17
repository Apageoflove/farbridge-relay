#!/usr/bin/env bash
# Create a short-lived privacy-sensitive SQLite backup under the project root.
set -euo pipefail
ROOT=/data/phone-mirror
DB=$ROOT/data/phone-mirror.db
mkdir -p "$ROOT/backups"
test -f "$DB" || { echo 'database missing' >&2; exit 1; }
PY=$ROOT/.venv/bin/python
test -x "$PY" || { echo 'project Python runtime missing' >&2; exit 1; }
target="$ROOT/backups/phone-mirror-$(date -u +%Y%m%dT%H%M%SZ).db"
"$PY" - "$DB" "$target" <<'PY'
"""Create a consistent online SQLite backup without a host sqlite3 CLI."""
import sqlite3
import sys

with sqlite3.connect(sys.argv[1]) as source, sqlite3.connect(sys.argv[2]) as target:
    source.backup(target)
    if target.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
        raise SystemExit("backup integrity check failed")
PY
chmod 600 "$target"
find "$ROOT/backups" -type f -name 'phone-mirror-*.db' -mtime +7 -delete
echo "$target"
