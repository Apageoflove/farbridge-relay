"""Initial encrypted mirror schema, frozen independently of current models."""
from alembic import op
import sqlalchemy as sa

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade():
    """Create the exact v1 schema so later model additions do not leak backward."""
    op.create_table(
        "mirror_records",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("device_id", sa.String(length=128), nullable=False),
        sa.Column("entity_type", sa.String(length=8), nullable=False),
        sa.Column("source_id", sa.String(length=128), nullable=False),
        sa.Column("entity_version", sa.Integer(), nullable=False),
        sa.Column("occurred_at", sa.Integer(), nullable=False),
        sa.Column("ciphertext", sa.Text(), nullable=False),
        sa.Column("deleted", sa.Boolean(), nullable=False),
        sa.UniqueConstraint("device_id", "entity_type", "source_id"),
    )
    op.create_index("ix_mirror_records_device_id", "mirror_records", ["device_id"])
    op.create_index("ix_mirror_records_entity_type", "mirror_records", ["entity_type"])
    op.create_index("ix_mirror_records_occurred_at", "mirror_records", ["occurred_at"])
    op.create_index("ix_mirror_records_deleted", "mirror_records", ["deleted"])
    op.create_table(
        "processed_events",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("device_id", sa.String(length=128), nullable=False),
        sa.Column("event_id", sa.String(length=128), nullable=False),
        sa.Column("processed_at", sa.Integer(), nullable=False),
        sa.UniqueConstraint("device_id", "event_id"),
    )
    op.create_index("ix_processed_events_device_id", "processed_events", ["device_id"])
    op.create_table(
        "devices",
        sa.Column("device_id", sa.String(length=128), primary_key=True),
        sa.Column("last_seen", sa.Integer(), nullable=False),
        sa.Column("battery_percent", sa.Integer(), nullable=False),
        sa.Column("charging", sa.Boolean(), nullable=False),
        sa.Column("network_type", sa.String(length=32), nullable=False),
        sa.Column("pending_event_count", sa.Integer(), nullable=False),
        sa.Column("sms_permission_ok", sa.Boolean(), nullable=False),
        sa.Column("call_log_permission_ok", sa.Boolean(), nullable=False),
        sa.Column("app_version", sa.String(length=64), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
    )
    op.create_table(
        "snapshot_states",
        sa.Column("device_id", sa.String(length=128), primary_key=True),
        sa.Column("generated_at", sa.Integer(), nullable=False),
    )
    op.create_table(
        "browser_sessions",
        sa.Column("token_hash", sa.String(length=64), primary_key=True),
        sa.Column("csrf_hash", sa.String(length=64), nullable=False),
        sa.Column("expires_at", sa.Integer(), nullable=False),
    )
    op.create_index("ix_browser_sessions_expires_at", "browser_sessions", ["expires_at"])
    op.create_table(
        "push_subscriptions",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("ciphertext", sa.Text(), nullable=False),
        sa.Column("disabled", sa.Boolean(), nullable=False),
    )


def downgrade():
    """Drop v1 tables in dependency-safe order."""
    for table in (
        "push_subscriptions",
        "browser_sessions",
        "snapshot_states",
        "devices",
        "processed_events",
        "mirror_records",
    ):
        op.drop_table(table)
