# Acceptance Status

## Automated / server-local

- [x] Fresh server pytest suite passes (30 tests)
- [x] Android JVM unit tests pass (40 tests); `lintRelease` and release APK build pass
- [x] Android instrumented tests (`androidTest`) compile successfully; they were not executed on a physical device
- [x] Manual-sync pre-request failure traced to missing `android.permission.ACCESS_NETWORK_STATE`; contract test failed before the fix and passed after the manifest permission was added
- [x] A second pre-request failure was traced from the `1.0.3 (4)` device symptom `network_or_tls_failure` plus zero concurrent server heartbeat/sync requests: `ApiClient` built plain Moshi while Kotlin DTOs used `@JsonClass(generateAdapter=false)`, so request serialization failed before network I/O and was mapped to the generic error
- [x] Moshi regression followed TDD: `ApiMoshiFactoryTest` first failed to compile with `Unresolved reference ApiMoshiFactory`; after adding `ApiMoshiFactory` with `KotlinJsonAdapterFactory`, a real `HeartbeatRequest` serialized to the protocol snake_case fields. Root's focused rerun passed (`BUILD SUCCESSFUL` in 1m34s; 34 tasks executed)
- [x] iPhone PWA tests pass (42 tests); typecheck and production build pass
- [x] Messages and calls are newest-first; foreground views update through SSE with a 15-second reconciliation fallback
- [x] PWA favicon/Home Screen assets use the new Phone Mirror icon
- [x] Layout and JSON artifacts pass static checks
- [x] Compose renders with operator secrets and bind/volumes/healthcheck confirmed
- [x] Database bytes do not contain test plaintext after sync
- [x] API responses carry `Cache-Control: no-store`

## Server integration

- [x] Port 8084 bound only on Docker bridge `172.17.0.1`
- [x] Only `phone-mirror-api-1` was replaced/updated with Compose `up -d --no-deps api`; it is healthy with `RestartCount=0`. All other 18 containers retained the same container IDs and images and continued running
- [x] Caddy snapshot/config validation and existing service regression checks completed
- [x] Public TLS health and APK GET return HTTP 200; APK size is 4,764,223 bytes and streamed SHA-256 matches `00a2067e17566f61aead4814f03acb34725c70c0c9b4ee1642327b33a6e8feda`
- [x] Authenticated public API verification returned 40 messages and 19 calls, with `latest_first=True` for both; SSE returned HTTP 200 with `text/event-stream` and `Cache-Control: no-store`
- [x] Database integrity check is OK, and deployment preserved counts at CALL 19 / SMS 40 / processed events 59
- [x] Android `1.0.5 (6)` APK has v1/v2 signatures and the same signing certificate as `1.0.4 (5)`, allowing an in-place/cover upgrade
- [x] API production smoke passed for login, HMAC, replay, sync, query, deletion and encrypted-at-rest payloads
- [x] Test smoke device row was backed up and then precisely deleted
- [x] Backup and restore drill passed using the project database backup API

## Physical / external — UNVERIFIED until executed

- [ ] OnePlus `1.0.5 (6)` manual sync to server, iPhone/Bark receipt, permission/provider diagnostics and real OTP latency
- [ ] Physical-device execution of the compiled Android instrumented test suite
- [ ] Default SMS role eligibility, opt-in, role loss and fallback
- [ ] Offline queue, ACK-loss replay, source deletions, process death and reboot
- [ ] iPhone Bark receipt in chosen privacy mode and Home Screen Web Push
- [ ] Notification Center deletion limitation acknowledged
- [ ] 72-hour operational test
- [ ] Seven-day unattended soak

Record evidence beside each checkbox; absence of evidence is not PASS.
