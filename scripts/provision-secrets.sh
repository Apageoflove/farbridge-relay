#!/bin/bash
set -euo pipefail
ROOT=/data/phone-mirror
PY=$ROOT/.venv/bin/python
if [ -f "$ROOT/deploy/secrets.env" ]; then
  # 兼容早期生成的未转义 Argon2 哈希；只改哈希行且绝不回显密钥。
  if grep -q '^ADMIN_PASSWORD_HASH=\$argon2id\$' "$ROOT/deploy/secrets.env"; then
    sed -i '/^ADMIN_PASSWORD_HASH=/ s/\$/$$/g' "$ROOT/deploy/secrets.env"
    chmod 600 "$ROOT/deploy/secrets.env"
    echo "existing secrets.env repaired for Compose interpolation"
  else
    echo "secrets.env already exists"
  fi
  exit 0
fi
ENC=$($PY -c "import secrets; print(secrets.token_urlsafe(32))")
SESS=$($PY -c "import secrets; print(secrets.token_urlsafe(32))")
DEV=$($PY -c "import secrets; print(secrets.token_urlsafe(32))")
ADMIN_PASS=$($PY -c "import secrets; print(secrets.token_urlsafe(15))")
HASH=$($PY -c "import argon2,sys; print(argon2.PasswordHasher().hash(sys.argv[1]))" "$ADMIN_PASS")
# Compose 会解释 env_file 中的美元符号；双写后容器实际收到原始 Argon2 哈希。
HASH_COMPOSE=$(printf '%s' "$HASH" | sed 's/\$/$$/g')
cat > "$ROOT/deploy/secrets.env" <<EOF
APP_ENV=production
DATABASE_URL=sqlite:////data/phone-mirror/data/phone-mirror.db
ENCRYPTION_KEY=$ENC
SESSION_SECRET=$SESS
ADMIN_USERNAME=admin
ADMIN_PASSWORD_HASH=$HASH_COMPOSE
DEVICE_SECRETS_JSON={"android-device-01":"$DEV"}
SECURE_COOKIE=true
BARK_ENABLED=false
BARK_BASE_URL=https://api.day.app
BARK_KEY=
BARK_PRIVACY_MODE=true
VAPID_PUBLIC_KEY=
VAPID_PRIVATE_KEY=
VAPID_SUBJECT=
DEGRADED_MINUTES=60
OFFLINE_MINUTES=120
EOF
chmod 600 "$ROOT/deploy/secrets.env"
cat > "$ROOT/deploy/initial-credentials.txt" <<EOF
Phone Mirror 初始凭据（生成时间: $(date -Is)）
=============================================
PWA 登录用户名: admin
PWA 登录密码:   $ADMIN_PASS
设备 ID:        android-device-01
设备密钥(粘贴到安卓 App): $DEV

说明:
- 本文件权限 600，且已 git-ignore，仅存在于服务器项目目录内。
- 登录密码可在 secrets.env 中用 ADMIN_PASSWORD_HASH 替换（argon2id）后 docker compose restart api。
- 设备密钥与 secrets.env 中 DEVICE_SECRETS_JSON 保持一致，改动需两边同步。
- 启用 Bark 推送: 编辑 secrets.env 设 BARK_ENABLED=true 并填 BARK_KEY（Bark App 内复制），
  BARK_PRIVACY_MODE=true 时推送不含短信正文，只提醒有验证码；设为 false 则直接推验证码。
EOF
chmod 600 "$ROOT/deploy/initial-credentials.txt"
echo "secrets.env + initial-credentials.txt created (600, git-ignored)"
