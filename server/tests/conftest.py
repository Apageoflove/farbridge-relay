"""Pytest fixtures create isolated server settings."""
import json

import pytest

from app.config import Settings
from app.security.password import hash_password


@pytest.fixture
def test_settings(tmp_path):
    return Settings(app_env="test", database_url=f"sqlite:///{tmp_path}/test.db", encryption_key="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", session_secret="s" * 48, admin_username="admin", admin_password_hash=hash_password("correct horse battery staple"), device_secrets_json=json.dumps({"android-device-01":"d" * 32}), secure_cookie=False, bark_enabled=False)
