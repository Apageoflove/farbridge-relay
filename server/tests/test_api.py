"""End-to-end API tests for authentication, sync, query, deletion and health."""
import hashlib
import hmac
import json
import asyncio
import time
import uuid

from fastapi.testclient import TestClient

from app.main import create_app, publish_nowait
import app.main as main_module


def signed_headers(body: bytes, path: str, nonce: str | None = None):
    ts = str(int(time.time()))
    nonce = nonce or uuid.uuid4().hex
    canonical = f"POST\n{path}\n{ts}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
    sig = hmac.new(b"d" * 32, canonical, hashlib.sha256).hexdigest()
    return {"X-Device-Id":"android-device-01", "X-Timestamp":ts, "X-Nonce":nonce, "X-Signature":sig, "Content-Type":"application/json"}


def test_android_apk_download_is_fixed_and_never_spa_fallback(test_settings, tmp_path):
    """The OnePlus installer must be a real APK with download-safe headers."""
    releases = tmp_path / "releases"
    releases.mkdir()
    apk = releases / "phone-mirror.apk"
    apk.write_bytes(b"PK\x03\x04test-apk")
    app = create_app(test_settings, releases_root=releases)
    with TestClient(app) as client:
        response = client.get("/downloads/phone-mirror.apk")
        assert response.status_code == 200
        assert response.content == apk.read_bytes()
        assert response.headers["content-type"] == "application/vnd.android.package-archive"
        assert "attachment" in response.headers["content-disposition"]


def test_sync_idempotent_encrypted_query_delete_and_health(test_settings):
    app = create_app(test_settings)
    occurred_at = int(time.time())
    with TestClient(app) as client:
        event = {"device_id":"android-device-01", "event_id":"evt-1", "entity_type":"SMS", "action":"UPSERT", "source_id":"42", "entity_version":1, "occurred_at":occurred_at, "payload":{"sender":"10086", "body":"验证码 123456", "received_at":occurred_at, "otp":"123456"}}
        body = json.dumps({"events":[event]}, separators=(",", ":")).encode()
        res = client.post("/api/v1/sync/events", content=body, headers=signed_headers(body, "/api/v1/sync/events"))
        assert res.status_code == 200 and res.json()["acked_event_ids"] == ["evt-1"]
        res = client.post("/api/v1/sync/events", content=body, headers=signed_headers(body, "/api/v1/sync/events"))
        assert res.status_code == 200 and res.json()["acked_event_ids"] == ["evt-1"]
        raw = test_settings.database_path.read_bytes()
        assert b"123456" not in raw and b"10086" not in raw

        login = client.post("/api/v1/auth/login", json={"username":"admin", "password":"correct horse battery staple"})
        assert login.status_code == 200
        csrf = login.json()["csrf_token"]
        assert client.get("/api/v1/messages").json()["items"][0]["otp"] == "123456"

        delete_event = {**event, "event_id":"evt-2", "action":"DELETE", "entity_version":2, "payload":None}
        delete_body = json.dumps({"events":[delete_event]}, separators=(",", ":")).encode()
        assert client.post("/api/v1/sync/events", content=delete_body, headers=signed_headers(delete_body, "/api/v1/sync/events")).status_code == 200
        assert client.get("/api/v1/messages").json()["items"] == []
        assert client.post("/api/v1/auth/logout", headers={"X-CSRF-Token":csrf}).status_code == 200
        assert client.get("/api/v1/messages").status_code == 401
        health = client.get("/healthz")
        assert health.status_code == 200 and health.json()["status"] == "ok"


def test_heartbeat_snapshot_and_device_isolation(test_settings):
    app = create_app(test_settings)
    with TestClient(app) as client:
        hb = {"device_id":"android-device-01", "timestamp":int(time.time()), "battery_percent":90, "charging":True, "network_type":"wifi", "pending_event_count":0, "sms_permission_ok":True, "call_log_permission_ok":True, "app_version":"1.0.0", "status":"ONLINE"}
        body = json.dumps(hb, separators=(",", ":")).encode()
        assert client.post("/api/v1/heartbeat", content=body, headers=signed_headers(body, "/api/v1/heartbeat")).status_code == 200
        snap = {"device_id":"android-device-01", "snapshot_id":"snap-1", "generated_at":int(time.time()), "complete":True, "messages":[], "calls":[]}
        sb = json.dumps(snap, separators=(",", ":")).encode()
        assert client.post("/api/v1/sync/snapshot", content=sb, headers=signed_headers(sb, "/api/v1/sync/snapshot")).status_code == 200


