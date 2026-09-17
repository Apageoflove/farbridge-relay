"""Production record-state, retention, and browser mutation contract tests."""
from __future__ import annotations

import hashlib
import hmac
import json
import sqlite3
import time
import uuid

from fastapi.testclient import TestClient

from app.main import create_app


DEVICE_ID = "android-device-01"


def signed_headers(body: bytes, path: str) -> dict[str, str]:
    """Sign one device request with the isolated test secret."""
    timestamp = str(int(time.time()))
    nonce = uuid.uuid4().hex
    canonical = f"POST\n{path}\n{timestamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
    signature = hmac.new(b"d" * 32, canonical, hashlib.sha256).hexdigest()
    return {
        "X-Device-Id": DEVICE_ID,
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
        "X-Signature": signature,
        "Content-Type": "application/json",
    }


def sync_event(client: TestClient, event: dict) -> None:
    """Submit one Android mirror event and require an ACK."""
    body = json.dumps({"events": [event]}, separators=(",", ":")).encode()
    response = client.post(
        "/api/v1/sync/events",
        content=body,
        headers=signed_headers(body, "/api/v1/sync/events"),
    )
    assert response.status_code == 200
    assert response.json()["acked_event_ids"] == [event["event_id"]]


def sms_event(source_id: str, version: int, occurred_at: int, *, action: str = "UPSERT") -> dict:
    """Build a realistic SMS event without placing secrets in test output."""
    return {
        "device_id": DEVICE_ID,
        "event_id": f"{source_id}-{version}-{action}",
        "entity_type": "SMS",
        "action": action,
        "source_id": source_id,
        "entity_version": version,
        "occurred_at": occurred_at,
        "payload": None if action == "DELETE" else {
            "sender": "fixture",
            "body": f"body-{source_id}",
            "received_at": occurred_at,
        },
    }


def login(client: TestClient) -> str:
    """Create a browser session and return its CSRF token."""
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "correct horse battery staple"},
    )
    assert response.status_code == 200
    return response.json()["csrf_token"]


def test_favorite_sms_survives_android_delete_with_body(test_settings):
    """Source deletion cannot erase a server-favorited SMS or its encrypted body."""
    app = create_app(test_settings)
    now = int(time.time())
    with TestClient(app) as client:
        sync_event(client, sms_event("favorite", 1, now))
        csrf = login(client)
        record = client.get("/api/v1/messages").json()["items"][0]
        assert client.patch(
            f"/api/v1/messages/{record['id']}",
            json={"favorite": True},
            headers={"X-CSRF-Token": csrf},
        ).status_code == 200

        sync_event(client, sms_event("favorite", 2, now + 1, action="DELETE"))
        kept = client.get("/api/v1/messages").json()["items"]
        assert len(kept) == 1
        assert kept[0]["body"] == "body-favorite"
        assert kept[0]["favorite"] is True


def test_user_delete_is_csrf_protected_and_snapshot_cannot_resurrect(test_settings):
    """A browser deletion is server-only and remains a tombstone across snapshots."""
    app = create_app(test_settings)
    now = int(time.time())
    with TestClient(app) as client:
        sync_event(client, sms_event("deleted", 1, now))
        csrf = login(client)
        record_id = client.get("/api/v1/messages").json()["items"][0]["id"]
        assert client.delete(f"/api/v1/messages/{record_id}").status_code == 403
        assert client.delete(
            f"/api/v1/messages/{record_id}",
            headers={"X-CSRF-Token": csrf},
        ).status_code == 204

        snapshot = {
            "device_id": DEVICE_ID,
            "snapshot_id": "cannot-revive",
            "generated_at": now + 10,
            "complete": True,
            "messages": [{
                "source_id": "deleted",
                "entity_version": 99,
                "occurred_at": now,
                "payload": {"sender": "fixture", "body": "resurrected", "received_at": now},
            }],
            "calls": [],
        }
        body = json.dumps(snapshot, separators=(",", ":")).encode()
        assert client.post(
            "/api/v1/sync/snapshot",
            content=body,
            headers=signed_headers(body, "/api/v1/sync/snapshot"),
        ).status_code == 200
        assert client.get("/api/v1/messages").json()["items"] == []
        assert b"resurrected" not in test_settings.database_path.read_bytes()


def test_thirty_day_expiry_keeps_favorite_and_clears_other_plaintext(test_settings):
    """Only favorited SMS bypass the rolling 30-day server retention window."""
    app = create_app(test_settings)
    now = int(time.time())
    old = now - 31 * 24 * 60 * 60
    with TestClient(app) as client:
        sync_event(client, sms_event("keep", 1, now))
        sync_event(client, sms_event("expire", 1, now - 1))
        csrf = login(client)
        records = {item["source_id"]: item for item in client.get("/api/v1/messages").json()["items"]}
        assert client.patch(
            f"/api/v1/messages/{records['keep']['id']}",
            json={"favorite": True},
            headers={"X-CSRF-Token": csrf},
        ).status_code == 200

        # Move both fixture records across the retention boundary after setting
        # the favorite, so the public list itself exercises expiration.
        with sqlite3.connect(test_settings.database_path) as db:
            db.execute("UPDATE mirror_records SET occurred_at = ?", (old,))
        remaining = client.get("/api/v1/messages").json()["items"]
        assert [item["source_id"] for item in remaining] == ["keep"]
        assert remaining[0]["favorite"] is True
        assert b"body-expire" not in test_settings.database_path.read_bytes()


def test_pinned_messages_sort_first_then_unpinned_strictly_newest(test_settings):
    """Pinned SMS lead the list while ordinary records remain newest-first."""
    app = create_app(test_settings)
    now = int(time.time())
    with TestClient(app) as client:
        for source_id, offset in (("old-pinned", 1), ("middle", 2), ("new", 3)):
            sync_event(client, sms_event(source_id, 1, now + offset))
        csrf = login(client)
        records = {item["source_id"]: item for item in client.get("/api/v1/messages").json()["items"]}
        assert client.patch(f"/api/v1/messages/{records['old-pinned']['id']}", json={"pinned": True}).status_code == 403
        updated = client.patch(
            f"/api/v1/messages/{records['old-pinned']['id']}",
            json={"pinned": True},
            headers={"X-CSRF-Token": csrf},
        )
        assert updated.status_code == 200
        assert updated.json()["pinned"] is True
        ordered = client.get("/api/v1/messages").json()["items"]
        assert [item["source_id"] for item in ordered] == ["old-pinned", "new", "middle"]
        assert all("favorite" in item and "pinned" in item for item in ordered)
