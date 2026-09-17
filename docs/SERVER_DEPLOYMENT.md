# Server Deployment

All commands start in `/data/phone-mirror`. Do not alter another `/data` project or the live gateway during preparation.

1. Run `bash scripts/verify-layout.sh`.
2. Copy `deploy/env.example` to `deploy/secrets.env`, replace every example, then `chmod 600 deploy/secrets.env`. Generate values with `PHONE_MIRROR_ADMIN_PASSWORD='...' .venv/bin/python tools/generate_secrets.py`; keep the password out of shell history by using a protected interactive method in production.
3. Immediately check `ss -H -ltn 'sport = :8084'`. Port 8084 must be free.
4. Validate without starting: `docker compose -p phone-mirror -f deploy/compose.yml config -q`.
5. Start only this project: `bash deploy/manage.sh start`; verify `bash deploy/manage.sh health` and inspect redacted logs.
6. Before touching Caddy, snapshot `/data/caddy` using the existing operator procedure. Copy/import only the isolated template, replace its placeholder hostname, run the existing Caddy validation command, then reload only after validation passes.
7. Check all pre-existing registered URLs for regression before DNS exposure. Do not append `/PROJECTS.md` until end-to-end health succeeds.

The Compose bind is intentionally `172.17.0.1:8084`; it is not public. It uses one Uvicorn worker because nonce replay state is process-local and SQLite writes are serialized.
