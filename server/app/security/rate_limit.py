"""Small in-process login limiter for the single-worker deployment."""
import threading
import time


class LoginRateLimiter:
    """Limit failed authentication attempts by opaque client key."""
    def __init__(self, max_attempts: int, window_seconds: int):
        self.max_attempts = max_attempts
        self.window_seconds = window_seconds
        self._failures: dict[str, list[float]] = {}
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        """Return whether another login may be attempted."""
        now = time.monotonic()
        with self._lock:
            self._failures[key] = [x for x in self._failures.get(key, []) if now - x < self.window_seconds]
            return len(self._failures[key]) < self.max_attempts

    def fail(self, key: str) -> None:
        """Record one failed login."""
        with self._lock:
            self._failures.setdefault(key, []).append(time.monotonic())

    def success(self, key: str) -> None:
        """Clear failures after successful authentication."""
        with self._lock:
            self._failures.pop(key, None)
