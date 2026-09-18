# 验收状态

## 自动化 / 服务器本地

- [x] 服务器 pytest 全量通过（30 项）
- [x] Android JVM 单测通过（40 项）；`lintRelease` 和 release APK 构建通过
- [x] Android instrumented 测试（`androidTest`）编译通过；未在真机上执行
- [x] 手动同步预请求失败，定位到缺 `android.permission.ACCESS_NETWORK_STATE`；修复前契约测试失败，补上 manifest 权限后通过
- [x] 第二次预请求失败，从 `1.0.3 (4)` 设备的 `network_or_tls_failure` 加零心跳/零同步请求定位：`ApiClient` 用了普通 Moshi，而 Kotlin DTO 是 `@JsonClass(generateAdapter=false)`，序列化在网络 I/O 之前就失败，被映射成了通用错误
- [x] Moshi 回归按 TDD 走：`ApiMoshiFactoryTest` 先报 `Unresolved reference ApiMoshiFactory`；加上带 `KotlinJsonAdapterFactory` 的 `ApiMoshiFactory` 后，`HeartbeatRequest` 能序列化出协议的 snake_case 字段。根目录复跑通过（`BUILD SUCCESSFUL`，1m34s，34 个任务）
- [x] iPhone PWA 测试通过（42 项）；typecheck 和生产构建通过
- [x] 消息和通话最新优先；前台靠 SSE 更新，15 秒对账兜底
- [x] PWA favicon/主屏幕资源已换成新图标
- [x] 布局和 JSON 产物过静态检查
- [x] Compose 用运维密钥渲染，bind/volumes/healthcheck 确认
- [x] 同步后数据库字节里没有测试明文
- [x] API 响应带 `Cache-Control: no-store`

## 服务器集成

- [x] 8084 端口只绑在 Docker 网桥 `172.17.0.1`
- [x] 只用 Compose `up -d --no-deps api` 替换/更新了 `phone-mirror-api-1`，健康且 `RestartCount=0`；其余 18 个容器的容器 ID 和镜像都没动，继续运行
- [x] Caddy 快照/配置校验和现有服务回归检查完成
- [x] 公网 TLS 健康检查和 APK GET 都是 HTTP 200；APK 4,764,223 字节，流式 SHA-256 为 `00a2067e17566f61aead4814f03acb34725c70c0c9b4ee1642327b33a6e8feda`
- [x] 带鉴权的公网 API 验证返回 40 条消息、19 通电话，均为 `latest_first=True`；SSE 返回 HTTP 200、`text/event-stream`、`Cache-Control: no-store`
- [x] 数据库完整性检查 OK，部署保持 CALL 19 / SMS 40 / 已处理事件 59
- [x] Android `1.0.5 (6)` APK 带 v1/v2 签名，证书与 `1.0.4 (5)` 一致，可原地覆盖升级
- [x] API 生产冒烟通过：登录、HMAC、重放、同步、查询、删除和落库加密
- [x] 冒烟测试设备行先备份后精确删除
- [x] 用项目数据库备份 API 完成备份恢复演练

## 真机 / 外部（待执行）

- [ ] 安卓手机 `1.0.5 (6)` 手动同步到服务器、iPhone/Bark 接收、权限/Provider 诊断和真实验证码延迟
- [ ] 编译出的 Android instrumented 测试在真机执行
- [ ] 默认短信角色的资格、开启、掉角色和回退
- [ ] 离线队列、丢 ACK 重放、源端删除、进程死亡和重启
- [ ] iPhone 在所选隐私模式下收 Bark、主屏幕 Web Push
- [ ] 确认通知中心删除的局限
- [ ] 72 小时运行测试
- [ ] 七天无人值守浸泡

每完成一项，把证据补在对应条目旁边。
