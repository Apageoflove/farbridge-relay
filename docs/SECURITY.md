# Security

Android requests use HTTPS plus `HMAC-SHA256` over exactly `METHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256(BODY)`. The server accepts a five-minute clock window, rejects reused nonces, compares signatures in constant time, and runs one Uvicorn worker so the in-memory nonce store is authoritative. Event IDs remain the durable idempotency layer.

Browser login uses Argon2id. The session is an opaque Secure, HttpOnly, SameSite=Strict cookie; state changes additionally require the separate `X-CSRF-Token`. Login attempts are rate limited. API responses are `Cache-Control: no-store`.

Production configuration refuses weak/default secrets. Logs may contain event IDs, entity/action and timings, but not bodies, OTPs, phone numbers, cookies, endpoints, keys or passwords. Bark privacy mode is the default and sends only a generic receipt notice.

Threats not solved by this repository include a compromised/rooted Android phone, compromised server root account, unlocked iPhone, malicious keyboard, carrier SS7 attacks, or physical access. Rotate session/device/encryption/Bark/VAPID credentials after suspected exposure; rotating the encryption key requires a controlled data migration or rebuild from Android snapshot.
