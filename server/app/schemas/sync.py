"""Strict synchronization and heartbeat request schemas."""
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    """Base schema that rejects unknown fields."""
    model_config = ConfigDict(extra="forbid", strict=True)


class SyncEvent(StrictModel):
    """One immutable outbox event from Android."""
    device_id: str = Field(min_length=1, max_length=128)
    event_id: str = Field(min_length=1, max_length=128)
    entity_type: Literal["SMS", "CALL"]
    action: Literal["UPSERT", "DELETE"]
    source_id: str = Field(min_length=1, max_length=128)
    entity_version: int = Field(ge=0)
    occurred_at: int = Field(ge=0)
    payload: dict[str, Any] | None = None

    @model_validator(mode="after")
    def payload_matches_action(self):
        """Prevent malformed UPSERTs or payload-bearing DELETEs."""
        if self.action == "UPSERT" and not self.payload:
            raise ValueError("UPSERT requires payload")
        if self.action == "DELETE" and self.payload is not None:
            raise ValueError("DELETE payload must be null")
        return self


class SyncBatch(StrictModel):
    """Atomic event batch."""
    events: list[SyncEvent] = Field(min_length=1, max_length=200)


class SnapshotEntity(StrictModel):
    """One entity in a complete snapshot."""
    source_id: str
    entity_version: int = Field(ge=0)
    occurred_at: int = Field(ge=0)
    payload: dict[str, Any]


class SnapshotRequest(StrictModel):
    """Atomic complete mirror snapshot from a device."""
    device_id: str
    snapshot_id: str
    generated_at: int = Field(ge=0)
    complete: bool
    messages: list[SnapshotEntity]
    calls: list[SnapshotEntity]


class HeartbeatRequest(StrictModel):
    """Device health update without sensitive content."""
    device_id: str
    timestamp: int = Field(ge=0)
    battery_percent: int = Field(ge=0, le=100)
    charging: bool
    network_type: str = Field(max_length=32)
    pending_event_count: int = Field(ge=0)
    sms_permission_ok: bool
    call_log_permission_ok: bool
    app_version: str = Field(max_length=64)
    status: str = Field(max_length=16)