def test_pwa_session_device_and_notification_contract(test_settings):
    """The live API shape must match what the iPhone PWA consumes."""
    app = create_app(test_settings)
    with TestClient(app) as client:
        heartbeat = {"device_id":"android-device-01", "timestamp":int(time.time()), "battery_percent":87, "charging":True, "network_type":"wifi", "pending_event_count":2, "sms_permission_ok":True, "call_log_permission_ok":True, "app_version":"1.0.0", "status":"ONLINE"}
        body = json.dumps(heartbeat, separators=(",", ":")).encode()
        assert client.post("/api/v1/heartbeat", content=body, headers=signed_headers(body, "/api/v1/heartbeat")).status_code == 200

        login = client.post("/api/v1/auth/login", json={"username":"admin", "password":"correct horse battery staple"})
        original_csrf = login.json()["csrf_token"]
        restored = client.get("/api/v1/auth/session")
        assert restored.status_code == 200
        assert restored.json()["authenticated"] is True
        assert restored.json()["csrf_token"] != original_csrf

        device = client.get("/api/v1/device").json()
        assert device["name"] == "OnePlus Home"
        assert device["status"] == "ONLINE"
        assert device["last_seen_at"] == heartbeat["timestamp"]
        assert device["pending_event_count"] == 2

        notifications = client.get("/api/v1/settings/notifications").json()
        assert notifications == {"bark_enabled": False, "web_push_enabled": False, "vapid_public_key": ""}


def test_bark_test_is_csrf_protected_and_uses_configured_channel(test_settings, monkeypatch):
    """An administrator can verify Bark without exposing its private key."""
    calls = []

    async def fake_send_bark(base_url, key, payload):
        calls.append((base_url, key, payload))

    monkeypatch.setattr(main_module, "send_bark", fake_send_bark)
    app = create_app(test_settings.model_copy(update={"bark_enabled": True, "bark_key": "test-private-key"}))
    with TestClient(app) as client:
        login = client.post("/api/v1/auth/login", json={"username":"admin", "password":"correct horse battery staple"})
        assert client.post("/api/v1/bark/test").status_code == 403
        response = client.post("/api/v1/bark/test", headers={"X-CSRF-Token": login.json()["csrf_token"]})
        assert response.status_code == 200
        assert len(calls) == 1
        assert "test-private-key" not in str(calls[0][2])


def test_bark_key_can_be_configured_from_pwa_without_plaintext_storage(test_settings, monkeypatch):
    """The iPhone setup flow stores the Bark key encrypted and never returns it."""
    calls = []

    async def fake_send_bark(base_url, key, payload):
        calls.append((base_url, key, payload))

    monkeypatch.setattr(main_module, "send_bark", fake_send_bark)
    app = create_app(test_settings)
    private_key = "pwa-device-key-1234567890"
    with TestClient(app) as client:
        login = client.post("/api/v1/auth/login", json={"username":"admin", "password":"correct horse battery staple"})
        csrf = login.json()["csrf_token"]
        assert client.put(
            "/api/v1/settings/bark",
            json={"enabled": True, "key": private_key, "privacy_mode": False},
        ).status_code == 403
        assert client.put(
            "/api/v1/settings/bark",
            json={"enabled": True, "key": private_key, "privacy_mode": False},
            headers={"X-CSRF-Token": csrf},
        ).status_code == 200
        settings_response = client.get("/api/v1/settings/notifications")
        assert settings_response.json()["bark_enabled"] is True
        assert private_key not in settings_response.text
        assert client.post("/api/v1/bark/test", headers={"X-CSRF-Token": csrf}).status_code == 200
    assert calls[0][1] == private_key
    assert private_key.encode() not in test_settings.database_path.read_bytes()


def test_bark_key_accepts_device_code_and_bark_example_urls(test_settings, monkeypatch):
    """Bark 首页示例 URL 必须始终提取第一段设备码，并拒绝缺失或非法设备码。"""
    calls = []

    async def fake_send_bark(base_url, key, payload):
        """记录实际发送边界收到的设备码，避免测试依赖外部 Bark 服务。"""
        calls.append((base_url, key, payload))

    monkeypatch.setattr(main_module, "send_bark", fake_send_bark)
    app = create_app(test_settings)
    expected_key = "fixture-device-key_123456"
    accepted_values = [
        expected_key,
        f"https://api.day.app/{expected_key}",
        f"https://api.day.app/{expected_key}/example-title/example-body?sound=minuet",
    ]
    with TestClient(app) as client:
        login = client.post(
            "/api/v1/auth/login",
            json={"username": "admin", "password": "correct horse battery staple"},
        )
        csrf = login.json()["csrf_token"]
        headers = {"X-CSRF-Token": csrf}

        for raw_value in accepted_values:
            saved = client.put(
                "/api/v1/settings/bark",
                json={"enabled": True, "key": raw_value, "privacy_mode": True},
                headers=headers,
            )
            assert saved.status_code == 200
            assert client.post("/api/v1/bark/test", headers=headers).status_code == 200
            assert calls[-1][1] == expected_key

        for invalid_value in ("https://api.day.app/", "https://api.day.app/short", "bad key!"):
            rejected = client.put(
                "/api/v1/settings/bark",
                json={"enabled": True, "key": invalid_value, "privacy_mode": True},
                headers=headers,
            )
            assert rejected.status_code == 422


