# 远桥 · Yuanqiao Relay：发布与安全边界

这份文档说明公开源码、使用者自己的运行环境和现有线上实例之间的边界。GitHub 仓库只是脱敏源码与文档，不是任何人的生产密钥备份。

## 脱敏发布规则

- 仓库不包含真实域名、IP、账号、密码、设备 ID、设备密钥、Bark key、VAPID 私钥、数据库、日志、APK 或签名 keystore。
- README 中的 `example.invalid`、`<placeholder>` 和示例设备 ID 只能作为模板，部署前必须由使用者替换。
- 截图和示意图只展示抽象界面，不展示真实地址、短信正文、验证码、手机号、二维码或个人资料。
- `deploy/secrets.env`、`deploy/initial-credentials.txt`、数据库和日志只存在于使用者自己的受控目录，并保持 Git 忽略。

## 使用者自行生成密钥

每个使用者都应生成自己的值，不要复用仓库作者或其他人的密钥：

1. **设备密钥**：使用系统随机源生成至少 32 字节的 URL-safe 随机值，例如 `python -c "import secrets; print(secrets.token_urlsafe(48))"`。把它只放入服务器的 `DEVICE_SECRETS_JSON` 和 Android 配置页；不要提交 Git。
2. **管理员密码**：设置临时环境变量 `PHONE_MIRROR_ADMIN_PASSWORD`，运行 `python tools/generate_secrets.py` 生成 Argon2id 哈希；使用后立即清除环境变量，避免写入 shell 历史或日志。
3. **Android 签名**：使用自己的 `keytool -genkeypair` 创建 keystore，并把密码放入本机的 `android/keystore/keystore.properties`。签名文件和密码永远不能进入 Git。
4. **Safari Web Push**：在目标服务器生成一对 VAPID 公私钥；私钥只放入 `VAPID_PRIVATE_KEY`，公钥放入 `VAPID_PUBLIC_KEY`，并为 `VAPID_SUBJECT` 设置自己的 `mailto:` 地址。iPhone 必须从 HTTPS 站点添加到主屏幕后再授权通知。
5. **Bark**：Bark 设备码由使用者在自己的 Bark App 中复制，写入目标环境的 `BARK_KEY`；它不是项目生成的密钥，也不应出现在截图或 Issue 中。

## 与线上实例隔离

GitHub 发布、克隆、下载或构建不会修改任何线上 `/data/phone-mirror` 文件、数据库、容器、Caddy 配置、端口或其他项目。线上升级必须另行审批，并遵循备份、健康检查、灰度、验收和回滚流程。

## 发布前验收

```bash
git diff --check
python -m compileall -q server
python -m json.tool contracts/sync-event.schema.json >/dev/null
```

再搜索真实地址、凭据前缀、私钥头、数据库和日志文件。若发现凭据曾经进入 Git 历史，必须先撤销并轮换，再清理历史；仅删除当前文件不够。
