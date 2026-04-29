# Admin user management routes
# POST   /admin/users
# GET    /admin/users
# PATCH  /admin/users/{user_id}
# POST   /admin/users/{user_id}/reset-password

import secrets
import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import hash_password
from app.dependencies import get_current_user, get_db
from app.models.refresh_token import RefreshToken
from app.models.user import User
from app.schemas import (
    CreateUserRequest,
    ErrorResponse,
    ResetPasswordResponse,
    UpdateUserRequest,
    UserResponse,
)

router = APIRouter(prefix="/admin", tags=["admin"])


def _require_admin(current_user: User) -> None:
    if not current_user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=ErrorResponse(code="NOT_ADMIN", message="Admin access required.").model_dump(),
        )


@router.post("/users", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
async def create_user(
    body: CreateUserRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> User:
    _require_admin(current_user)

    existing = await db.execute(select(User).where(User.email == body.email))
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=ErrorResponse(code="EMAIL_TAKEN", message="A user with that email already exists.").model_dump(),
        )

    user = User(
        email=body.email,
        display_name=body.display_name,
        password_hash=hash_password(body.password),
        must_change_password=True,
        is_admin=body.is_admin,
        token_version=0,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    return user


@router.get("/users", response_model=list[UserResponse])
async def list_users(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[User]:
    _require_admin(current_user)
    result = await db.execute(select(User).order_by(User.created_at))
    return list(result.scalars().all())


@router.patch("/users/{user_id}", response_model=UserResponse)
async def update_user(
    user_id: uuid.UUID,
    body: UpdateUserRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> User:
    _require_admin(current_user)

    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=ErrorResponse(code="USER_NOT_FOUND", message="User not found.").model_dump(),
        )

    if body.email is not None:
        conflict = await db.execute(
            select(User).where(User.email == body.email, User.id != user_id)
        )
        if conflict.scalar_one_or_none() is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=ErrorResponse(code="EMAIL_TAKEN", message="A user with that email already exists.").model_dump(),
            )
        user.email = body.email

    if body.display_name is not None:
        user.display_name = body.display_name
    if body.is_admin is not None:
        user.is_admin = body.is_admin

    await db.commit()
    await db.refresh(user)
    return user


@router.post("/users/{user_id}/reset-password", response_model=ResetPasswordResponse)
async def reset_password(
    user_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> ResetPasswordResponse:
    """Reset a user's password (CONCERN-3).

    Generates a new random password, hashes it, sets must_change_password=True,
    increments token_version, revokes all refresh tokens for the user.
    Returns the plain-text password once — it is never stored.
    """
    _require_admin(current_user)

    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=ErrorResponse(code="USER_NOT_FOUND", message="User not found.").model_dump(),
        )

    new_password = secrets.token_urlsafe(16)
    user.password_hash = hash_password(new_password)
    user.must_change_password = True
    user.token_version += 1

    await db.execute(
        update(RefreshToken)
        .where(RefreshToken.user_id == user.id)
        .values(revoked=True)
    )
    await db.commit()

    return ResetPasswordResponse(new_password=new_password)
