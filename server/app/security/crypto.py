"""AES-GCM field encryption with random nonces and authenticated context."""
import base64
import os

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class FieldCipher:
    """Encrypt/decrypt UTF-8 fields using AES-256-GCM."""
    def __init__(self, key: bytes):
        if len(key) != 32:
            raise ValueError("AES-GCM key must be 32 bytes")
        self._aes = AESGCM(key)

    def encrypt(self, plaintext: str) -> str:
        """Return versioned URL-safe nonce+ciphertext."""
        nonce = os.urandom(12)
        encrypted = self._aes.encrypt(nonce, plaintext.encode(), b"phone-mirror:v1")
        return "v1." + base64.urlsafe_b64encode(nonce + encrypted).decode()

    def decrypt(self, token: str) -> str:
        """Authenticate and decrypt a versioned token."""
        try:
            version, encoded = token.split(".", 1)
            if version != "v1":
                raise ValueError("unsupported ciphertext version")
            raw = base64.urlsafe_b64decode(encoded.encode())
            return self._aes.decrypt(raw[:12], raw[12:], b"phone-mirror:v1").decode()
        except (ValueError, InvalidTag, UnicodeDecodeError) as exc:
            raise ValueError("ciphertext authentication failed") from exc
