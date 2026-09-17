#!/usr/bin/env bash
# Safely validate and operate only the Phone Mirror Compose project.
set -euo pipefail
ROOT=/data/phone-mirror
cd "$ROOT"
action=${1:-status}
test -f deploy/secrets.env || { echo 'Create deploy/secrets.env first' >&2; exit 1; }
test "$(stat -c %a deploy/secrets.env)" = 600 || { echo 'secrets.env must be mode 600' >&2; exit 1; }
if grep -Eq 'GENERATE_|OPTIONAL_PRIVATE_KEY|replace_me|changeme' deploy/secrets.env; then echo 'placeholder secret refused' >&2; exit 1; fi
if [[ "$action" == start ]]; then
  ! ss -H -ltn 'sport = :8084' | grep -q . || { echo 'port 8084 is occupied' >&2; exit 1; }
  docker compose -p phone-mirror -f deploy/compose.yml config -q
  docker compose -p phone-mirror -f deploy/compose.yml up -d --build
elif [[ "$action" == stop ]]; then
  docker compose -p phone-mirror -f deploy/compose.yml down
elif [[ "$action" == status ]]; then
  docker compose -p phone-mirror -f deploy/compose.yml ps
elif [[ "$action" == health ]]; then
  curl --fail --silent --show-error --max-time 5 http://172.17.0.1:8084/healthz
else
  echo 'usage: deploy/manage.sh {start|stop|status|health}' >&2; exit 2
fi
