<div align="center">

# 远桥 · FarBridge Relay

跨设备接收重要短信与通话记录

[简体中文](README.md) · [English](README.en.md) · [Releases](https://github.com/Apageoflove/farbridge-relay/releases)

</div>

> **脱敏公开仓库**：不包含真实域名、IP、账号、密码、设备密钥、Bark/VAPID 私钥、数据库、日志或个人信息。线上环境与本仓库隔离。

## 概览

远桥是一个自托管的 Android → Relay Server → iPhone Safari/PWA 通信镜像。Android 端安全上送短信、通话事件和状态；服务端完成认证、去重、短期保留及通知；iPhone 端查看最新消息与通话记录。

![架构示意图](docs/assets/relay-overview.png)

## iPhone / Safari PWA

<img src="docs/assets/iphone-ui-preview.png" alt="FarBridge Relay iPhone Safari PWA 消息列表预览" width="360">

<sub>脱敏界面预览：最新优先、短信展开、收藏、置顶与刷新。没有设备外框或四角表格边框。</sub>

## 功能

- Android 离线事件队列与网络恢复重试
- 服务端认证、事件去重、状态审计和短期保留
- iPhone 消息/通话分栏；最新优先；短信全文展开
- 收藏、置顶、复制、列表删除与手动刷新
- 可选 Safari Web Push 与 Bark 备用提醒，支持锁屏隐私模式

## 下载与使用

在 [Releases](https://github.com/Apageoflove/farbridge-relay/releases) 获取：

- farbridge-relay-android-v1.0.9.apk：Android 安装包
- farbridge-relay-ios-safari-setup.md：iPhone Safari/PWA 离线设置说明
- SHA256SUMS.txt：APK 完整性校验

iPhone 不提供 IPA，也不需要侧载：用 Safari 打开自己的 HTTPS 地址，登录后点“分享 → 添加到主屏幕”；再在项目设置中启用 Web Push。需要备用提醒时，在 Bark App 复制设备码或完整地址，粘贴到项目“设置 → Bark”，保存并发送测试。

## 自己生成密钥

真实密钥只写入目标服务器的 deploy/secrets.env 或密码管理器，绝不提交 GitHub。

- 设备密钥：python -c "import secrets; print(secrets.token_urlsafe(48))"
- Android 签名：使用 keytool 创建本机 keystore，别上传 keystore 或密码。
- Safari Web Push：安装 py-vapid 后执行 vapid --gen 和 vapid --applicationServerKey。
- Bark：从自己的 Bark App 复制设备码或完整地址。

详细资料：

- [架构](docs/ARCHITECTURE.md)
- [Android 权限](docs/ANDROID_PERMISSIONS.md)
- [iPhone 设置](docs/IPHONE_SETUP.md)
- [从零部署与密钥操作手册](docs/OPERATOR_GUIDE.zh-CN.md)
- [验收清单](docs/ACCEPTANCE.md)
- [安全与发布边界](docs/RELEASE_SAFETY.md)

## 本地构建

克隆 https://github.com/Apageoflove/farbridge-relay.git 后，进入 android 目录，执行 gradle testDebugUnitTest 和 gradle assembleRelease。本仓库不携带 Gradle Wrapper；请使用与 Android Gradle Plugin 8.9.1 兼容的 Gradle。

## 生产边界

发布或下载 GitHub 内容不会修改线上服务。生产变更应先备份、健康检查、灰度验证并保留回滚版本；分享截图或日志前，务必去除短信正文、手机号、验证码、设备 ID、Token 和服务器地址。
