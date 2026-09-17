#!/usr/bin/env bash
# Reject layout, secret, bind and volume mistakes before deployment.
set -euo pipefail
ROOT=/data/phone-mirror
cd "$ROOT"
required=(.gitignore README.md contracts/sync-event.schema.json server/pyproject.toml deploy/compose.yml deploy/env.example deploy/caddy/phone-mirror.caddy deploy/install.sh deploy/start.sh deploy/stop.sh deploy/status.sh deploy/health.sh deploy/backup.sh deploy/restore.sh deploy/cleanup.sh)
for path in "${required[@]}"; do test -f "$path" || { echo "missing: $path" >&2; exit 1; }; done
grep -q '^deploy/secrets.env$' .gitignore
if test -e deploy/secrets.env; then
  test "$(stat -c %a deploy/secrets.env)" = 600 || { echo 'deploy/secrets.env must be mode 600' >&2; exit 1; }
  git check-ignore -q deploy/secrets.env || { echo 'deploy/secrets.env is not ignored' >&2; exit 1; }
  ! grep -Eq 'GENERATE_|OPTIONAL_PRIVATE_KEY|replace_me|changeme' deploy/secrets.env || { echo 'placeholder found in deploy/secrets.env' >&2; exit 1; }
fi
test -z "$(git check-ignore android/app/src/main/java/com/zj/phonemirror/data/db/AppDatabase.kt 2>/dev/null || true)" || { echo 'Android database source is incorrectly ignored' >&2; exit 1; }
grep -q '172.17.0.1:8084:8084' deploy/compose.yml
! grep -Eq '(^|[[:space:]])(0\.0\.0\.0|127\.0\.0\.1):8084' deploy/compose.yml
for path in /data/phone-mirror/data /data/phone-mirror/logs /data/phone-mirror/backups /data/phone-mirror/.cache; do grep -Fq "$path" deploy/compose.yml || { echo "missing volume $path" >&2; exit 1; }; done
grep -Fq '/data/phone-mirror/deploy/secrets.env' deploy/compose.yml
grep -q 'healthcheck:' deploy/compose.yml
grep -qx '!web/' .dockerignore
if grep -IlE 'replace_me|changeme' README.md deploy/compose.yml deploy/caddy/phone-mirror.caddy contracts/fixtures/*.json | grep -q .; then
  echo 'placeholder value found in a runtime artifact' >&2
  exit 1
fi
python3 -m json.tool contracts/sync-event.schema.json >/dev/null
for fixture in contracts/fixtures/*.json; do python3 -m json.tool "$fixture" >/dev/null; done
echo 'PASS: project state is isolated under /data/phone-mirror'
