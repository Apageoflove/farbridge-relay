#!/usr/bin/env bash
# Show only Phone Mirror container state.
set -euo pipefail
exec /data/phone-mirror/deploy/manage.sh status
