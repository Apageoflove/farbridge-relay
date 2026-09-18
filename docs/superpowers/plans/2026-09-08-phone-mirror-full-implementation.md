# Phone Mirror Full Implementation Plan

> **For agentic workers:** Execute each task with test-first development. A task is complete only after its focused tests and the relevant aggregate test suite pass.

**Goal:** Build and deploy a private OnePlus-to-server-to-iPhone mirror for SMS, OTP codes, and call logs, including reliable upsert/delete synchronization, Bark and Web Push notifications, a PWA viewer, and unattended operation.

**Architecture:** The OnePlus app is the source of truth and persists provider changes with a Room transactional outbox before network transmission. A single-worker FastAPI service applies HMAC-authenticated events idempotently to an encrypted SQLite mirror, publishes SSE updates, and sends Bark/Web Push notifications. The Vue PWA is a read-only authenticated viewer and never persistently caches message or call content.

**Tech Stack:** Kotlin/AndroidX/Room/WorkManager/OkHttp; Python 3.12/FastAPI/SQLAlchemy/Alembic/SQLite/AES-GCM; Vue 3/TypeScript/Vite/Vitest; Docker Compose/Caddy on Ubuntu.

**Spec:** `Phone_Mirror_OnePlus到iPhone_短信验证码通话记录_删除同步_长期稳定版_v2.md`

## Global Constraints

- The only project root is `/data/phone-mirror` (`Z:\data\phone-mirror` over SSHFS); do not create a second local project copy.
- Do not read, modify, stop, restart, or reuse files belonging to other `/data` projects.
- Persistent state, caches, backups, secrets, build outputs, and logs must stay under `/data/phone-mirror`.
- Server port `8084` is provisional and must be rechecked immediately before deployment; bind it to `172.17.0.1` or loopback only.
- Reuse the existing Caddy gateway through an isolated site file; snapshot `/data/caddy` before any gateway change.
- Bark and Web Push are notification channels; the server mirror and PWA are the authoritative iPhone view.
- Existing iOS Notification Center entries are not required to disappear after source deletion.
- A failed or partial Android provider query must never generate DELETE events.
- Android 17 OTP delay must be measured on the real OnePlus; Default SMS role remains opt-in unless the delay is observed.
- Production logs must not contain complete SMS bodies, OTPs, phone numbers, credentials, cookies, or push endpoints.
- Every behavior change follows RED, GREEN, REFACTOR; no production implementation precedes its failing test.
- OnePlus, iPhone, DNS/TLS, Bark/Web Push, reboot, 72-hour, and 7-day checks remain explicit external acceptance gates.

---

### Task 1: Repository, isolated runtime layout, and protocol contract

**Files:** Create `.gitignore`, `README.md`, `contracts/sync-event.schema.json`, `contracts/fixtures/*.json`, `scripts/project-env.sh`, `scripts/verify-layout.sh`; modify no external file.

**Interfaces:** Produce versioned `SyncEvent`, `SnapshotRequest`, `HeartbeatRequest`, and ACK JSON contracts consumed by Android, server, and web tests. Event identity is `(device_id,event_id)`; mirror identity is `(device_id,entity_type,source_id)`.

- [ ] Write failing layout/JSON-schema tests proving secrets, DB files, caches, and absolute paths outside `/data/phone-mirror` are rejected.
- [ ] Run `bash scripts/verify-layout.sh`; expect failure because the contract and ignore rules do not exist.
- [ ] Add minimal contracts, fixtures, environment exports, and ignore rules with no real secrets.
- [ ] Run `bash scripts/verify-layout.sh`; expect PASS and no path outside the project root.

### Task 2: Server configuration, database, encryption, and security primitives

**Files:** Create `server/pyproject.toml`, `server/app/config.py`, `server/app/db.py`, `server/app/models.py`, `server/app/security/{crypto,hmac_auth,password,session,csrf,rate_limit}.py`, Alembic migration, and focused tests.

**Interfaces:** `Settings` validates production secrets; `FieldCipher.encrypt/decrypt`; `verify_device_request(method,path,body,headers)` validates device id, timestamp, nonce, and HMAC; session cookies are Secure/HttpOnly/SameSite=Strict; CSRF uses a separate header token.

- [ ] Write pytest failures for short/default secrets, AES-GCM round trip/tamper, canonical HMAC vectors, timestamp skew, nonce reuse, password verification, session expiry, CSRF mismatch, and login throttling.
- [ ] Run `cd server && python -m pytest tests/test_config.py tests/test_crypto.py tests/test_hmac.py tests/test_auth_primitives.py -q`; expect behavioral failures.
- [ ] Implement the minimal primitives and WAL/foreign-key initialization; never log plaintext.
- [ ] Re-run the focused tests and then `python -m pytest -q`; expect zero failures.

### Task 3: Idempotent event and atomic snapshot synchronization

