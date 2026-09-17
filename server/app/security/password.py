"""Argon2id password hashing and constant-behavior verification."""
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerifyMismatchError
from argon2.low_level import Type

_hasher = PasswordHasher(type=Type.ID)


def hash_password(password: str) -> str:
    """Hash a password with Argon2id."""
    return _hasher.hash(password)


def verify_password(encoded: str, candidate: str) -> bool:
    """Verify a candidate without leaking mismatch exceptions."""
    try:
        return _hasher.verify(encoded, candidate)
    except (VerifyMismatchError, InvalidHashError):
        return False
