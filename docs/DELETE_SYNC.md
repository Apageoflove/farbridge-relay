# Delete Synchronization

Android performs a complete provider query, reconciles against its Room mirror, and appends DELETE events in the same database transaction. A query failure or incomplete snapshot produces no deletion. The server applies a DELETE only when its `entity_version` is not older than the stored row, records its event ID in the same transaction, and ACKs replayed events.

Complete server snapshots are atomic, per-device and monotonic by `generated_at`; incomplete or stale snapshots are rejected. Deletion removes the live encrypted mirror row and causes an identifier-only SSE event. It does not remove an old iOS Notification Center entry or an expired backup; retention is therefore seven days by default and may be reduced to zero.