**Files:** Create `server/app/schemas/sync.py`, `server/app/services/sync.py`, `server/app/api/sync.py`, `server/tests/test_sync_events.py`, `server/tests/test_snapshot.py`.

**Interfaces:** `POST /api/v1/sync/events` atomically applies SMS/CALL UPSERT/DELETE and returns exact `acked_event_ids`; `POST /api/v1/sync/snapshot` atomically converges one device while rejecting stale/incomplete snapshots.

- [ ] Write failing tests for batch rollback, duplicate event replay, ACK-loss replay, stale entity version, delete, encrypted storage, snapshot convergence, and device isolation.
- [ ] Run the two focused test files; confirm failures come from missing endpoints/services.
- [ ] Implement transaction boundaries, processed-event uniqueness, source/version ordering, and mirror deletion.
- [ ] Re-run focused and complete server tests; expect zero failures.

### Task 4: Authenticated query API, device health, SSE, Bark, and Web Push

**Files:** Create server routes/services for `auth`, `messages`, `calls`, `device`, `heartbeat`, `events`, `push`, `bark`, `health`, and their tests.

**Interfaces:** Browser routes require session+CSRF where state changes; cursor pagination is stable; SSE emits only identifiers/change types; Bark configuration accepts a base URL and key from secrets; Web Push subscriptions are encrypted at rest and disabled after permanent delivery failures.

- [ ] Write failing tests for login/logout, auth rejection, pagination/search/filtering, heartbeat status thresholds, SSE deletion, Bark privacy/full modes, push failure disablement, and `/healthz` dependency checks.
- [ ] Run focused API/push tests and observe intended failures.
- [ ] Implement minimal routes and notification services; never include full content in privacy mode or logs.
- [ ] Run `cd server && python -m pytest -q`; expect zero failures.

### Task 5: PWA shell, authentication, and sensitive-cache boundary

**Files:** Create `web/package.json`, Vite/Vitest/TypeScript config, `web/src` shell/router/API/auth modules, manifest, service worker, and tests.

**Interfaces:** `apiFetch` uses same-origin cookies, CSRF headers, abort timeouts, and 401 routing; the service worker caches only versioned static assets and treats `/api/**` as network-only with `Cache-Control: no-store`.

- [ ] Write failing Vitest tests for auth redirects, CSRF requests, API timeouts, logout, manifest, and refusal to cache sensitive endpoints.
- [ ] Run `cd web && npm test -- --run`; expect missing-module/behavior failures.
- [ ] Implement the minimal shell, login, accessible navigation, error boundary, and service-worker policy.
- [ ] Re-run tests plus `npm run typecheck` and `npm run build`; expect success.

### Task 6: PWA messages, calls, device, settings, SSE, and notifications

**Files:** Create typed APIs, views, components, composables, styles, and tests under `web/src`.

**Interfaces:** Messages support cursor pagination, sender/search filters, safe copy-code feedback, and SSE refresh/removal; calls support type filter; device displays stale/degraded/offline reasons; settings manages Web Push and explains Bark configuration without exposing its key.

- [ ] Write failing component/composable tests for loading, empty, error, pagination, filter, copy, deletion event, SSE reconnect, resume refresh, push opt-in gesture, and accessible status announcements.
- [ ] Run focused Vitest files and confirm expected failures.
- [ ] Implement responsive iPhone-first UI without persisting sensitive API data in localStorage/IndexedDB/Cache Storage.
- [ ] Run all web tests, typecheck, and production build.

### Task 7: Android build, permission diagnostics, and safe provider readers

**Files:** Create complete Gradle Android project, manifest, XML resources, application/activity, permission checker, SMS/call provider readers, diagnostic UI, receiver trigger, and JVM/instrumentation tests.

**Interfaces:** Provider readers return sealed `CompleteSnapshot` or `QueryFailure`; only complete snapshots can reach reconciliation. Diagnostic results display grant state and masked latest-five records. Broadcast receivers enqueue work and never perform HTTP.

- [ ] Write failing JVM tests for masking/OTP parsing/provider mappings and instrumentation tests for permission/query-state handling.
- [ ] Run focused Gradle tests with `GRADLE_USER_HOME=/data/phone-mirror/.cache/gradle`; observe intended failures.
- [ ] Implement manifests, runtime permission requests, safe cursors, masking, UI, and enqueue-only receivers.
- [ ] Run JVM tests, lint, assembleDebug, and available instrumentation tests; record real-device-only checks separately.

### Task 8: Android Room mirror, transactional outbox, and reconciliation

**Files:** Create Room entities/DAOs/database, repositories, reconcilers, observers, workers, migrations, and tests.

**Interfaces:** `reconcileSms(CompleteSnapshot)` and `reconcileCalls(CompleteSnapshot)` update mirror and append ordered UPSERT/DELETE events in one Room transaction. Query failures make no state change. Full reconcile runs after bootstrap and periodically; observers only enqueue unique work.

