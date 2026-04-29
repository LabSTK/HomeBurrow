# Auth routes
# POST /auth/login
# POST /auth/refresh
# POST /auth/logout
# GET  /auth/me
# POST /auth/change-password

import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import (
    create_access_token,
    generate_refresh_token,
    hash_password,
    hash_token,
    refresh_token_expiry,
    verify_password,
)
from app.dependencies import get_current_user, get_db
from app.models.refresh_token import RefreshToken
from app.models.user import User
from app.schemas import (
    AccessTokenResponse,
    ChangePasswordRequest,
    ErrorResponse,
    LoginRequest,
    LogoutRequest,
    RefreshRequest,
    TokenResponse,
    UserResponse,
)

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login", response_model=TokenResponse)
async def login(body: LoginRequest, db: AsyncSession = Depends(get_db)) -> TokenResponse:
    result = await db.execute(select(User).where(User.email == body.email))
    user = result.scalar_one_or_none()

    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=ErrorResponse(code="INVALID_CREDENTIALS", message="Invalid email or password.").model_dump(),
        )

    access_token = create_access_token(user.id, user.token_version)
    raw_refresh = generate_refresh_token()

    db.add(
        RefreshToken(
            user_id=user.id,
            token_hash=hash_token(raw_refresh),
            expires_at=refresh_token_expiry(),
        )
    )
    await db.commit()

    return TokenResponse(access_token=access_token, refresh_token=raw_refresh)


@router.post("/refresh", response_model=AccessTokenResponse)
async def refresh(body: RefreshRequest, db: AsyncSession = Depends(get_db)) -> AccessTokenResponse:
    token_hash = hash_token(body.refresh_token)
    now = datetime.now(timezone.utc)

    result = await db.execute(
        select(RefreshToken).where(
            RefreshToken.token_hash == token_hash,
            RefreshToken.revoked.is_(False),
            RefreshToken.expires_at > now,
        )
    )
    stored = result.scalar_one_or_none()

    if stored is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=ErrorResponse(code="INVALID_TOKEN", message="Refresh token is invalid or expired.").model_dump(),
        )

    user_result = await db.execute(select(User).where(User.id == stored.user_id))
    user = user_result.scalar_one_or_none()

    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=ErrorResponse(code="INVALID_TOKEN", message="Refresh token is invalid or expired.").model_dump(),
        )

    return AccessTokenResponse(access_token=create_access_token(user.id, user.token_version))


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(
    body: LogoutRequest,
    _current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> None:
    token_hash = hash_token(body.refresh_token)
    await db.execute(
        update(RefreshToken)
        .where(RefreshToken.token_hash == token_hash)
        .values(revoked=True)
    )
    await db.commit()


@router.get("/me", response_model=UserResponse)
async def me(current_user: User = Depends(get_current_user)) -> User:
    return current_user


@router.post("/change-password", status_code=status.HTTP_204_NO_CONTENT)
async def change_password(
    body: ChangePasswordRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> None:
    if not verify_password(body.old_password, current_user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=ErrorResponse(code="INVALID_PASSWORD", message="Current password is incorrect.").model_dump(),
        )

    current_user.password_hash = hash_password(body.new_password)
    current_user.token_version += 1
    current_user.must_change_password = False

    await db.execute(
        update(RefreshToken)
        .where(RefreshToken.user_id == current_user.id)
        .values(revoked=True)
    )
    await db.commit()
