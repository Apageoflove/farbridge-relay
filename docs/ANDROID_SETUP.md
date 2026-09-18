# Android Phone Setup

1. Open `https://your-phone-mirror.example:8443/downloads/phone-mirror.apk`, install Android version `1.0.5 (6)`, and verify its size is `4,764,223` bytes and SHA-256 is `00a2067e17566f61aead4814f03acb34725c70c0c9b4ee1642327b33a6e8feda` when a verifier is available. The APK has verified v1/v2 signatures and the `com.zj.phonemirror` signing certificate matches `1.0.4 (5)`, allowing an in-place/cover upgrade without uninstalling the app or clearing the existing configuration.
2. Grant SMS, call-log and phone-state permissions in system Settings.
3. Open the app once after the upgrade and confirm the persistent “远桥正在运行” notification appears. In system settings, allow autostart and background activity, disable battery optimization for the app, and lock it in Recents where the vendor ROM provides that control.
4. Configure server URL `https://your-phone-mirror.example:8443`; copy the device ID and device secret from `deploy/initial-credentials.txt` on the server. Never use HTTP in production.
5. After upgrading to `1.0.5 (6)`, tap “立即同步一次” again; do not treat installation alone as acceptance. Continue only when Android shows “服务器连接成功，同步已完成” and the matching server/iPhone/Bark evidence is recorded. Then run diagnostics: ordinary SMS, real OTP, incoming/missed/outgoing call, offline queue, deletion, reboot and permission-loss checks.
6. Keep reliable power plus battery-health safeguards, stable Wi-Fi/mobile data and sufficient carrier balance.

Default SMS mode must remain off until its eligibility, role loss, multipart SMS, send/delete behavior and OTP latency are physically validated on this exact Android model/OS build.
