<div align="center">

# FarBridge Relay · 远桥

Keep important SMS and call records available across devices

[简体中文](README.md) · [English](README.en.md) · [Releases](https://github.com/Apageoflove/farbridge-relay/releases)

</div>

## Overview

FarBridge Relay is a self-hosted Android → relay server → iPhone Safari/PWA communication mirror. The Android client submits SMS, call, and status events; the server authenticates, deduplicates, retains short-lived records, and sends notifications; the iPhone client presents the newest messages and calls.

![Architecture overview](docs/assets/relay-overview.png)

## iPhone / Safari PWA

<img src="docs/assets/iphone-ui-preview.png" alt="FarBridge Relay iPhone Safari PWA message list preview" width="360">

<sub>Message list preview: newest first, expandable SMS, favorites, pinning, and manual refresh.</sub>

## Features

- Offline Android event queue with retry after network recovery
- Server authentication, event de-duplication, device state audit, and bounded retention
- Separate iPhone message/call views; newest first; expandable full SMS content
- Favorites, pinning, copy, list-only deletion, and manual refresh
- Optional Safari Web Push and Bark fallback with privacy-mode lock-screen alerts

## Download and use

Get these files from [Releases](https://github.com/Apageoflove/farbridge-relay/releases):

- farbridge-relay-android-v1.0.9.apk — Android application package
- farbridge-relay-ios-safari-setup.md — offline iPhone Safari/PWA setup guide
- SHA256SUMS.txt — APK integrity checksum

iPhone uses Safari/PWA rather than a fabricated IPA. Open your own HTTPS address in Safari, sign in, choose **Share → Add to Home Screen**, then enable Web Push in the project settings. For a fallback, copy the device code or full URL from Bark into **Settings → Bark**, save, and send a test.

## Generate your own keys

- Device key: python -c "import secrets; print(secrets.token_urlsafe(48))"
- Android signing: create and keep a local keytool keystore.
- Safari Web Push: install py-vapid, then run vapid --gen and vapid --applicationServerKey.
- Bark: copy the device code or full URL from your own Bark app.

Read [Architecture](docs/ARCHITECTURE.md), [Android permissions](docs/ANDROID_PERMISSIONS.md), [iPhone setup](docs/IPHONE_SETUP.md), the detailed [operator guide (Chinese)](docs/OPERATOR_GUIDE.zh-CN.md), and the [Acceptance checklist](docs/ACCEPTANCE.md) before production use.

## Build locally

Clone https://github.com/Apageoflove/farbridge-relay.git, enter the android directory, then run gradle testDebugUnitTest and gradle assembleRelease. No Gradle Wrapper is bundled; use a Gradle release compatible with Android Gradle Plugin 8.9.1.

## License

Released under the MIT License. See [LICENSE](LICENSE).
