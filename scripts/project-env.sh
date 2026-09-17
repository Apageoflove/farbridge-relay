#!/usr/bin/env bash
# Establish the only permitted project/cache/runtime roots.
set -euo pipefail
export PHONE_MIRROR_ROOT=/data/phone-mirror
export PIP_CACHE_DIR=/data/phone-mirror/.cache/pip
export GRADLE_USER_HOME=/data/phone-mirror/.cache/gradle
export npm_config_cache=/data/phone-mirror/.cache/npm
export XDG_CACHE_HOME=/data/phone-mirror/.cache/xdg
test "$(pwd -P)" = "$PHONE_MIRROR_ROOT" || { echo "Run from $PHONE_MIRROR_ROOT" >&2; return 1 2>/dev/null || exit 1; }
