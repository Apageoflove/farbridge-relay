# 远桥 · FarBridge Relay 运维操作手册

> 适用范围：新的自托管环境、Android 采集端与 iPhone Safari/PWA 的首次部署、验收、日常排障和密钥轮换。<br>
> 本文所有尖括号内容都必须由操作者自行替换；不要把真实值提交到 GitHub、截图、聊天记录或日志。

## 0. 先理解三端链路

Android 手机只负责读取短信、通话记录和设备状态，并把事件送到自己的服务器。服务器负责认证、去重、存储和推送。iPhone 通过 Safari/PWA 登录服务器查看记录；Web Push 是主提醒，Bark 是备用提醒。

> iPhone 客户端是 Safari/PWA，不提供 IPA，不需要越狱或侧载。

## 1. 部署前准备

1. 准备一台由你控制的 Linux 服务器、Docker 与 Docker Compose、可用的 HTTPS 域名和有效证书。
2. 在服务器创建本项目独占目录，例如 <PROJECT_ROOT>。不要把项目数据混入其他容器或共享卷。
3. 将源码放进项目目录。公开模板默认使用 /data/phone-mirror；如果你使用不同目录，先审查 compose、脚本和挂载路径，统一替换后再部署。
4. 确认服务器时间正确、磁盘可写、网络可访问，且 8084 仅由本项目自己的反向代理链路使用。
5. 从模板创建真正的密钥文件：

    cp deploy/env.example deploy/secrets.env
    chmod 600 deploy/secrets.env

6. 确认 secrets.env 被 Git 忽略：

    git check-ignore deploy/secrets.env

## 2. 生成服务端全部密钥

在受控终端进入项目根目录执行。以下命令会打印随机值；复制到 deploy/secrets.env 后，清理终端滚屏或历史记录。

### 2.1 生成 ENCRYPTION_KEY、SESSION_SECRET、设备密钥

    python -m pip install -e "./server" -i https://pypi.tuna.tsinghua.edu.cn/simple
    python tools/generate_secrets.py

脚本会输出：

- ENCRYPTION_KEY：用于服务端加密敏感设置。
- SESSION_SECRET：用于浏览器会话。
- DEVICE_SECRET：Android 与服务器之间的设备签名密钥。

把输出的前三项填入 secrets.env。每一台 Android 手机必须有自己独立的 DEVICE_SECRET。

### 2.2 生成管理员密码哈希

先在当前终端临时设置管理员密码，不要把密码直接写进 README 或脚本。

Linux/macOS：

    read -r -s PHONE_MIRROR_ADMIN_PASSWORD
    export PHONE_MIRROR_ADMIN_PASSWORD
    python tools/generate_secrets.py
    unset PHONE_MIRROR_ADMIN_PASSWORD

