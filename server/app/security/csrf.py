"""CSRF comparison helper for state-changing browser requests."""
import hmac


def csrf_matches(expected_hash: str, provided_token: str) -> bool:
    """Compare the stored digest with a provided token in constant time."""
    from .session import token_hash
    return bool(provided_token) and hmac.compare_digest(expected_hash, token_hash(provided_token))
