"""Run a no-secret-output production smoke test against the public HTTPS endpoint."""
from __future__ import annotations

import hashlib
import hmac
import json
import re
import sqlite3
import time
import uuid
from pathlib import Path

import httpx


ROOT = Path("/data/phone-mirror")
BASE_URL = "https://your-phone-mirror.example:8443"
DEVICE_ID = "android-device-01"
DEVICE_COLUMNS = (
    "device_id",
    "last_seen",
    "battery_percent",
    "charging",
    "network_type",
    "pending_event_count",
    "sms_permission_ok",
    "call_log_permission_ok",
    "app_version",
    "status",
)


def cleanup_synthetic_rows() -> None:
    """Remove only smoke-prefixed rows, including tombstones and idempotency markers."""
    with sqlite3.connect(ROOT / "data/phone-mirror.db", timeout=10) as connection:
        connection.execute("DELETE FROM mirror_records WHERE source_id GLOB 'smoke-*'")
        connection.execute("DELETE FROM processed_events WHERE event_id GLOB 'smoke-*'")


def capture_device_row(
    database_path: Path | str | None = None,
    device_id: str = DEVICE_ID,
) -> tuple[object, ...] | None:
    """Capture every persisted field so smoke can restore the device exactly."""
    path = Path(database_path) if database_path is not None else ROOT / "data/phone-mirror.db"
    columns = ", ".join(DEVICE_COLUMNS)
    with sqlite3.connect(path, timeout=10) as connection:
        return connection.execute(
            f"SELECT {columns} FROM devices WHERE device_id = ?",
            (device_id,),
        ).fetchone()


def restore_device_row(
    original_row: tuple[object, ...] | None,
    database_path: Path | str | None = None,
    device_id: str = DEVICE_ID,
) -> None:
    """Restore the captured device row, or remove the row smoke created."""
    path = Path(database_path) if database_path is not None else ROOT / "data/phone-mirror.db"
    with sqlite3.connect(path, timeout=10) as connection:
        if original_row is None:
            connection.execute("DELETE FROM devices WHERE device_id = ?", (device_id,))
            return

        columns = ", ".join(DEVICE_COLUMNS)
        placeholders = ", ".join("?" for _ in DEVICE_COLUMNS)
        updates = ", ".join(f"{column} = excluded.{column}" for column in DEVICE_COLUMNS[1:])
        connection.execute(
            f"""
            INSERT INTO devices ({columns}) VALUES ({placeholders})
            ON CONFLICT(device_id) DO UPDATE SET {updates}
            """,
            original_row,
        )


def is_acceptable_bark_smoke_status(status_code: int) -> bool:
    """Accept Bark delivery success or the explicit not-configured response only."""
    return status_code in {200, 409}


def load_credentials() -> tuple[str, str]:
    """Read the one-time admin password and device secret without logging them."""
    credentials = (ROOT / "deploy/initial-credentials.txt").read_text(encoding="utf-8")
    password_match = re.search(r"^PWA 登录密码:\s+(.+)$", credentials, re.MULTILINE)
    if not password_match:
        raise RuntimeError("initial admin password is unavailable")

    env_line = next(
        line for line in (ROOT / "deploy/secrets.env").read_text(encoding="utf-8").splitlines()
        if line.startswith("DEVICE_SECRETS_JSON=")
    )
    device_secret = json.loads(env_line.split("=", 1)[1])[DEVICE_ID]
    return password_match.group(1).strip(), device_secret


def signed_headers(path: str, body: bytes, secret: str) -> dict[str, str]:
    """Sign exact request bytes using the Android/server canonical contract."""
    timestamp = str(int(time.time()))
    nonce = uuid.uuid4().hex
    canonical = f"POST\n{path}\n{timestamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
    signature = hmac.new(secret.encode(), canonical, hashlib.sha256).hexdigest()
    return {
        "X-Device-Id": DEVICE_ID,
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
        "X-Signature": signature,
        "Content-Type": "application/json",
    }


def post_device(client: httpx.Client, path: str, payload: dict, secret: str) -> httpx.Response:
    """POST compact JSON whose bytes exactly match the HMAC body hash."""
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
    return client.post(path, content=body, headers=signed_headers(path, body, secret))


