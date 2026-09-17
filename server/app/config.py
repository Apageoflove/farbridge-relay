"""Load and strictly validate runtime configuration."""
from __future__ import annotations

import base64
import json
from pathlib import Path

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


def decode_urlsafe_key(value: str) -> bytes:
    """Decode standard or unpadded URL-safe Base64 from token_urlsafe."""
    encoded = value.encode()
    return base64.urlsafe_b64decode(encoded + b"=" * (-len(encoded) % 4))


class Settings(BaseSettings):
    """Validated settings; production refuses placeholders and weak secrets."""

    model_config = SettingsConfigDict(env_file=None, extra="forbid")
    app_env: str = "production"
    database_url: str = "sqlite:////data/phone-mirror/data/phone-mirror.db"
    encryption_key: str
    session_secret: str
    admin_username: str = "admin"
    admin_password_hash: str
    device_secrets_json: str
    secure_cookie: bool = True
    session_ttl_seconds: int = 43200
    hmac_skew_seconds: int = 300
    login_attempts: int = 5
    login_window_seconds: int = 900
    degraded_minutes: int = 60
    offline_minutes: int = 120
    bark_enabled: bool = False
    bark_base_url: str = "https://api.day.app"
    bark_key: str = ""
    bark_privacy_mode: bool = True
    vapid_public_key: str = ""
    vapid_private_key: str = ""
    vapid_subject: str = ""

    @model_validator(mode="after")
    def validate_security(self):
        """Reject unsafe configuration before binding a production socket."""
        forbidden = {"", "replace_me", "changeme", "password", "secret"}
        try:
            raw_key = decode_urlsafe_key(self.encryption_key)
        except Exception as exc:
            raise ValueError("ENCRYPTION_KEY must be urlsafe base64") from exc
        if len(raw_key) != 32:
            raise ValueError("ENCRYPTION_KEY must decode to 32 bytes")
        if self.app_env == "production":
            if self.session_secret.lower() in forbidden or len(self.session_secret) < 32:
                raise ValueError("SESSION_SECRET is weak")
            if self.admin_password_hash.lower() in forbidden or not self.admin_password_hash.startswith("$argon2id$"):
                raise ValueError("ADMIN_PASSWORD_HASH must be Argon2id")
            try:
                secrets = self.device_secrets
            except Exception as exc:
                raise ValueError("DEVICE_SECRETS_JSON is invalid") from exc
            if not secrets or any(len(v) < 32 or v.lower() in forbidden for v in secrets.values()):
                raise ValueError("device secrets must be at least 32 characters")
            if not self.database_url.startswith("sqlite:////data/phone-mirror/data/"):
                raise ValueError("production database must stay under /data/phone-mirror/data")
            if self.bark_enabled and (not self.bark_key or self.bark_key.lower() in forbidden):
                raise ValueError("BARK_KEY is required when Bark is enabled")
        return self

    @property
    def encryption_bytes(self) -> bytes:
        """Return the decoded AES-256 key."""
        return decode_urlsafe_key(self.encryption_key)

    @property
    def device_secrets(self) -> dict[str, str]:
        """Return device-secret mapping without logging it."""
        value = json.loads(self.device_secrets_json)
        if not isinstance(value, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in value.items()):
            raise ValueError("DEVICE_SECRETS_JSON must be an object")
        return value

    @property
    def database_path(self) -> Path:
        """Return SQLite path for local validation and backup."""
        return Path(self.database_url.removeprefix("sqlite:///"))