- [ ] Write failing Room tests for insert/update/delete, transaction rollback, offline delete persistence, partial-query safety, full old-record deletion, duplicate source ids, and migration.
- [ ] Run focused Android tests and confirm expected failures.
- [ ] Implement minimal Room schema, repositories, diff engine, observers, WorkManager schedules, boot recovery, and ACK cleanup.
- [ ] Re-run focused tests, all Android tests, lint, and build.

### Task 9: Android authenticated networking, retry, snapshot, heartbeat, and secret storage

**Files:** Create Keystore-backed configuration, canonical HMAC signer, HTTP DTO/client, sync/snapshot/heartbeat workers, settings UI, and tests.

**Interfaces:** Device secret is wrapped by Android Keystore; sync batches mark rows SENDING transactionally and ACK only returned ids; ambiguous/network failures return events to retry state; snapshot is resumable and heartbeats expose permission/pending/error state without sensitive text.

- [ ] Write failing tests using protocol fixtures for exact canonical bytes, partial ACK, timeout after commit, backoff, process restart, server URL validation, certificate failure, and secret redaction.
- [ ] Run focused tests and confirm behavioral failures.
- [ ] Implement client/workers/configuration; reject cleartext HTTP outside debug builds.
- [ ] Re-run all Android tests, lint, and build.

### Task 10: Optional Default SMS role with safe fallback

**Files:** Create role manager, required SMS delivery/WAP receiver/respond-via-message/send-to components, basic inbox/delete/send UI, manifest declarations, and tests.

**Interfaces:** Role activation is explicit and reversible. Incoming delivery writes the system SMS provider before mirror/outbox reconciliation. Delete uses provider+mirror+outbox consistency. Role loss changes device status to DEGRADED and falls back to mirror diagnostics without pretending OTP is immediate.

- [ ] Write failing tests for eligibility, denial, role loss, multipart delivery, provider write failure, delete transaction, and component routing.
- [ ] Run focused tests; confirm failures reflect missing behavior.
- [ ] Implement the minimum role-compliant SMS experience; do not request/default to dialer role.
- [ ] Run Android tests/lint/build; leave actual role grant, real OTP latency, send, MMS, and reboot behavior as OnePlus gates.

### Task 11: Isolated Ubuntu deployment and operations

**Files:** Create production Dockerfiles, `deploy/compose.yml`, `deploy/env.example`, `deploy/caddy/phone-mirror.caddy`, scripts for install/start/stop/status/health/backup/restore/cleanup, and operations tests/docs.

**Interfaces:** Compose binds the web/API only to internal port 8084, mounts `/data/phone-mirror/data`, `/data/phone-mirror/logs`, and `/data/phone-mirror/deploy/secrets.env`; Caddy owns public TLS routing. Scripts refuse wrong roots, occupied ports, missing/weak secrets, or unhealthy dependencies.

- [ ] Write shell checks that fail for external bind, wrong volume, absent secrets, plaintext secret leakage, non-`/data/phone-mirror` state, and missing healthcheck.
- [ ] Run configuration checks before building; confirm intended failures.
- [ ] Implement deployment and operation files, snapshot `/data/caddy` before any change, validate Caddy config, and append exactly one Phone Mirror row to `/PROJECTS.md` only after health checks pass.
- [ ] Recheck port 8084, build/start only Phone Mirror services, verify container health/log redaction/restart policy, then validate existing registered URLs for regression before exposing DNS.

### Task 12: Cross-stack contract, failure, security, and external acceptance

**Files:** Create `tests/contract`, `tests/e2e`, `docs/{ARCHITECTURE,SECURITY,ANDROID_PERMISSIONS,ONEPLUS_SETUP,SERVER_DEPLOYMENT,IPHONE_SETUP,DELETE_SYNC,TEST_PLAN,DISASTER_RECOVERY,ACCEPTANCE}.md`.

**Interfaces:** Shared fixtures must serialize identically on Android and server; synthetic E2E covers UPSERT/DELETE/idempotency/SSE/Bark; acceptance document separates local, server-integration, OnePlus, iPhone, DNS/TLS, and soak evidence.

- [ ] Write failing contract/E2E assertions for duplicate replay, offline queue recovery, server restart, stale snapshot, delete convergence, log leakage, API cache headers, and Bark privacy mode.
- [ ] Run server, web, Android, shell, and contract suites; record every failure before fixing.
- [ ] Fix only observed failures and run fresh aggregate builds/tests plus dependency/security scans.
- [ ] Perform server health/regression checks. Mark OnePlus permission/OTP/default-role, iPhone Bark/Web Push, DNS/TLS, notification-center limitation, 72-hour, and 7-day soak items `UNVERIFIED` until physically executed.
