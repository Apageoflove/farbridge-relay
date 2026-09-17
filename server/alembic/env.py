"""Alembic environment for Phone Mirror schema migrations."""
from alembic import context
from sqlalchemy import engine_from_config, pool
from app.db import Base
from app import models  # noqa: F401

config = context.config
target_metadata = Base.metadata

def run_migrations_offline():
    """Generate SQL without connecting."""
    context.configure(url=config.get_main_option("sqlalchemy.url"), target_metadata=target_metadata, literal_binds=True)
    with context.begin_transaction(): context.run_migrations()

def run_migrations_online():
    """Apply migrations transactionally."""
    connectable = engine_from_config(config.get_section(config.config_ini_section), prefix="sqlalchemy.", poolclass=pool.NullPool)
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction(): context.run_migrations()

run_migrations_offline() if context.is_offline_mode() else run_migrations_online()
