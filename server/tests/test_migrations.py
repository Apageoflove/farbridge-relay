"""Alembic migrations must build the production schema without model leakage."""
from __future__ import annotations

import sqlite3
from pathlib import Path

from alembic import command
from alembic.config import Config


def test_fresh_database_upgrades_through_record_state(tmp_path):
    """A fresh database reaches 0003 and contains all server-owned state fields."""
    database = tmp_path / "migration.db"
    config = Config(str(Path(__file__).parents[1] / "alembic.ini"))
    config.set_main_option("sqlalchemy.url", f"sqlite:///{database.as_posix()}")
    command.upgrade(config, "head")

    with sqlite3.connect(database) as connection:
        columns = {row[1] for row in connection.execute("PRAGMA table_info(mirror_records)")}
        revision = connection.execute("SELECT version_num FROM alembic_version").fetchone()[0]
    assert {"favorite", "pinned", "pinned_at", "user_deleted"} <= columns
    assert revision == "0003"
