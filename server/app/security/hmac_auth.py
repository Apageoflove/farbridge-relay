"""Canonical device HMAC verification and in-process nonce replay defense."""
from __future__ import annotations

import hashlib
import hmac
import threading
import time


class NonceStore:
    """Bounded nonce memory appropriate for a single Uvicorn worker."""
    def __init__(self):
        self._items: dict[tuple[str, str], int] = {}
        self._lock = threading.Lock()

    def consume(self, device_id: str, nonce: str, now: int, ttl: int = 600) -> bool:
        """Atomically reject reuse and prune expired entries."""
        with self._lock:
            self._items = {k: expiry for k, expiry in self._items.items() if expiry > now}
            key = (device_id, nonce)
            if key in self._items:
                return False
            self._items[key] = now + ttl
            return True


def verify_device_request(method: str, path: str, body: bytes, headers, secrets: dict[str, str], nonce_store: NonceStore, skew_seconds: int = 300, now: int | None = None) -> str:
    """Verify identity, timestamp, unique nonce, and constant-time HMAC."""
    device_id = headers.get("X-Device-Id", "")
    timestamp = headers.get("X-Timestamp", "")
    nonce = headers.get("X-Nonce", "")
    signature = headers.get("X-Signature", "")
    if device_id not in secrets or not timestamp.isdigit() or len(nonce) < 12 or len(signature) != 64:
        raise ValueError("invalid authentication headers")
    current = int(time.time()) if now is None else now
    if abs(current - int(timestamp)) > skew_seconds:
        raise ValueError("timestamp outside allowed window")
    canonical = f"{method.upper()}\n{path}\n{timestamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
    expected = hmac.new(secrets[device_id].encode(), canonical, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, signature.lower()):
        raise ValueError("invalid signature")
    # Only consume after signature passes, preventing unauthenticated nonce poisoning.
    if not nonce_store.consume(device_id, nonce, current):
        raise ValueError("replay detected")
    return device_id
