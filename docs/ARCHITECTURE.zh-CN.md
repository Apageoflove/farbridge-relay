# 架构

安卓手机的系统短信和通话记录是唯一数据源。Android 端在本地维护一份 Room 镜像加事务性 outbox，按至少一次的语义发送 HMAC 签名事件，并定期用系统 Provider 的完整快照做对账。服务端是单进程 FastAPI，按 `(device_id, event_id)` 幂等去重，镜像行的唯一身份是 `(device_id, entity_type, source_id)`。

SQLite 开 WAL 模式、启用外键。短信和通话正文、Web Push 订阅都在应用层用 AES-256-GCM 加密。iPhone PWA 查询的是当前镜像，SSE 只推事件类型和标识符。Bark 和 Web Push 只是通知渠道，不算权威历史。

删除的链路是：Android 发 DELETE → 服务端镜像移除 → PWA 刷新或 SSE 移除。已经弹出的系统通知收不回来。
