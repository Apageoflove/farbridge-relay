"""Transactional idempotent event and complete-snapshot application."""
from __future__ import annotations

import json
import time
from dataclasses import dataclass

from sqlalchemy import select

from ..models import MirrorRecord, ProcessedEvent, SnapshotState


@dataclass(frozen=True)
class EffectiveMutation:
    """Describe one visible state change that should emit SSE and notifications."""

    entity_type: str
    action: str
    source_id: str
    payload: dict


def apply_events(db, cipher, device_id: str, events) -> tuple[list[str], list]:
    """Apply a batch atomically and ACK every valid event, including replays.

    Returns (acked_event_ids, effective_events): replays, within-batch
    duplicates, and stale/equal versions are acknowledged silently, while
    effective_events carries only events that mutated mirror state so
    callers publish/notify exactly once per real change.
    """
    acked: list[str] = []
    effective: list = []
    seen: set[str] = set()
    for event in events:
        if event.device_id != device_id:
            raise ValueError("event device does not match authenticated device")
        if event.event_id in seen:
            continue
        seen.add(event.event_id)
        prior = db.scalar(select(ProcessedEvent).where(ProcessedEvent.device_id == device_id, ProcessedEvent.event_id == event.event_id))
        if prior:
            acked.append(event.event_id)
            continue
        row = db.scalar(select(MirrorRecord).where(MirrorRecord.device_id == device_id, MirrorRecord.entity_type == event.entity_type, MirrorRecord.source_id == event.source_id))
        snapshot = db.get(SnapshotState, device_id)
        if row is None and snapshot and event.occurred_at <= snapshot.generated_at:
            # An old outbox item delivered after a complete snapshot cannot resurrect absent data.
            db.add(ProcessedEvent(device_id=device_id, event_id=event.event_id, processed_at=int(time.time())))
            acked.append(event.event_id)
            continue
        mutated = False
        # Equal/older versions are acknowledged but cannot overwrite a tombstone/newer row.
        if row is None or event.entity_version > row.entity_version:
            # A browser tombstone may advance its source version, but that is
            # not a user-visible mutation and must not emit SSE/push again.
            mutated = row is None or not row.user_deleted
            if event.action == "DELETE":
                if row is None:
                    db.add(MirrorRecord(device_id=device_id, entity_type=event.entity_type, source_id=event.source_id, entity_version=event.entity_version, occurred_at=event.occurred_at, ciphertext=cipher.encrypt("{}"), deleted=True))
                else:
                    row.entity_version = event.entity_version
                    # A favorited SMS is an intentional server archive: retain
                    # its original timestamp and encrypted body when Android no
                    # longer has the source row. Other records become minimal
                    # source tombstones.
                    if not (row.entity_type == "SMS" and row.favorite and not row.user_deleted):
                        row.occurred_at = event.occurred_at
                        row.ciphertext = cipher.encrypt("{}")
                    row.deleted = True
            elif row is None:
                db.add(MirrorRecord(device_id=device_id, entity_type=event.entity_type, source_id=event.source_id, entity_version=event.entity_version, occurred_at=event.occurred_at, ciphertext=cipher.encrypt(json.dumps(event.payload, ensure_ascii=False, separators=(",", ":"))), deleted=False))
            elif not row.user_deleted:
                row.entity_version = event.entity_version
                row.occurred_at = event.occurred_at
                row.ciphertext = cipher.encrypt(json.dumps(event.payload, ensure_ascii=False, separators=(",", ":")))
                row.deleted = False
            else:
                # Preserve a browser tombstone across newer Android events,
                # while advancing the version so stale delivery remains inert.
                row.entity_version = event.entity_version
        db.add(ProcessedEvent(device_id=device_id, event_id=event.event_id, processed_at=int(time.time())))
        acked.append(event.event_id)
        if mutated:
            effective.append(event)
    return acked, effective


def apply_snapshot(db, cipher, device_id: str, snapshot) -> list[EffectiveMutation]:
    """Converge a complete snapshot and return only newly visible/newer UPSERTs."""
    if snapshot.device_id != device_id:
        raise ValueError("snapshot device mismatch")
    if not snapshot.complete:
        raise ValueError("partial snapshot rejected")
    state = db.get(SnapshotState, device_id)
    if state and snapshot.generated_at <= state.generated_at:
        raise ValueError("stale snapshot rejected")
    existing = {
        (row.entity_type, row.source_id): row
        for row in db.scalars(select(MirrorRecord).where(MirrorRecord.device_id == device_id)).all()
    }
    effective: list[EffectiveMutation] = []
    seen: set[tuple[str, str]] = set()
    for entity_type, entities in (("SMS", snapshot.messages), ("CALL", snapshot.calls)):
        for entity in entities:
            key = (entity_type, entity.source_id)
            seen.add(key)
            row = existing.get(key)
            if row is None:
                db.add(MirrorRecord(device_id=device_id, entity_type=entity_type, source_id=entity.source_id, entity_version=entity.entity_version, occurred_at=entity.occurred_at, ciphertext=cipher.encrypt(json.dumps(entity.payload, ensure_ascii=False, separators=(",", ":"))), deleted=False))
                effective.append(EffectiveMutation(entity_type=entity_type, action="UPSERT", source_id=entity.source_id, payload=entity.payload))
            elif not row.user_deleted and entity.entity_version > row.entity_version:
                # Snapshot and outbox may race. Only a strictly newer version
                # is visible work, so either arrival order produces one push.
                row.entity_version = entity.entity_version
                row.occurred_at = entity.occurred_at
                row.ciphertext = cipher.encrypt(json.dumps(entity.payload, ensure_ascii=False, separators=(",", ":")))
                row.deleted = False
                effective.append(EffectiveMutation(entity_type=entity_type, action="UPSERT", source_id=entity.source_id, payload=entity.payload))
            elif row.user_deleted and entity.entity_version > row.entity_version:
                # Browser deletions are durable, but advancing the version
                # prevents a stale Android event from becoming effective later.
                row.entity_version = entity.entity_version

    # Absence from a declared complete snapshot is a source deletion. Keep
    # rows as tombstones so a later snapshot cannot resurrect browser-deleted
    # content; only favorited SMS retain their encrypted payload for display.
    for key, row in existing.items():
        if key in seen or row.user_deleted:
            continue
        row.deleted = True
        if not (row.entity_type == "SMS" and row.favorite):
            row.ciphertext = cipher.encrypt("{}")
    if state is None:
        db.add(SnapshotState(device_id=device_id, generated_at=snapshot.generated_at))
    else:
        state.generated_at = snapshot.generated_at
    return effective
