"""SQLAlchemy engine/session setup with SQLite durability pragmas."""
from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, sessionmaker


class Base(DeclarativeBase):
    """Declarative model base."""


def create_database(database_url: str):
    """Create an engine and factory; each SQLite connection enforces invariants."""
    engine = create_engine(database_url, connect_args={"check_same_thread": False}, future=True)

    @event.listens_for(engine, "connect")
    def configure_sqlite(dbapi_connection, _):
        # WAL lets readers continue while the single API worker commits a sync batch.
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA synchronous=NORMAL")
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()

    return engine, sessionmaker(engine, expire_on_commit=False)
