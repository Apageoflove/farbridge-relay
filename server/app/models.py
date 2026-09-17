"""Persistent models; sensitive values are stored only as ciphertext."""
from __future__ import annotations

from datetime import datetime

from sqlalchemy import Boolean, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from .db import Base


class MirrorRecord(Base):
    """Current SMS or call mirror row."""
    __tablename__ = "mirror_records"
    __table_args__ = (UniqueConstraint("device_id", "entity_type", "source_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    device_id: Mapped[str] = mapped_column(String(128), index=True)
    entity_type: Mapped[str] = mapped_column(String(8), index=True)
    source_id: Mapped[str] = mapped_column(String(128))
    entity_version: Mapped[int] = mapped_column(Integer)
    occurred_at: Mapped[int] = mapped_column(Integer, index=True)
    ciphertext: Mapped[str] = mapped_column(Text)
    deleted: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    # These fields are server-owned PWA state. Device sync may update the
    # mirrored payload but must never reset an operator choice or tombstone.
    favorite: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    pinned: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    pinned_at: Mapped[int | None] = mapped_column(Integer, nullable=True)
    user_deleted: Mapped[bool] = mapped_column(Boolean, default=False, index=True)


class ProcessedEvent(Base):
    """Idempotency record for at-least-once delivery."""
    __tablename__ = "processed_events"
    __table_args__ = (UniqueConstraint("device_id", "event_id"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    device_id: Mapped[str] = mapped_column(String(128), index=True)
    event_id: Mapped[str] = mapped_column(String(128))
    processed_at: Mapped[int] = mapped_column(Integer)


class Device(Base):
    """Latest device health without sensitive message content."""
    __tablename__ = "devices"
    device_id: Mapped[str] = mapped_column(String(128), primary_key=True)
    last_seen: Mapped[int] = mapped_column(Integer)
    battery_percent: Mapped[int] = mapped_column(Integer)
    charging: Mapped[bool] = mapped_column(Boolean)
    network_type: Mapped[str] = mapped_column(String(32))
    pending_event_count: Mapped[int] = mapped_column(Integer)
    sms_permission_ok: Mapped[bool] = mapped_column(Boolean)
    call_log_permission_ok: Mapped[bool] = mapped_column(Boolean)
    app_version: Mapped[str] = mapped_column(String(64))
    status: Mapped[str] = mapped_column(String(16), default="ONLINE")


class SnapshotState(Base):
    """Newest accepted snapshot timestamp per device."""
    __tablename__ = "snapshot_states"
    device_id: Mapped[str] = mapped_column(String(128), primary_key=True)
    generated_at: Mapped[int] = mapped_column(Integer)


class BrowserSession(Base):
    """Hashed browser session id and CSRF token."""
    __tablename__ = "browser_sessions"
    token_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    csrf_hash: Mapped[str] = mapped_column(String(64))
    expires_at: Mapped[int] = mapped_column(Integer, index=True)


class PushSubscription(Base):
    """Encrypted standards-based Web Push subscription."""
    __tablename__ = "push_subscriptions"
    id: Mapped[int] = mapped_column(primary_key=True)
    ciphertext: Mapped[str] = mapped_column(Text)
    disabled: Mapped[bool] = mapped_column(Boolean, default=False)


class RuntimeSetting(Base):
    """Encrypted operator-managed setting keyed by a non-secret name."""
    __tablename__ = "runtime_settings"
    key: Mapped[str] = mapped_column(String(64), primary_key=True)
    ciphertext: Mapped[str] = mapped_column(Text)
    updated_at: Mapped[int] = mapped_column(Integer)