def test_full_sse_queue_is_evicted_without_blocking_sync():
    """A stalled browser must never apply backpressure to Android ingestion."""
    queue = asyncio.Queue(maxsize=1)
    queue.put_nowait({"type": "old"})
    subscribers = {queue}
    assert publish_nowait(subscribers, {"type": "new"}) == 0
    assert subscribers == set()


def test_invalid_batch_rolls_back(test_settings):
    app = create_app(test_settings)
    with TestClient(app) as client:
        events = [{"device_id":"android-device-01", "event_id":"ok", "entity_type":"SMS", "action":"UPSERT", "source_id":"1", "entity_version":1, "occurred_at":1700000000, "payload":{"sender":"x", "body":"ok", "received_at":1700000000}}, {"bad":True}]
        body = json.dumps({"events":events}, separators=(",", ":")).encode()
        assert client.post("/api/v1/sync/events", content=body, headers=signed_headers(body, "/api/v1/sync/events")).status_code == 422


def test_delete_tombstone_blocks_stale_resurrection(test_settings):
    app = create_app(test_settings)
    with TestClient(app) as client:
        base = {"device_id":"android-device-01", "entity_type":"SMS", "source_id":"9", "occurred_at":1700000000}
        upsert = {**base, "event_id":"v3", "action":"UPSERT", "entity_version":3, "payload":{"body":"old"}}
        delete = {**base, "event_id":"v4", "action":"DELETE", "entity_version":4, "payload":None}
        stale = {**base, "event_id":"stale", "action":"UPSERT", "entity_version":3, "payload":{"body":"resurrected"}}
        for event in (upsert, delete, stale):
            body = json.dumps({"events":[event]}, separators=(",", ":")).encode()
            assert client.post("/api/v1/sync/events", content=body, headers=signed_headers(body, "/api/v1/sync/events")).status_code == 200
        assert client.post("/api/v1/auth/login", json={"username":"admin", "password":"correct horse battery staple"}).status_code == 200
        assert client.get("/api/v1/messages").json()["items"] == []


def test_call_type_filter_matches_android_payload(test_settings):
    """Call filtering uses the call_type field emitted by Android."""
    app = create_app(test_settings)
    occurred_at = int(time.time())
    with TestClient(app) as client:
        events = [
            {"device_id":"android-device-01", "event_id":"call-missed", "entity_type":"CALL", "action":"UPSERT", "source_id":"c1", "entity_version":1, "occurred_at":occurred_at, "payload":{"number":"10000", "call_type":"MISSED", "call_date":occurred_at, "duration":0}},
            {"device_id":"android-device-01", "event_id":"call-incoming", "entity_type":"CALL", "action":"UPSERT", "source_id":"c2", "entity_version":1, "occurred_at":occurred_at + 1, "payload":{"number":"10010", "call_type":"INCOMING", "call_date":occurred_at + 1, "duration":12}},
        ]
        body = json.dumps({"events":events}, separators=(",", ":")).encode()
        client.post("/api/v1/sync/events", content=body, headers=signed_headers(body, "/api/v1/sync/events")).raise_for_status()
        client.post("/api/v1/auth/login", json={"username":"admin", "password":"correct horse battery staple"}).raise_for_status()
        items = client.get("/api/v1/calls", params={"call_type":"MISSED"}).json()["items"]
        assert [item["source_id"] for item in items] == ["c1"]


