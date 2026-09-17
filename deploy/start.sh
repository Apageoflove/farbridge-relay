#!/usr/bin/env bash
# Start only the isolated Phone Mirror Compose project after safety checks.
set -euo pipefail
exec /data/phone-mirror/deploy/manage.sh start
