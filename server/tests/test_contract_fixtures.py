"""Cross-stack JSON fixtures must stay parseable by strict server schemas."""
import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.schemas.sync import HeartbeatRequest, SyncBatch

FIXTURES = Path(__file__).parents[2] / "contracts" / "fixtures"


def test_sync_and_heartbeat_fixtures_match_wire_models():
    """Validate the shared Android/server examples with strict models."""
    SyncBatch.model_validate_json((FIXTURES / "sync-upsert.json").read_text())
    SyncBatch.model_validate_json((FIXTURES / "sync-delete.json").read_text())
    HeartbeatRequest.model_validate_json((FIXTURES / "heartbeat.json").read_text())


def test_wire_models_reject_unknown_fields():
    """Protocol expansion requires an explicit versioned schema change."""
    value = json.loads((FIXTURES / "heartbeat.json").read_text())
    value["unexpected"] = True
    with pytest.raises(ValidationError):
        HeartbeatRequest.model_validate(value)