def test_records_use_occurred_at_desc_with_stable_compound_cursor(test_settings):
    """消息必须按发生时间倒序，同时间分页不得重复或遗漏。"""
    app = create_app(test_settings)
    base = int(time.time())
    events = [
        {"device_id":"android-device-01", "event_id":"late", "entity_type":"SMS", "action":"UPSERT", "source_id":"late", "entity_version":1, "occurred_at":base + 300, "payload":{"sender":"late", "body":"late", "received_at":base + 300}},
        {"device_id":"android-device-01", "event_id":"same-a", "entity_type":"SMS", "action":"UPSERT", "source_id":"same-a", "entity_version":1, "occurred_at":base + 200, "payload":{"sender":"same-a", "body":"same-a", "received_at":base + 200}},
        {"device_id":"android-device-01", "event_id":"same-b", "entity_type":"SMS", "action":"UPSERT", "source_id":"same-b", "entity_version":1, "occurred_at":base + 200, "payload":{"sender":"same-b", "body":"same-b", "received_at":base + 200}},
        {"device_id":"android-device-01", "event_id":"old", "entity_type":"SMS", "action":"UPSERT", "source_id":"old", "entity_version":1, "occurred_at":base + 100, "payload":{"sender":"old", "body":"old", "received_at":base + 100}},
    ]
    with TestClient(app) as client:
        body = json.dumps({"events": events}, separators=(",", ":")).encode()
        client.post("/api/v1/sync/events", content=body, headers=signed_headers(body, "/api/v1/sync/events")).raise_for_status()
        client.post("/api/v1/auth/login", json={"username":"admin", "password":"correct horse battery staple"}).raise_for_status()

        first = client.get("/api/v1/messages", params={"limit": 2}).json()
        assert [item["source_id"] for item in first["items"]] == ["late", "same-b"]
        assert isinstance(first["next_cursor"], str) and ":" in first["next_cursor"]
        second = client.get("/api/v1/messages", params={"limit": 2, "cursor": first["next_cursor"]}).json()
        assert [item["source_id"] for item in second["items"]] == ["same-a", "old"]
        assert {item["source_id"] for item in first["items"] + second["items"]} == {"late", "same-a", "same-b", "old"}

        # 升级中的旧 PWA 仍可能发送纯数字 cursor，服务端必须继续接受。
        legacy_cursor = first["items"][1]["id"]
        assert client.get("/api/v1/messages", params={"limit": 2, "cursor": legacy_cursor}).status_code == 200


def test_records_reject_malformed_or_out_of_range_cursors(test_settings):
    """游标必须是正整数旧格式或非负时间加正整数复合格式。"""
    app = create_app(test_settings)
    with TestClient(app) as client:
        client.post("/api/v1/auth/login", json={"username":"admin", "password":"correct horse battery staple"}).raise_for_status()
        invalid_cursors = ["", "abc", "+1", "-1", "1:", ":1", "1:-1", "1:0", "1:2:3", str(2**63)]
        for cursor in invalid_cursors:
            response = client.get("/api/v1/messages", params={"cursor": cursor})
            assert response.status_code == 422, cursor


def test_snapshot_publishes_applied_sse_hint(test_settings):
    """快照成功落库后必须保留 snapshot.applied 实时刷新提示。"""
    app = create_app(test_settings)
    queue = asyncio.Queue()
    app.state.sse_subscribers.add(queue)
    snapshot = {"device_id":"android-device-01", "snapshot_id":"snap-sse", "generated_at":int(time.time()), "complete":True, "messages":[], "calls":[]}
    body = json.dumps(snapshot, separators=(",", ":")).encode()
    with TestClient(app) as client:
        response = client.post("/api/v1/sync/snapshot", content=body, headers=signed_headers(body, "/api/v1/sync/snapshot"))
        assert response.status_code == 200
        assert queue.get_nowait() == {"type": "snapshot.applied", "entity_type": "", "source_id": ""}


