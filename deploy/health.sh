#!/usr/bin/env bash
# Probe the internal Phone Mirror health endpoint with a bounded timeout.
set -euo pipefail
exec /data/phone-mirror/deploy/manage.sh health
