# iPhone Setup

Use Safari to open `https://your-phone-mirror.example:8443`, sign in with the administrator credentials stored in `Z:\data\phone-mirror\deploy\initial-credentials.txt`, choose Share → Add to Home Screen, then launch the installed PWA. Messages and calls are displayed newest-first. While the PWA is in the foreground it receives SSE updates and also reconciles every 15 seconds. The PWA is the authoritative current list and does not persist API content in Cache Storage, IndexedDB or localStorage.

The Home Screen icon has been replaced. If iOS still shows the previous icon, remove only the old Phone Mirror Home Screen entry and add the site to the Home Screen again; this does not delete or change server data.

For Bark, copy the device code or full `https://api.day.app/...` address shown on the Bark home page. In Phone Mirror PWA → Settings → Bark, paste it and press “保存并发送 Bark 测试”. The server normalizes and encrypts the key; the browser never reads it back. Privacy mode is on by default: the lock screen displays a generic “收到验证码” notification, while the PWA requires login to reveal content. Selecting full-content mode makes the OTP visible on the lock screen and places notification content outside the mirror's deletion control.