def test_snapshot_notifies_only_first_effective_sms_and_call(test_settings, monkeypatch):
    """A fresh snapshot emits entity side effects once; equal entity versions stay quiet."""
    bark_calls = []

    async def fake_send_bark(_base_url, _key, payload):
        bark_calls.append(payload)

    monkeypatch.setattr(main_module, "send_bark", fake_send_bark)
    settings = test_settings.model_copy(update={"bark_enabled": True, "bark_key": "test-private-key"})
    app = create_app(settings)
    queue = asyncio.Queue()
    app.state.sse_subscribers.add(queue)
    generated_at = int(time.time())
    snapshot = {
        "device_id": "android-device-01",
        "snapshot_id": "snap-effective-1",
        "generated_at": generated_at,
        "complete": True,
        "messages": [{"source_id": "sms-snapshot", "entity_version": 1, "occurred_at": generated_at, "payload": {"sender": "10086", "body": "code 123456", "otp": "123456"}}],
        "calls": [{"source_id": "call-snapshot", "entity_version": 1, "occurred_at": generated_at, "payload": {"number": "10010", "call_type": "MISSED", "duration": 0}}],
    }
    with TestClient(app) as client:
        body = json.dumps(snapshot, separators=(",", ":")).encode()
        assert client.post("/api/v1/sync/snapshot", content=body, headers=signed_headers(body, "/api/v1/sync/snapshot")).status_code == 200
        first_events = [queue.get_nowait() for _ in range(queue.qsize())]
        assert first_events == [
            {"type": "sms.upserted", "entity_type": "SMS", "source_id": "sms-snapshot"},
            {"type": "call.upserted", "entity_type": "CALL", "source_id": "call-snapshot"},
            {"type": "snapshot.applied", "entity_type": "", "source_id": ""},
        ]
        assert len(bark_calls) == 2

        repeated = {**snapshot, "snapshot_id": "snap-effective-2", "generated_at": generated_at + 1}
        repeated_body = json.dumps(repeated, separators=(",", ":")).encode()
        assert client.post("/api/v1/sync/snapshot", content=repeated_body, headers=signed_headers(repeated_body, "/api/v1/sync/snapshot")).status_code == 200
        assert queue.get_nowait() == {"type": "snapshot.applied", "entity_type": "", "source_id": ""}
        assert queue.empty()
        assert len(bark_calls) == 2

        newer_message = {**snapshot["messages"][0], "entity_version": 2, "payload": {"sender": "10086", "body": "new code 654321", "otp": "654321"}}
        newer = {**snapshot, "snapshot_id": "snap-effective-3", "generated_at": generated_at + 2, "messages": [newer_message]}
        newer_body = json.dumps(newer, separators=(",", ":")).encode()
        assert client.post("/api/v1/sync/snapshot", content=newer_body, headers=signed_headers(newer_body, "/api/v1/sync/snapshot")).status_code == 200
        assert queue.get_nowait() == {"type": "sms.upserted", "entity_type": "SMS", "source_id": "sms-snapshot"}
        assert queue.get_nowait() == {"type": "snapshot.applied", "entity_type": "", "source_id": ""}
        assert queue.empty()
        assert len(bark_calls) == 3


def test_replay_stale_and_batch_duplicate_have_one_side_effect(test_settings, monkeypatch):
    """ACK replays but publish/notify only a first effective state mutation."""
    bark_calls = []

    async def fake_send_bark(_base_url, _key, payload):
        bark_calls.append(payload)

    monkeypatch.setattr(main_module, "send_bark", fake_send_bark)
    settings = test_settings.model_copy(update={"bark_enabled": True, "bark_key": "test-private-key"})
    app = create_app(settings)
    queue = asyncio.Queue()
    # Current implementation did not expose subscribers; after the fix this captures capture gives
    # the test a real queue at the same boundary used by SSE clients.
    subscribers = getattr(app.state, "sse_subscribers", set())
    subscribers.add(queue)
    with TestClient(app) as client:
        base = {"device_id":"android-device-01", "entity_type":"SMS", "action":"UPSERT", "source_id":"side-effect", "occurred_at":int(time.time()), "payload":{"body":"fixture", "otp":"654321"}}
        first = {**base, "event_id":"effect-v1", "entity_version":1}
        body = json.dumps({"events":[first]}, separators=(",", ":")).encode()
        first_response = client.post("/api/v1/sync/events", content=body, headers=signed_headers(body, "/api/v1/sync/events"))
        replay_response = client.post("/api/v1/sync/events", content=body, headers=signed_headers(body, "/api/v1/sync/events"))
        assert first_response.json()["acked_event_ids"] == ["effect-v1"]
        assert replay_response.json()["acked_event_ids"] == ["effect-v1"]
        assert len(bark_calls) == 1
        assert queue.qsize() == 1

        equal = {**base, "event_id":"effect-equal", "entity_version":1}
        equal_body = json.dumps({"events":[equal]}, separators=(",", ":")).encode()
        assert client.post("/api/v1/sync/events", content=equal_body, headers=signed_headers(equal_body, "/api/v1/sync/events")).status_code == 200
        assert len(bark_calls) == 1 and queue.qsize() == 1

        duplicate = {**base, "event_id":"effect-v2", "entity_version":2}
        duplicate_body = json.dumps({"events":[duplicate, duplicate]}, separators=(",", ":")).encode()
        response = client.post("/api/v1/sync/events", content=duplicate_body, headers=signed_headers(duplicate_body, "/api/v1/sync/events"))
        assert response.json()["acked_event_ids"] == ["effect-v2"]
        assert len(bark_calls) == 2 and queue.qsize() == 2
