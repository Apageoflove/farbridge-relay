"""Opaque browser-session and CSRF token helpers."""
import hashlib
import secrets


def new_token() -> str:
    """Generate a 256-bit URL-safe token."""
    return secrets.token_urlsafe(32)


def token_hash(token: str) -> str:
    """Hash a token before database storage."""
    return hashlib.sha256(token.encode()).hexdigest()
