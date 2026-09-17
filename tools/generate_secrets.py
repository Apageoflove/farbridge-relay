"""Print fresh secret material for manual placement in deploy/secrets.env."""
import base64
import os
import secrets

from argon2 import PasswordHasher


def main():
    """Generate an encryption key, session/device secrets and optional password hash."""
    password = os.environ.get("PHONE_MIRROR_ADMIN_PASSWORD")
    print("ENCRYPTION_KEY=" + base64.urlsafe_b64encode(os.urandom(32)).decode())
    print("SESSION_SECRET=" + secrets.token_urlsafe(48))
    print("DEVICE_SECRET=" + secrets.token_urlsafe(48))
    if password:
        print("ADMIN_PASSWORD_HASH=" + PasswordHasher().hash(password))
    else:
        print("Set PHONE_MIRROR_ADMIN_PASSWORD temporarily to generate ADMIN_PASSWORD_HASH", file=__import__("sys").stderr)


if __name__ == "__main__":
    main()
