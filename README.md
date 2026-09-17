# 远桥 · FarBridge Relay

> 让重要的短信与通话记录，跨设备抵达你手上。<br>
> Keep important SMS and call records available across your devices.

远桥（**FarBridge Relay**）是一个可自托管的 Android → Relay Server → iPhone Safari/PWA 通信镜像项目。Android 端负责读取并安全提交短信、通话事件；中继服务负责认证、去重、短期保留和通知；iPhone 端通过 Safari 或“添加到主屏幕”的 PWA 查看最新记录。

**FarBridge Relay** is a self-hosted Android → relay server → iPhone Safari/PWA mirror. The Android client submits SMS and call events, the relay authenticates and deduplicates them, and the iPhone client displays the newest records with optional Web Push or Bark notifications.

![远桥 / FarBridge Relay 架构示意图](docs/assets/relay-overview.png)

> **脱敏边界 / Sanitization boundary**<br>
> 本仓库是可审计的公开源码副本，不包含真实域名、IP、账号、密码、设备密钥、Bark key、VAPID 私钥、数据库、日志、APK 或个人信息。线上 \`/data/phone-mirror\` 与本仓库完全隔离。<br>
> This repository is a sanitized, auditable source copy. It contains no production URL, IP, credential, device key, Bark key, VAPID private key, database, log, APK, or personal data. The live \`/data/phone-mirror\` deployment is completely separate.

## 1.0.9 UI preview / 界面预览

![iPhone / Safari PWA 消息页预览](docs/assets/iphone-ui-preview.png)

<p><sub>iPhone / Safari PWA：最新消息优先、单条短信展开、收藏、置顶与刷新。图中内容为虚构脱敏数据；页面外部没有四角方框，也不是某台真实手机的截图。<br>iPhone / Safari PWA: newest-first messages, expandable SMS, favorites, pinning, and refresh. Fictional sanitized data only; the preview is a rounded page panel without a square outer frame.</sub></p>

## 项目身份 / Project identity

| 用途 | 中文 ID | English ID |
| --- | --- | --- |
| 产品名称 / Product | 远桥 | FarBridge Relay |
| GitHub 仓库 slug | \`yuanqiao-relay\` | \`yuanqiao-relay\` |
| Android 应用显示名 | 远桥采集端 | FarBridge Agent |
| iPhone PWA 显示名 | 远桥消息 | FarBridge Inbox |

仓库 slug 为了保持 URL 稳定仍使用 \`yuanqiao-relay\`；产品展示、应用标题和文档同时使用“远桥 / FarBridge Relay”。

The repository slug remains \`yuanqiao-relay\` for URL stability. Product-facing names use both **远桥** and **FarBridge Relay**.

## 功能 / Features

- Android SMS and call events are queued locally when offline and retried after connectivity returns.
- Server-side event IDs provide deduplication; authentication, retention, and device state are auditable.
- iPhone messages and calls are separated, newest first; a message can be expanded to reveal its full original text.
- iPhone list actions include favorite, pin, copy, refresh, and list-only deletion. Server retention is bounded; favorited items require explicit operator deletion in the UI.
- Optional Web Push and Bark notifications. Privacy mode can suppress SMS bodies on lock-screen notifications.
- Secrets, databases, logs, and deployment configuration stay outside the public source tree.

## GitHub Releases / 发布下载

下载入口：**[Releases](https://github.com/Apageoflove/yuanqiao-relay/releases)**

- **Android / 安卓：** 从 Release 下载 \`farbridge-relay-android-v1.0.9.apk\`，或用自己的 keystore 构建；覆盖升级必须保留同一套 keystore。
- **iPhone / iOS：** 本项目不伪造 IPA。iOS 客户端是 Safari/PWA：打开自己的 HTTPS 地址，登录后“分享 → 添加到主屏幕”。Release 提供 \`farbridge-relay-ios-safari-setup.md\` 离线说明。
- **Checksums / 校验：** Release 的 \`SHA256SUMS.txt\` 用于核对 APK 完整性。

- **Android:** Download \`farbridge-relay-android-v1.0.9.apk\` from Releases, or build it with your own keystore. Keep that keystore for in-place upgrades.
- **iPhone/iOS:** There is intentionally no fabricated IPA. Open your HTTPS URL in Safari, sign in, then choose **Share → Add to Home Screen**. The Release includes \`farbridge-relay-ios-safari-setup.md\`.
- **Checksums:** Use \`SHA256SUMS.txt\` to verify the APK.

## iPhone Safari/PWA setup / iPhone 使用步骤

1. 用自己的 HTTPS 地址替换 \`<YOUR_HTTPS_URL>\` 并在 Safari 打开；不要公开真实地址。 / Replace \`<YOUR_HTTPS_URL>\` with your HTTPS endpoint and keep the real URL private.
2. 使用目标服务器生成的管理员账号登录，不要使用仓库示例值。 / Sign in with credentials generated in your target environment, not repository examples.
3. Safari“分享”→“添加到主屏幕”，从图标启动；iOS Web Push 通常必须通过 HTTPS 与主屏幕 PWA。 / Use **Share → Add to Home Screen**; iOS Web Push requires HTTPS and should be enabled from the installed PWA.
4. 在“设置 → Web Push”开启，并在 iOS 通知设置中允许“远桥消息 / FarBridge Inbox”的提醒、声音、标记。 / Enable **Settings → Web Push**, then allow alerts, sounds, and badges for the app.
5. 可选 Bark：在 Bark App 复制设备码/完整地址，填入项目“设置 → Bark”，保存后发送测试；建议保持隐私模式。 / Optional Bark: copy its device code/full URL into **Settings → Bark**, save, and send a test; keep privacy mode enabled.
6. 页面不刷新时下拉刷新；若 Service Worker 过旧，删除主屏幕图标、清 Safari 网站数据，再重新添加。 / If refresh is stale, remove the icon, clear Safari website data, and add the PWA again.

## 自己生成密钥 / Generate your own keys

所有结果只写入目标服务器的 \`deploy/secrets.env\` 或密码管理器，不提交 GitHub。 / Store all outputs only in controlled \`deploy/secrets.env\` or a password manager; never commit them.

### 管理员密码哈希 / Admin password hash

\`\`\`bash
cp deploy/env.example deploy/secrets.env
chmod 600 deploy/secrets.env
python -m pip install -e './server' -i https://pypi.tuna.tsinghua.edu.cn/simple
read -r -s PHONE_MIRROR_ADMIN_PASSWORD
export PHONE_MIRROR_ADMIN_PASSWORD
python tools/generate_secrets.py
unset PHONE_MIRROR_ADMIN_PASSWORD
\`\`\`

把输出的 Argon2id \`ADMIN_PASSWORD_HASH\` 写入受控配置；不要把明文密码写入 shell 历史。 / Store the generated Argon2id hash in controlled config; never place plaintext passwords in shell history.

### 设备密钥、Android 签名、VAPID、Bark / Device, signing, VAPID and Bark keys

\`\`\`bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
mkdir -p android/keystore
keytool -genkeypair -v -keystore android/keystore/release.keystore -alias farbridge-relay -keyalg RSA -keysize 2048 -validity 10950
python -m pip install py-vapid -i https://pypi.tuna.tsinghua.edu.cn/simple
vapid --gen
vapid --applicationServerKey
\`\`\`

为每台设备生成不同的随机设备密钥（至少 32 个随机字符），让服务端 \`DEVICE_SECRETS_JSON\` 与 Android 完全一致；VAPID 私钥只留在服务端；Bark key 从 Bark App 复制并保持 \`BARK_PRIVACY_MODE=true\`。/ Generate a different device secret (at least 32 random characters) per device, keep \`DEVICE_SECRETS_JSON\` identical on server and Android, store VAPID private keys server-side only, and copy Bark keys from the Bark app with privacy mode enabled.

## 本地构建 / Build locally

\`\`\`bash
git clone https://github.com/Apageoflove/yuanqiao-relay.git
cd yuanqiao-relay
cd android
gradle testDebugUnitTest
gradle assembleRelease
\`\`\`

服务端测试：\`python -m pip install -e './server[test]' -i https://pypi.tuna.tsinghua.edu.cn/simple && cd server && python -m pytest -q\`。布局检查：\`bash scripts/verify-layout.sh\`。仓库不携带 Gradle Wrapper，请使用与 Android Gradle Plugin 8.9.1 兼容的 Gradle。

See \`web/package.json\` for PWA commands. Read [architecture](docs/ARCHITECTURE.md), [Android permissions](docs/ANDROID_PERMISSIONS.md), [iPhone setup](docs/IPHONE_SETUP.md), [acceptance](docs/ACCEPTANCE.md), [security](docs/SECURITY.md), and [release safety](docs/RELEASE_SAFETY.md) before production use.

## 生产边界 / Production boundary

- GitHub is a sanitized source/release distribution, not a backup of live SMS, calls, databases, or secrets.
- Publishing must not modify \`/data/phone-mirror\`, containers, reverse-proxy configuration, or unrelated services.
- Back up, health-check, stage, smoke-test, and keep a rollback version before production changes; never run an unreviewed \`git pull\` in a live deployment.
- SMS and verification codes are sensitive. Redact bodies, phone numbers, device IDs, tokens, and URLs before sharing screenshots or logs.

## 许可证 / License

本仓库当前未预置许可证；公开使用前请补充许可证和第三方依赖声明。<br>
No license is bundled yet; add a license and third-party notices before public redistribution.
