import asyncio
from logging.config import fileConfig

from alembic import context
from sqlalchemy.ext.asyncio import create_async_engine

from app.config import settings
from app.database import Base

# Import all models so their tables are registered on Base.metadata
import app.models.user  # noqa: F401
import app.models.group  # noqa: F401
import app.models.location  # noqa: F401
import app.models.message  # noqa: F401
import app.models.file  # noqa: F401
import app.models.refresh_token  # noqa: F401

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    url = settings.DATABASE_URL
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


async def run_migrations_online() -> None:
    connectable = create_async_engine(settings.DATABASE_URL, echo=False)
    async with connectable.connect() as connection:
        await connection.run_sync(_run_sync_migrations)
    await connectable.dispose()


def _run_sync_migrations(connection):
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    asyncio.run(run_migrations_online())