def main() -> None:
    """Exercise the public production path and leave no synthetic mirror records."""
    admin_password, device_secret = load_credentials()
    original_device_row = capture_device_row()
    try:
        cleanup_synthetic_rows()
        marker = uuid.uuid4().hex[:12]
        otp = f"8{int(time.time()) % 100000:05d}"
        sms_source, call_source = f"smoke-sms-{marker}", f"smoke-call-{marker}"
        sms_event_id, call_event_id = f"smoke-up-sms-{marker}", f"smoke-up-call-{marker}"

        with httpx.Client(base_url=BASE_URL, timeout=10, follow_redirects=False) as client:
            assert client.get("/healthz").json() == {"status": "ok", "database": "ok"}
            assert client.get("/api/v1/messages").status_code == 401

            login = client.post("/api/v1/auth/login", json={"username": "admin", "password": admin_password})
            login.raise_for_status()
            original_csrf = login.json()["csrf_token"]
            restored = client.get("/api/v1/auth/session")
            restored.raise_for_status()
            csrf = restored.json()["csrf_token"]
            assert csrf != original_csrf
            assert client.post("/api/v1/bark/test", headers={"X-CSRF-Token": original_csrf}).status_code == 403
            bark_status = client.post("/api/v1/bark/test", headers={"X-CSRF-Token": csrf}).status_code
            assert is_acceptable_bark_smoke_status(bark_status)

            now = int(time.time())
            heartbeat = {
                "device_id": DEVICE_ID,
                "timestamp": now,
                "battery_percent": 88,
                "charging": True,
                "network_type": "production-smoke",
                "pending_event_count": 0,
                "sms_permission_ok": True,
                "call_log_permission_ok": True,
                "app_version": "smoke-test",
                "status": "ONLINE",
            }
            post_device(client, "/api/v1/heartbeat", heartbeat, device_secret).raise_for_status()

            events = [
                {
                    "device_id": DEVICE_ID,
                    "event_id": sms_event_id,
                    "entity_type": "SMS",
                    "action": "UPSERT",
                    "source_id": sms_source,
                    "entity_version": 1,
                    "occurred_at": now,
                    "payload": {"sender": "PhoneMirrorSmoke", "body": f"验证码 {otp}", "received_at": now, "otp": otp},
                },
                {
                    "device_id": DEVICE_ID,
                    "event_id": call_event_id,
                    "entity_type": "CALL",
                    "action": "UPSERT",
                    "source_id": call_source,
                    "entity_version": 1,
                    "occurred_at": now,
                    "payload": {"number": "10000", "cached_name": "Smoke", "call_type": "MISSED", "call_date": now, "duration": 0},
                },
            ]
            first = post_device(client, "/api/v1/sync/events", {"events": events}, device_secret)
            first.raise_for_status()
            assert set(first.json()["acked_event_ids"]) == {sms_event_id, call_event_id}
            replay = post_device(client, "/api/v1/sync/events", {"events": events}, device_secret)
            replay.raise_for_status()

            messages = client.get("/api/v1/messages", params={"q": otp}).json()["items"]
            calls = client.get("/api/v1/calls", params={"call_type": "MISSED"}).json()["items"]
            assert len([item for item in messages if item["source_id"] == sms_source]) == 1
            assert len([item for item in calls if item["source_id"] == call_source]) == 1
            assert client.get("/api/v1/device").json()["network_type"] == "production-smoke"
            assert client.get("/").headers["content-type"].startswith("text/html")
            assert "javascript" in client.get("/service-worker.js").headers["content-type"]

            deletes = [
                {**events[0], "event_id": f"smoke-del-sms-{marker}", "action": "DELETE", "entity_version": 2, "payload": None},
                {**events[1], "event_id": f"smoke-del-call-{marker}", "action": "DELETE", "entity_version": 2, "payload": None},
            ]
            post_device(client, "/api/v1/sync/events", {"events": deletes}, device_secret).raise_for_status()
            assert not any(item["source_id"] == sms_source for item in client.get("/api/v1/messages", params={"q": otp}).json()["items"])
            assert not any(item["source_id"] == call_source for item in client.get("/api/v1/calls").json()["items"])
            client.post("/api/v1/auth/logout", headers={"X-CSRF-Token": csrf}).raise_for_status()

        raw_database = (ROOT / "data/phone-mirror.db").read_bytes()
        assert otp.encode() not in raw_database
    finally:
        # Always attempt device restoration even when synthetic-row cleanup fails.
        try:
            cleanup_synthetic_rows()
        finally:
            restore_device_row(original_device_row)
    print("PASS production smoke: HTTPS auth HMAC replay query delete PWA encryption")


if __name__ == "__main__":
    main()
