# Test Plan

Automated server gates: strict schemas, weak-secret rejection, AES-GCM round trip/tamper, Argon2id, HMAC canonical vector/timestamp/replay, login throttling, authenticated sync replay, encrypted-at-rest assertion, DELETE, rollback, snapshot freshness/isolation, heartbeat, session/CSRF, query and health. Run `cd server && ../.venv/bin/python -m pytest -q`.

Static deployment gate: `bash scripts/verify-layout.sh`, then `docker compose -p phone-mirror -f deploy/compose.yml config -q` only after operator-created secrets exist.

External gates: OnePlus permissions and actual OTP latency; offline/online delete convergence; process death and reboot; iPhone PWA install, Bark privacy/full choice and Web Push; DNS/TLS; server reboot; 72-hour normal operation; seven-day soak. Record timestamps, OS/app versions and observable outcomes. Never convert an unexecuted external gate into PASS.
