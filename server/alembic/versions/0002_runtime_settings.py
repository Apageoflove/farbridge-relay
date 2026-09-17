"""Add encrypted operator-managed runtime settings."""
from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade():
    """Create the encrypted runtime setting store used by Bark onboarding."""
    op.create_table(
        "runtime_settings",
        sa.Column("key", sa.String(length=64), nullable=False),
        sa.Column("ciphertext", sa.Text(), nullable=False),
        sa.Column("updated_at", sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint("key"),
    )


def downgrade():
    """Remove only the runtime setting table introduced by this revision."""
    op.drop_table("runtime_settings")
