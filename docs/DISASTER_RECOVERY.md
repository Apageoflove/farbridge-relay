# Disaster Recovery

The Android phone is the source of truth, so the safest recovery is often an empty server database followed by a complete Android snapshot. Backups contain encrypted sensitive mirror data and remain sensitive even after a live deletion.

Create: `bash deploy/backup.sh`. Files are mode 600 under `/data/phone-mirror/backups` and expire after seven days. Restore only an explicitly named file, with the app stopped: `bash deploy/manage.sh stop`, `bash deploy/restore.sh /data/phone-mirror/backups/<file>.db`, then start and health-check. Never restore into another project.

After database loss: preserve logs only if redacted, rotate credentials if compromise is suspected, initialize schema, force a complete Android snapshot, compare counts without exposing content, and repeat deletion tests. Existing iOS notifications cannot be recalled.
