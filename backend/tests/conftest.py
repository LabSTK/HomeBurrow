import os
import uuid

import pytest_asyncio
from fastapi import Depends, HTTPException, Request, status
from httpx import ASGITransport, AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

os.environ.setdefault("DATABASE_URL", "postgresql+asyncpg://test:test@localhost/homeburrow_test")
os.environ.setdefault("SECRET_KEY", "test-secret-key")
os.environ.setdefault("STORAGE_PATH", "test-storage")

import app.models.file  # noqa: F401
import app.models.group  # noqa: F401
import app.models.location  # noqa: F401
import app.models.message  # noqa: F401
import app.models.refresh_token  # noqa: F401
import app.models.user  # noqa: F401
from app.database import Base
from app.dependencies import get_current_user, get_db
from app.main import app
from app.models.user import User
from app.schemas import ErrorResponse


@pytest_asyncio.fixture
async def session_factory():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    factory = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)
    try:
        yield factory
    finally:
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.drop_all)
        await engine.dispose()


@pytest_asyncio.fixture
async def client(session_factory):
    async def _override_get_db():
        async with session_factory() as session:
            yield session

    async def _override_get_current_user(
        request: Request,
        db: AsyncSession = Depends(get_db),
    ) -> User:
        header_value = request.headers.get("X-Test-User")
        if header_value is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=ErrorResponse(code="INVALID_TOKEN", message="Could not validate credentials").model_dump(),
            )

        try:
            user_id = uuid.UUID(header_value)
        except ValueError:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=ErrorResponse(code="INVALID_TOKEN", message="Could not validate credentials").model_dump(),
            )

        result = await db.execute(select(User).where(User.id == user_id))
        user = result.scalar_one_or_none()
        if user is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail=ErrorResponse(code="INVALID_TOKEN", message="Could not validate credentials").model_dump(),
            )
        return user

    app.dependency_overrides[get_db] = _override_get_db
    app.dependency_overrides[get_current_user] = _override_get_current_user
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as test_client:
        yield test_client
    app.dependency_overrides.clear()
