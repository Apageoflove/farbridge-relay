"""Regression tests keep production smoke checks from polluting device state."""
from __future__ import annotations

import importlib.util
import sqlite3
from pathlib import Path

import pytest


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


def load_production_smoke():
    """Load the standalone smoke script as a testable Python module."""
    script_path = Path(__file__).resolve().parents[2] / "tools" / "production_smoke.py"
    spec = importlib.util.spec_from_file_location("production_smoke", script_path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.fixture
def device_database(tmp_path: Path) -> Path:
    """Create the real devices schema in an isolated SQLite database."""
    database_path = tmp_path / "phone-mirror.db"
    with sqlite3.connect(database_path) as connection:
        connection.execute(
            """
            CREATE TABLE devices (
                device_id TEXT PRIMARY KEY,
                last_seen INTEGER NOT NULL,
                battery_percent INTEGER NOT NULL,
                charging BOOLEAN NOT NULL,
                network_type TEXT NOT NULL,
                pending_event_count INTEGER NOT NULL,
                sms_permission_ok BOOLEAN NOT NULL,
                call_log_permission_ok BOOLEAN NOT NULL,
                app_version TEXT NOT NULL,
                status TEXT NOT NULL
            )
            """
        )
    return database_path


def fetch_device(database_path: Path, device_id: str) -> tuple | None:
    """Read every persisted device field for direct state assertions."""
    columns = ", ".join(DEVICE_COLUMNS)
    with sqlite3.connect(database_path) as connection:
        return connection.execute(
            f"SELECT {columns} FROM devices WHERE device_id = ?",
            (device_id,),
        ).fetchone()


def insert_device(database_path: Path, values: tuple) -> None:
    """Insert a complete literal device fixture into the isolated database."""
    placeholders = ", ".join("?" for _ in DEVICE_COLUMNS)
    columns = ", ".join(DEVICE_COLUMNS)
    with sqlite3.connect(database_path) as connection:
        connection.execute(
            f"INSERT INTO devices ({columns}) VALUES ({placeholders})",
            values,
        )


def test_restore_device_row_reinstates_every_existing_value(device_database: Path) -> None:
    """A smoke heartbeat must not leave any real device field overwritten."""
    smoke = load_production_smoke()
    insert_device(
        device_database,
        ("android-device-01", 1700000001, 37, 0, "wifi", 4, 1, 0, "1.4.2", "DEGRADED"),
    )

    original = smoke.capture_device_row(device_database, "android-device-01")
    with sqlite3.connect(device_database) as connection:
        connection.execute(
            """
            UPDATE devices
            SET last_seen = 1800000002,
                battery_percent = 88,
                charging = 1,
                network_type = 'production-smoke',
                pending_event_count = 0,
                sms_permission_ok = 1,
                call_log_permission_ok = 1,
                app_version = 'smoke-test',
                status = 'ONLINE'
            WHERE device_id = 'android-device-01'
            """
        )

    smoke.restore_device_row(original, device_database, "android-device-01")

    assert fetch_device(device_database, "android-device-01") == (
        "android-device-01",
        1700000001,
        37,
        0,
        "wifi",
        4,
        1,
        0,
        "1.4.2",
        "DEGRADED",
    )


def test_restore_device_row_removes_row_created_by_smoke(device_database: Path) -> None:
    """A synthetic device row must disappear when no row existed before smoke."""
    smoke = load_production_smoke()
    original = smoke.capture_device_row(device_database, "android-device-01")
    insert_device(
        device_database,
        ("android-device-01", 1800000002, 88, 1, "production-smoke", 0, 1, 1, "smoke-test", "ONLINE"),
    )

    smoke.restore_device_row(original, device_database, "android-device-01")

    assert fetch_device(device_database, "android-device-01") is None


@pytest.mark.parametrize(
    ("status_code", "accepted"),
    [(200, True), (409, True), (502, False)],
)
def test_bark_smoke_status_accepts_only_sent_or_unconfigured(status_code: int, accepted: bool) -> None:
    """Bark smoke may skip an unconfigured service but must reject delivery failures."""
    smoke = load_production_smoke()

    assert smoke.is_acceptable_bark_smoke_status(status_code) is accepted
