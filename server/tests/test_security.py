"""Security behavior tests for server primitives."""
import hashlib
import hmac
import secrets
import time

import pytest

from app.config import Settings
from app.security.crypto import FieldCipher
from app.security.hmac_auth import NonceStore, verify_device_request
from app.security.password import hash_password, verify_password
from app.security.rate_limit import LoginRateLimiter


def test_production_rejects_weak_secrets(tmp_path):
    with pytest.raises(ValueError):
        Settings(app_env="production", database_url=f"sqlite:///{tmp_path}/x.db", encryption_key="replace_me", session_secret="short", admin_password_hash="replace_me", device_secrets_json='{"one":"short"}')


def test_production_accepts_generated_unpadded_urlsafe_key():
    """The provisioning script and runtime must agree on token_urlsafe keys."""
    settings = Settings(
        app_env="production",
        database_url="sqlite:////data/phone-mirror/data/test.db",
        encryption_key=secrets.token_urlsafe(32),
        session_secret="s" * 48,
        admin_password_hash=hash_password("correct horse battery staple"),
        device_secrets_json='{"one":"dddddddddddddddddddddddddddddddd"}',
    )
    assert len(settings.encryption_bytes) == 32


def test_aes_gcm_roundtrip_and_tamper():
    cipher = FieldCipher(bytes(range(32)))
    token = cipher.encrypt("secret text")
    assert cipher.decrypt(token) == "secret text"
    damaged = token[:-2] + ("AA" if token[-2:] != "AA" else "BB")
    with pytest.raises(ValueError):
        cipher.decrypt(damaged)


def test_hmac_canonical_timestamp_and_replay():
    body = b'{"a":1}'
    ts = str(int(time.time()))
    nonce = "n-unique-123456"
    canonical = f"POST\n/api/v1/sync/events\n{ts}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
    signature = hmac.new(b"x" * 32, canonical, hashlib.sha256).hexdigest()
    headers = {"X-Device-Id":"one", "X-Timestamp":ts, "X-Nonce":nonce, "X-Signature":signature}
    store = NonceStore()
    assert verify_device_request("POST", "/api/v1/sync/events", body, headers, {"one":"x" * 32}, store) == "one"
    with pytest.raises(ValueError, match="replay"):
        verify_device_request("POST", "/api/v1/sync/events", body, headers, {"one":"x" * 32}, store)


def test_password_and_login_rate_limit():
    value = hash_password("long password")
    assert verify_password(value, "long password")
    assert not verify_password(value, "wrong")
    limiter = LoginRateLimiter(max_attempts=2, window_seconds=60)
    assert limiter.allow("client")
    limiter.fail("client")
    assert limiter.allow("client")
    limiter.fail("client")
    assert not limiter.allow("client")
