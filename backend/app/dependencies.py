from collections.abc import AsyncGenerator

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database import async_session
from app.models.user import User
from app.schemas import ErrorResponse

_bearer = HTTPBearer()

EXEMPT_FROM_PASSWORD_CHANGE = {
    ("POST", "/auth/change-password"),
    ("POST", "/auth/logout"),
}


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session() as session:
        yield session


async def get_current_user(
    request: Request,
    credentials: HTTPAuthorizationCredentials = Depends(_bearer),
    db: AsyncSession = Depends(get_db),
) -> User:
    """Validate JWT, check token_version, enforce must_change_password."""
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=ErrorResponse(code="INVALID_TOKEN", message="Could not validate credentials").model_dump(),
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(
            credentials.credentials,
            settings.SECRET_KEY,
            algorithms=["HS256"],
        )
        user_id: str = payload.get("sub")
        token_version: int = payload.get("token_version")
        if user_id is None or token_version is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception

    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise credentials_exception

    if user.token_version != token_version:
        raise credentials_exception

    _require_password_changed(request, user)

    return user


def _require_password_changed(request: Request, user: User) -> None:
    """Raise HTTP 403 MUST_CHANGE_PASSWORD unless the route is exempt."""
    if not user.must_change_password:
        return

    route_key = (request.method.upper(), request.url.path)
    if route_key in EXEMPT_FROM_PASSWORD_CHANGE:
        return

    raise HTTPException(
        status_code=status.HTTP_403_FORBIDDEN,
        detail=ErrorResponse(
            code="MUST_CHANGE_PASSWORD",
            message="You must change your password before using the API.",
        ).model_dump(),
    )
