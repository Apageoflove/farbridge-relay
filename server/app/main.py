"""FastAPI composition root for the private Phone Mirror API."""
from __future__ import annotations

import asyncio
import json
import logging
import re
import time
from contextlib import asynccontextmanager
from urllib.parse import urlsplit

from fastapi import BackgroundTasks, Cookie, Depends, FastAPI, Header, HTTPException, Request, Response
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pathlib import Path
from pydantic import BaseModel, ConfigDict
from sqlalchemy import Integer, and_, func, or_, select, text

from .config import Settings
from .db import Base, create_database
from .models import BrowserSession, Device, MirrorRecord, PushSubscription, RuntimeSetting
from .schemas.sync import HeartbeatRequest, SnapshotRequest, SyncBatch
from .security.crypto import FieldCipher
from .security.csrf import csrf_matches
from .security.hmac_auth import NonceStore, verify_device_request
from .security.password import verify_password
from .security.rate_limit import LoginRateLimiter
from .security.session import new_token, token_hash
from .services.sync import apply_events, apply_snapshot
from .services.notifications import bark_payload, is_permanent_push_failure, send_bark, send_webpush

logger = logging.getLogger("phone_mirror")


class LoginRequest(BaseModel):
    """Strict administrator login request."""
    model_config = ConfigDict(extra="forbid", strict=True)
    username: str
    password: str


class PushRequest(BaseModel):
    """Strict Web Push subscription input."""
    model_config = ConfigDict(extra="forbid")
    endpoint: str
    keys: dict[str, str]


class BarkSettingsRequest(BaseModel):
    """Write-only Bark configuration accepted from an authenticated PWA."""
    model_config = ConfigDict(extra="forbid", strict=True)
    enabled: bool
    key: str = ""
    privacy_mode: bool = True


class RecordStateRequest(BaseModel):
    """Partial server-owned SMS state update from the authenticated PWA."""
    model_config = ConfigDict(extra="forbid", strict=True)
    favorite: bool | None = None
    pinned: bool | None = None


def normalize_bark_key(raw: str) -> str | None:
    """从裸设备码或 Bark 示例 URL 中提取并严格校验设备码。"""
    value = raw.strip()
    parsed = urlsplit(value)
    if parsed.scheme.lower() in {"http", "https"}:
        segments = [segment for segment in parsed.path.split("/") if segment]
        value = segments[0] if segments else ""
    return value if re.fullmatch(r"[A-Za-z0-9_-]{8,256}", value) else None


def publish_nowait(subscribers: set[asyncio.Queue], event: dict) -> int:
    """Publish without letting a stalled browser block device ingestion."""
    delivered = 0
    for queue in tuple(subscribers):
        try:
            queue.put_nowait(event)
            delivered += 1
        except asyncio.QueueFull:
            # A client that missed 100 state-change hints will refresh from the
            # authoritative API after reconnecting; evicting it protects sync.
            subscribers.discard(queue)
    return delivered