Windows PowerShell（仅在隔离终端使用；完成后关闭该终端）：

    $secure = Read-Host -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:PHONE_MIRROR_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr); python tools/generate_secrets.py }
    finally { Remove-Item Env:PHONE_MIRROR_ADMIN_PASSWORD -ErrorAction SilentlyContinue; [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }

最终只把脚本输出的 ADMIN_PASSWORD_HASH 写入 secrets.env；绝不要保存明文密码。

### 2.3 配置 DEVICE_SECRETS_JSON

选择一个不含真实姓名或手机号的设备 ID，例如 android-home-01。将设备 ID 与刚才的设备密钥填成一行 JSON：

    DEVICE_SECRETS_JSON={"android-home-01":"<DEVICE_SECRET>"}

Android 端填入的“设备 ID”和“设备密钥”必须和这行完全一致，大小写也必须一致。

### 2.4 生成 Safari Web Push 的 VAPID 密钥

    python -m pip install py-vapid -i https://pypi.tuna.tsinghua.edu.cn/simple
    vapid --gen
    vapid --applicationServerKey

将生成的值填入：

    VAPID_PUBLIC_KEY=<VAPID_PUBLIC_KEY>
    VAPID_PRIVATE_KEY=<VAPID_PRIVATE_KEY>
    VAPID_SUBJECT=mailto:<OPERATOR_EMAIL>

VAPID_PRIVATE_KEY 只能存在于 secrets.env。若以后轮换 VAPID 密钥，iPhone 需要重新订阅 Web Push。

### 2.5 配置 Bark（可选备用通道）

1. 在 iPhone 安装 Bark。
2. 从 Bark 首页复制“设备码”或完整的 api.day.app 地址。
3. 在 secrets.env 先设置：

    BARK_ENABLED=true
    BARK_BASE_URL=https://api.day.app
    BARK_KEY=<BARK_DEVICE_KEY>
    BARK_PRIVACY_MODE=true

4. 推荐保留隐私模式 true：锁屏只提示收到验证码/新消息，不展示短信正文。

### 2.6 secrets.env 最终检查

至少确认以下项不再是模板占位符：

    ENCRYPTION_KEY=<GENERATED_VALUE>
    SESSION_SECRET=<GENERATED_VALUE>
    ADMIN_USERNAME=<ADMIN_USERNAME>
    ADMIN_PASSWORD_HASH=<ARGON2ID_HASH>
    DEVICE_SECRETS_JSON={"android-home-01":"<DEVICE_SECRET>"}
    SECURE_COOKIE=true
    BARK_ENABLED=<true_or_false>
    VAPID_PUBLIC_KEY=<VAPID_PUBLIC_KEY>
    VAPID_PRIVATE_KEY=<VAPID_PRIVATE_KEY>
    VAPID_SUBJECT=mailto:<OPERATOR_EMAIL>

## 3. 启动与健康检查

先只操作本项目目录；不要为了部署远桥而重启 Docker 或其他容器。

    bash deploy/install.sh
    bash deploy/start.sh
    bash deploy/status.sh
    bash deploy/health.sh

健康接口应返回 status 为 ok，database 为 ok。若失败，先修复本项目配置，不要修改不相关服务。

## 4. Android 采集端配置

1. 从 GitHub Releases 下载 APK，或使用自己的 keystore 构建签名 APK。
2. 在 Android 打开“远桥采集端 / FarBridge Agent”。
3. 填写：
   - 服务器地址：<YOUR_HTTPS_URL>
   - 设备 ID：android-home-01
   - 设备密钥：与 DEVICE_SECRETS_JSON 对应的 <DEVICE_SECRET>
4. 点击“保存并开始同步”。
5. 点击“授予短信/通话权限”，允许短信、通话记录、电话状态及应用提示的权限。
6. 在系统设置中为此应用关闭电池优化，允许后台活动和自启动；将应用在最近任务中锁定。
7. 保持常驻通知、网络和供电。点击“立即同步一次”。

成功标准：应用状态显示服务器连接成功/同步完成；待同步事件最终回到 0；iPhone 的设备页能看到最新心跳、电量和权限状态。

## 5. iPhone Safari/PWA 与 Web Push

1. 用 iPhone Safari 打开 <YOUR_HTTPS_URL>，使用 ADMIN_USERNAME 和管理员密码登录。
2. 点击 Safari 的“分享”按钮，选择“添加到主屏幕”。
3. 从主屏幕的“远桥消息 / FarBridge Inbox”图标重新打开，不要只在 Safari 标签页中测试推送。
4. 打开“设置”，点击“开启 Web Push”，在系统弹窗中允许通知。
5. 在 iPhone 系统“设置 → 通知”中允许此 PWA 的横幅、声音和标记。
6. 在远桥设置页选择 Bark，粘贴自己的 Bark 设备码或完整地址，点击“保存并发送 Bark 测试”。

如果 Web Push 未到达，Bark 可以作为备用通道；若两者都无提醒，请先确认服务器健康和 Android 同步状态。

## 6. 端到端验收清单

按顺序验证，每完成一项再进行下一项：

1. Android 端显示服务器已连接、短信权限 OK、通话记录权限 OK。
2. 给 Android SIM 卡发送一条普通短信；iPhone 消息页刷新后，新记录应在最上面。
3. 点击该短信，确认能展开完整原文。
4. 发一条验证码短信，确认在验证码有效期内显示；检查 Web Push 或 Bark 是否提醒。
5. 在 iPhone 对一条短信执行收藏、置顶、复制和列表删除；删除只影响服务器镜像，不删除 Android 原始短信。
6. 给 Android 手机拨打一通电话；iPhone 通话页刷新后应显示记录，号码可复制。
7. Android 临时断网后接收一条短信，再恢复网络；确认事件补传且只出现一次。
8. 刷新 iPhone 页面，确认最新状态不被旧缓存覆盖。

## 7. 常见问题

### Android 提示网络或证书连接失败

检查服务器地址是否为 HTTPS、手机时间是否正确、证书链是否有效，以及反向代理是否把请求转到本项目 API。不要用 HTTP 或把密钥放在 URL 中。

### 手动同步失败

依次检查：短信/通话权限、Android 网络、设备 ID、设备密钥、服务健康接口。设备 ID 或密钥只要一处不一致，服务器就会拒绝同步。

### 短信或通话不更新

检查 Android 是否被系统限制后台运行；检查待同步事件数量；确认手机具备供电和网络；再查看 iPhone 页面是否刷新到最新列表。不要以旧缓存页面判断同步状态。

### Web Push 没有弹窗

必须同时满足：HTTPS、从 Safari 添加到主屏幕、在 PWA 内点击过“开启 Web Push”、iOS 通知已允许、服务端配置了正确的 VAPID 公私钥。VAPID 轮换后，删除旧主屏幕图标并重新添加 PWA。

### Bark 测试失败

确认 BARK_ENABLED=true、设备码来自自己的 Bark App、BARK_BASE_URL 可访问。Bark key 保存后不会回显是正常的；如怀疑泄露，在 Bark 端重新生成并替换。

## 8. 安全与回滚

1. 密钥、管理员密码、Bark key、VAPID 私钥泄露时，立即轮换，不要只删除当前文件。
2. 轮换 DEVICE_SECRET 时，要同时更新服务器和 Android；否则同步会中断。
3. 回滚前先记录版本、健康状态和本项目容器状态。
4. 只恢复本项目自己的配置、数据库、备份与镜像；不要删除或重启无关项目。
5. 回滚后重跑本手册第 6 节的端到端验收。
