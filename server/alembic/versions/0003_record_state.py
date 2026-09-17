"""Add server-owned record state and durable browser tombstones."""
from alembic import op
import sqlalchemy as sa

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade():
    """Add non-null state flags without altering existing mirrored content."""
    with op.batch_alter_table("mirror_records") as batch:
        batch.add_column(sa.Column("favorite", sa.Boolean(), nullable=False, server_default=sa.false()))
        batch.add_column(sa.Column("pinned", sa.Boolean(), nullable=False, server_default=sa.false()))
        batch.add_column(sa.Column("pinned_at", sa.Integer(), nullable=True))
        batch.add_column(sa.Column("user_deleted", sa.Boolean(), nullable=False, server_default=sa.false()))
        batch.create_index("ix_mirror_records_favorite", ["favorite"])
        batch.create_index("ix_mirror_records_pinned", ["pinned"])
        batch.create_index("ix_mirror_records_user_deleted", ["user_deleted"])


def downgrade():
    """Remove only record-state fields introduced by this revision."""
    with op.batch_alter_table("mirror_records") as batch:
        batch.drop_index("ix_mirror_records_user_deleted")
        batch.drop_index("ix_mirror_records_pinned")
        batch.drop_index("ix_mirror_records_favorite")
        batch.drop_column("user_deleted")
        batch.drop_column("pinned_at")
        batch.drop_column("pinned")
        batch.drop_column("favorite")