def create_app(settings: Settings | None = None, releases_root: Path | None = None) -> FastAPI:
    """Build an isolated app instance for production or tests."""
    settings = settings or Settings()
    releases_root = releases_root or Path("/data/phone-mirror/releases")
    engine, SessionLocal = create_database(settings.database_url)
    cipher = FieldCipher(settings.encryption_bytes)
    nonces = NonceStore()
    limiter = LoginRateLimiter(settings.login_attempts, settings.login_window_seconds)
    subscribers: set[asyncio.Queue] = set()

    @asynccontextmanager
    async def lifespan(_app):
        Base.metadata.create_all(engine)
        yield
        engine.dispose()

    app = FastAPI(title="Phone Mirror", version="1.0", lifespan=lifespan, docs_url=None if settings.app_env == "production" else "/docs")
    app.state.settings = settings
    app.state.sse_subscribers = subscribers

    async def device_auth(request: Request) -> str:
        body = await request.body()
        try:
            return verify_device_request(request.method, request.url.path, body, request.headers, settings.device_secrets, nonces, settings.hmac_skew_seconds)
        except ValueError as exc:
            raise HTTPException(401, "device authentication failed") from exc

    def browser_session(phone_mirror_session: str | None = Cookie(default=None)):
        if not phone_mirror_session:
            raise HTTPException(401, "authentication required")
        with SessionLocal() as db:
            row = db.get(BrowserSession, token_hash(phone_mirror_session))
            if row is None or row.expires_at <= int(time.time()):
                raise HTTPException(401, "session expired")
            return row.token_hash

    def browser_csrf(request: Request, session_hash: str = Depends(browser_session), x_csrf_token: str = Header(default="")):
        with SessionLocal() as db:
            row = db.get(BrowserSession, session_hash)
            if row is None or not csrf_matches(row.csrf_hash, x_csrf_token):
                raise HTTPException(403, "CSRF validation failed")
        return session_hash

    async def publish(kind: str, entity_type: str = "", source_id: str = ""):
        # SSE intentionally carries identifiers and change types, never message content.
        event = {"type": kind, "entity_type": entity_type, "source_id": source_id}
        publish_nowait(subscribers, event)

    def current_bark_config() -> dict:
        """Resolve encrypted runtime Bark settings before environment defaults."""
        with SessionLocal() as db:
            row = db.get(RuntimeSetting, "bark")
            if row is not None:
                try:
                    value = json.loads(cipher.decrypt(row.ciphertext))
                    return {
                        "enabled": bool(value.get("enabled")),
                        "key": str(value.get("key", "")),
                        "privacy_mode": bool(value.get("privacy_mode", True)),
                    }
                except Exception as exc:
                    logger.error("encrypted Bark settings are unreadable: %s", type(exc).__name__)
                    return {"enabled": False, "key": "", "privacy_mode": True}
        return {"enabled": settings.bark_enabled, "key": settings.bark_key, "privacy_mode": settings.bark_privacy_mode}

    async def notify_event(event) -> None:
        """Deliver generic Web Push and configured Bark without affecting sync ACKs."""
        if event.action != "UPSERT":
            return
        payload = event.payload or {}
        otp = payload.get("otp")
        bark = current_bark_config()
        if bark["enabled"]:
            try:
                await send_bark(settings.bark_base_url, bark["key"], bark_payload(bark["privacy_mode"], str(payload.get("sender", "")), str(payload.get("body", "")), str(otp) if otp else None))
            except Exception as exc:
                logger.warning("Bark delivery failed: %s", type(exc).__name__)
        if settings.vapid_private_key and settings.vapid_subject:
            with SessionLocal() as db:
                subscriptions = db.scalars(select(PushSubscription).where(PushSubscription.disabled.is_(False))).all()
            generic = {"title": "远桥", "body": "收到验证码" if otp else "收到新记录", "type": f"{event.entity_type.lower()}.upserted"}
            for subscription in subscriptions:
                try:
                    await asyncio.to_thread(send_webpush, json.loads(cipher.decrypt(subscription.ciphertext)), generic, settings.vapid_private_key, settings.vapid_subject)
                except Exception as exc:
                    status_code = getattr(getattr(exc, "response", None), "status_code", 0)
                    if is_permanent_push_failure(status_code):
                        with SessionLocal.begin() as db:
                            row = db.get(PushSubscription, subscription.id)
                            if row:
                                row.disabled = True
                    logger.warning("Web Push delivery failed: %s", type(exc).__name__)

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        response = await call_next(request)
        if request.url.path.startswith("/api/"):
            response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        return response

    @app.post("/api/v1/sync/events")
    async def sync_events(batch: SyncBatch, background: BackgroundTasks, device_id: str = Depends(device_auth)):
        try:
            with SessionLocal.begin() as db:
                acked, effective = apply_events(db, cipher, device_id, batch.events)
                expire_records(db)
        except ValueError as exc:
            raise HTTPException(409, str(exc)) from exc
        for event in effective:
            await publish(f"{event.entity_type.lower()}.{event.action.lower()}d", event.entity_type, event.source_id)
            background.add_task(notify_event, event)
        return {"acked_event_ids": acked}

    @app.post("/api/v1/sync/snapshot")
    async def snapshot(value: SnapshotRequest, background: BackgroundTasks, device_id: str = Depends(device_auth)):
        try:
            with SessionLocal.begin() as db:
                effective = apply_snapshot(db, cipher, device_id, value)
                expire_records(db)
        except ValueError as exc:
            raise HTTPException(409, str(exc)) from exc
        for mutation in effective:
            await publish(f"{mutation.entity_type.lower()}.upserted", mutation.entity_type, mutation.source_id)
            background.add_task(notify_event, mutation)
        await publish("snapshot.applied")
        return {"snapshot_id": value.snapshot_id, "status": "applied"}

    @app.post("/api/v1/heartbeat")
    async def heartbeat(value: HeartbeatRequest, device_id: str = Depends(device_auth)):
        if value.device_id != device_id:
            raise HTTPException(409, "device mismatch")
        with SessionLocal.begin() as db:
            row = db.get(Device, device_id)
            values = value.model_dump(exclude={"device_id", "timestamp"})
            if row is None:
                row = Device(device_id=device_id, last_seen=value.timestamp, **values)
                db.add(row)
            else:
                row.last_seen = value.timestamp
                for key, item in values.items():
                    setattr(row, key, item)
        await publish("device.updated")
        return {"status": "ok"}

    @app.post("/api/v1/auth/login")
    def login(value: LoginRequest, request: Request, response: Response):
        key = request.client.host if request.client else "unknown"
        if not limiter.allow(key):
            raise HTTPException(429, "too many attempts")
        if value.username != settings.admin_username or not verify_password(settings.admin_password_hash, value.password):
            limiter.fail(key)
            raise HTTPException(401, "invalid credentials")
        limiter.success(key)
        session_token, csrf_token = new_token(), new_token()
        with SessionLocal.begin() as db:
            db.add(BrowserSession(token_hash=token_hash(session_token), csrf_hash=token_hash(csrf_token), expires_at=int(time.time()) + settings.session_ttl_seconds))
        response.set_cookie("phone_mirror_session", session_token, secure=settings.secure_cookie, httponly=True, samesite="strict", max_age=settings.session_ttl_seconds, path="/")
        return {"csrf_token": csrf_token}

    @app.post("/api/v1/auth/logout")
    def logout(response: Response, session_hash: str = Depends(browser_csrf)):
        with SessionLocal.begin() as db:
            row = db.get(BrowserSession, session_hash)
            if row:
                db.delete(row)
        response.delete_cookie("phone_mirror_session", path="/", secure=settings.secure_cookie, httponly=True, samesite="strict")
        return {"status": "logged_out"}

    @app.get("/api/v1/auth/session")
    def restore_session(session_hash: str = Depends(browser_session)):
        """Validate the HttpOnly cookie and rotate the in-memory CSRF token."""
        csrf_token = new_token()
        with SessionLocal.begin() as db:
            row = db.get(BrowserSession, session_hash)
            if row is None:
                raise HTTPException(401, "authentication required")
            row.csrf_hash = token_hash(csrf_token)
        return {"authenticated": True, "csrf_token": csrf_token}

    def expire_records(db, now: int | None = None) -> int:
        """Retire non-favorite records older than 30 days into safe tombstones."""
        cutoff = (now if now is not None else int(time.time())) - 30 * 24 * 60 * 60
        rows = db.scalars(
            select(MirrorRecord).where(
                MirrorRecord.occurred_at < cutoff,
                MirrorRecord.user_deleted.is_(False),
                or_(MirrorRecord.entity_type != "SMS", MirrorRecord.favorite.is_(False)),
            )
        ).all()
        for row in rows:
            row.user_deleted = True
            row.deleted = True
            row.pinned = False
            row.pinned_at = None
            row.ciphertext = cipher.encrypt("{}")
        return len(rows)

    def query_records(entity_type: str, limit: int, cursor: str | None, q: str | None):
        """按发生时间和主键稳定倒序，并兼容旧版纯数字 cursor。"""
        limit = min(max(limit, 1), 100)
        with SessionLocal.begin() as db:
            expire_records(db)
            visible = and_(
                MirrorRecord.user_deleted.is_(False),
                or_(MirrorRecord.deleted.is_(False), and_(MirrorRecord.entity_type == "SMS", MirrorRecord.favorite.is_(True))),
            )
            stmt = select(MirrorRecord).where(MirrorRecord.entity_type == entity_type, visible)
            if cursor is not None:
                # Only accept cursors that this service can generate. Strict
                # bounds prevent malformed or oversized values reaching the DB.
                state_match = re.fullmatch(r"([01]):([0-9]+):([0-9]+):([0-9]+)", cursor)
                match = re.fullmatch(r"([0-9]+):([0-9]+)", cursor)
                if state_match:
                    cursor_pinned, cursor_pinned_at, occurred_at, record_id = (int(part) for part in state_match.groups())
                    if any(value > 2**63 - 1 for value in (cursor_pinned_at, occurred_at, record_id)) or record_id < 1:
                        raise HTTPException(422, "invalid cursor")
                    pinned_value = func.cast(MirrorRecord.pinned, Integer)
                    pinned_at_value = func.coalesce(MirrorRecord.pinned_at, 0)
                    stmt = stmt.where(or_(
                        pinned_value < cursor_pinned,
                        and_(pinned_value == cursor_pinned, or_(
                            pinned_at_value < cursor_pinned_at,
                            and_(pinned_at_value == cursor_pinned_at, or_(
                                MirrorRecord.occurred_at < occurred_at,
                                and_(MirrorRecord.occurred_at == occurred_at, MirrorRecord.id < record_id),
                            )),
                        )),
                    ))
                elif match:
                    occurred_at, record_id = (int(part) for part in match.groups())
                    if occurred_at > 2**63 - 1 or not 1 <= record_id <= 2**63 - 1:
                        raise HTTPException(422, "invalid cursor")
                    stmt = stmt.where(
                        or_(
                            MirrorRecord.occurred_at < occurred_at,
                            and_(MirrorRecord.occurred_at == occurred_at, MirrorRecord.id < record_id),
                        )
                    )
                elif re.fullmatch(r"[0-9]+", cursor):
                    record_id = int(cursor)
                    if not 1 <= record_id <= 2**63 - 1:
                        raise HTTPException(422, "invalid cursor")
                    stmt = stmt.where(MirrorRecord.id < record_id)
                else:
                    raise HTTPException(422, "invalid cursor")
            rows = db.scalars(
                stmt.order_by(
                    MirrorRecord.pinned.desc(),
                    func.coalesce(MirrorRecord.pinned_at, 0).desc(),
                    MirrorRecord.occurred_at.desc(),
                    MirrorRecord.id.desc(),
                ).limit(limit + 1)
            ).all()
            items = []
            for row in rows[:limit]:
                payload = json.loads(cipher.decrypt(row.ciphertext))
                if q and q.casefold() not in json.dumps(payload, ensure_ascii=False).casefold():
                    continue
                items.append({
                    "id": row.id,
                    "source_id": row.source_id,
                    "occurred_at": row.occurred_at,
                    "favorite": row.favorite,
                    "pinned": row.pinned,
                    **payload,
                })
            if len(rows) > limit:
                last = rows[limit - 1]
                next_cursor = f"{int(last.pinned)}:{last.pinned_at or 0}:{last.occurred_at}:{last.id}"
            else:
                next_cursor = None
            return {"items": items, "next_cursor": next_cursor}

    @app.get("/api/v1/messages", dependencies=[Depends(browser_session)])
    def messages(limit: int = 50, cursor: str | None = None, sender: str | None = None, q: str | None = None):
        result = query_records("SMS", limit, cursor, q)
        if sender:
            result["items"] = [x for x in result["items"] if x.get("sender") == sender]
        return result

    @app.get("/api/v1/calls", dependencies=[Depends(browser_session)])
    def calls(limit: int = 50, cursor: str | None = None, call_type: str | None = None):
        result = query_records("CALL", limit, cursor, None)
        if call_type:
            result["items"] = [x for x in result["items"] if x.get("call_type") == call_type]
        return result

    def update_message_state(record_id: int, value: RecordStateRequest, _: str = Depends(browser_csrf)):
        """Persist favorite/pinned state without mutating the Android source."""
        if value.favorite is None and value.pinned is None:
            raise HTTPException(422, "at least one state field is required")
        with SessionLocal.begin() as db:
            expire_records(db)
            row = db.get(MirrorRecord, record_id)
            if row is None or row.entity_type != "SMS" or row.user_deleted:
                raise HTTPException(404, "message not found")
            if value.favorite is not None:
                row.favorite = value.favorite
            if value.pinned is not None:
                row.pinned = value.pinned
                row.pinned_at = int(time.time()) if value.pinned else None
            return {"id": row.id, "favorite": row.favorite, "pinned": row.pinned}

    app.patch("/api/v1/messages/{record_id}")(update_message_state)

    def delete_record(record_id: int, entity_type: str) -> Response:
        """Replace PWA content with a durable tombstone; Android stays untouched."""
        with SessionLocal.begin() as db:
            row = db.get(MirrorRecord, record_id)
            if row is None or row.entity_type != entity_type or row.user_deleted:
                raise HTTPException(404, "record not found")
            row.user_deleted = True
            row.deleted = True
            row.favorite = False
            row.pinned = False
            row.pinned_at = None
            row.ciphertext = cipher.encrypt("{}")
        return Response(status_code=204)

    @app.delete("/api/v1/messages/{record_id}")
    def delete_message(record_id: int, _: str = Depends(browser_csrf)):
        """Hide one SMS from the server/PWA only."""
        return delete_record(record_id, "SMS")

    @app.delete("/api/v1/calls/{record_id}")
    def delete_call(record_id: int, _: str = Depends(browser_csrf)):
        """Hide one call from the server/PWA only."""
        return delete_record(record_id, "CALL")

    @app.get("/api/v1/device", dependencies=[Depends(browser_session)])
    def device():
        now = int(time.time())
        with SessionLocal() as db:
            row = db.scalar(select(Device).order_by(Device.last_seen.desc()).limit(1))
            if row is None:
                return {"status": "UNKNOWN", "status_reason": "尚未收到一加心跳", "name": "OnePlus Home", "last_seen_at": None, "battery_percent": None, "charging": None, "network_type": None, "sms_permission_ok": False, "call_log_permission_ok": False, "pending_event_count": 0, "sync_error_count": 0, "app_version": ""}
            age = max(0, now - row.last_seen)
            if age > settings.offline_minutes * 60:
                status, reason = "OFFLINE", f"超过 {settings.offline_minutes} 分钟未收到心跳"
            elif age > settings.degraded_minutes * 60:
                status, reason = "DEGRADED", f"超过 {settings.degraded_minutes} 分钟未收到心跳"
            elif not row.sms_permission_ok or not row.call_log_permission_ok or row.status != "ONLINE":
                status, reason = "DEGRADED", "一加权限或默认短信角色异常"
            else:
                status, reason = "ONLINE", "链路心跳正常"
            return {"status": status, "status_reason": reason, "name": "OnePlus Home", "last_seen_at": row.last_seen, "battery_percent": row.battery_percent, "charging": row.charging, "network_type": row.network_type, "sms_permission_ok": row.sms_permission_ok, "call_log_permission_ok": row.call_log_permission_ok, "pending_event_count": row.pending_event_count, "sync_error_count": 0, "app_version": row.app_version}

    @app.get("/api/v1/settings/notifications", dependencies=[Depends(browser_session)])
    def notification_settings():
        """Expose notification capability flags without exposing private keys."""
        bark = current_bark_config()
        with SessionLocal() as db:
            web_push_enabled = db.scalar(select(PushSubscription.id).where(PushSubscription.disabled.is_(False)).limit(1)) is not None
        return {"bark_enabled": bark["enabled"], "web_push_enabled": web_push_enabled, "vapid_public_key": settings.vapid_public_key}

    @app.put("/api/v1/settings/bark")
    def save_bark_settings(value: BarkSettingsRequest, _: str = Depends(browser_csrf)):
        """Store a normalized Bark key encrypted without ever returning it."""
        key = normalize_bark_key(value.key)
        if value.enabled and key is None:
            raise HTTPException(422, "Bark key is invalid")
        payload = json.dumps({"enabled": value.enabled, "key": key if value.enabled else "", "privacy_mode": value.privacy_mode}, separators=(",", ":"))
        with SessionLocal.begin() as db:
            row = db.get(RuntimeSetting, "bark")
            ciphertext = cipher.encrypt(payload)
            if row is None:
                db.add(RuntimeSetting(key="bark", ciphertext=ciphertext, updated_at=int(time.time())))
            else:
                row.ciphertext = ciphertext
                row.updated_at = int(time.time())
        return {"bark_enabled": value.enabled}

    @app.post("/api/v1/bark/test")
    async def bark_test(_: str = Depends(browser_csrf)):
        """Send a content-free Bark test through the configured channel."""
        bark = current_bark_config()
        if not bark["enabled"]:
            raise HTTPException(409, "Bark is not enabled")
        try:
            await send_bark(settings.bark_base_url, bark["key"], {"title": "远桥", "body": "Bark 通道测试成功", "group": "phone-mirror"})
        except Exception as exc:
            logger.warning("Bark test failed: %s", type(exc).__name__)
            raise HTTPException(502, "Bark delivery failed") from exc
        return {"status": "sent"}

    @app.get("/api/v1/events/stream")
    async def events_stream(_: str = Depends(browser_session)):
        queue: asyncio.Queue = asyncio.Queue(maxsize=100)
        subscribers.add(queue)
        async def generate():
            try:
                while True:
                    try:
                        item = await asyncio.wait_for(queue.get(), timeout=20)
                        yield f"event: {item['type']}\ndata: {json.dumps(item, separators=(',', ':'))}\n\n"
                    except TimeoutError:
                        yield ": keepalive\n\n"
            finally:
                subscribers.discard(queue)
        return StreamingResponse(generate(), media_type="text/event-stream", headers={"Cache-Control":"no-store", "X-Accel-Buffering":"no"})

    @app.post("/api/v1/push/subscriptions")
    def add_push(value: PushRequest, _: str = Depends(browser_csrf)):
        with SessionLocal.begin() as db:
            row = PushSubscription(ciphertext=cipher.encrypt(value.model_dump_json()), disabled=False)
            db.add(row)
            db.flush()
            return {"id": row.id}

    @app.delete("/api/v1/push/subscriptions/{subscription_id}")
    def remove_push(subscription_id: int, _: str = Depends(browser_csrf)):
        with SessionLocal.begin() as db:
            row = db.get(PushSubscription, subscription_id)
            if row:
                db.delete(row)
        return Response(status_code=204)

    @app.get("/healthz")
    def healthz():
        """Verify the API process can execute a database query."""
        try:
            with engine.connect() as connection:
                connection.execute(text("SELECT 1"))
            return {"status": "ok", "database": "ok"}
        except Exception as exc:
            logger.error("health dependency failed: %s", type(exc).__name__)
            raise HTTPException(503, "database unavailable") from exc

    @app.get("/downloads/phone-mirror.apk", include_in_schema=False)
    def download_android_apk():
        """Serve the audited OnePlus installer from the project release directory."""
        apk = releases_root / "phone-mirror.apk"
        if not apk.is_file():
            raise HTTPException(404, "Android installer is not published")
        return FileResponse(
            apk,
            media_type="application/vnd.android.package-archive",
            filename="phone-mirror.apk",
            headers={"Cache-Control": "no-store"},
        )

    web_root = Path(__file__).resolve().parent.parent / "web-dist"
    if web_root.is_dir() and (web_root / "index.html").is_file():
        app.mount("/assets", StaticFiles(directory=web_root / "assets"), name="assets")

        @app.get("/service-worker.js", include_in_schema=False)
        def service_worker():
            return FileResponse(web_root / "service-worker.js", media_type="application/javascript", headers={"Cache-Control": "no-cache"})

        @app.get("/{full_path:path}", include_in_schema=False)
        def spa(full_path: str):
            # SPA history-mode fallback; API paths stay 404 and static traversal is rejected.
            if full_path.startswith("api/"):
                raise HTTPException(404, "not found")
            target = (web_root / full_path).resolve()
            if full_path and target.is_file() and web_root in target.parents:
                return FileResponse(target)
            return FileResponse(web_root / "index.html", headers={"Cache-Control": "no-cache"})

    return app
