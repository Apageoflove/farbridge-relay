# Architecture

The OnePlus system SMS and call providers are the source of truth. Android stores a local Room mirror and transactional outbox, sends at-least-once HMAC-authenticated events, and periodically reconciles complete provider snapshots. A single FastAPI worker applies `(device_id,event_id)` idempotently and identifies mirror rows by `(device_id,entity_type,source_id)`.

SQLite runs in WAL mode with foreign keys enabled. SMS/call payloads and Web Push subscriptions are AES-256-GCM encrypted at application level. The iPhone PWA queries the current mirror; SSE carries only event type and identifiers. Bark and Web Push are notifications, never authoritative history.

Deletion means Android DELETE → live server mirror removal → PWA refresh/SSE removal. It cannot retract notifications already displayed by iOS.
