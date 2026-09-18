# iPhone 设置

用 Safari 打开 `https://your-phone-mirror.example:8443`，用管理员账号登录（初始凭据在服务器上的 `deploy/initial-credentials.txt`），点“分享 → 添加到主屏幕”，之后从主屏幕图标进入。消息和通话按最新优先排列；PWA 在前台时走 SSE 实时更新，每 15 秒再做一次兜底对账。列表以 PWA 当前显示为准，API 内容不会写进 Cache Storage、IndexedDB 或 localStorage。

主屏幕图标已经换新。如果 iOS 还显示旧图标，只删掉旧的 Phone Mirror 主屏幕条目、重新添加一次即可，服务器数据不受影响。

Bark 的接法：在 Bark 首页复制设备码或完整的 `https://api.day.app/...` 地址，进远桥 PWA 的“设置 → Bark”粘贴，点“保存并发送 Bark 测试”。服务器会把 key 归一化并加密保存，浏览器端不会回显。隐私模式默认开启：锁屏只显示“收到验证码”这样的通用提示，要看内容得进 PWA 登录。如果切到全文模式，验证码会直接出现在锁屏上，通知内容也就不受镜像删除的控制了。
