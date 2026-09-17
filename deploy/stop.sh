#!/usr/bin/env bash
# Stop only the isolated Phone Mirror Compose project.
set -euo pipefail
exec /data/phone-mirror/deploy/manage.sh stop
