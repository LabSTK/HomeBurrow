"""Phase 2 group and membership routes.

Authorization matrix:
    POST   /groups                           -> is_admin
    GET    /groups                           -> authenticated user (membership-scoped list)
    GET    /groups/{group_id}                -> group member
    GET    /groups/{group_id}/members        -> group member
    POST   /groups/{group_id}/members        -> group owner OR is_admin
    DELETE /groups/{group_id}/members/{id}   -> group owner OR is_admin (with LAST_OWNER protection)
"""

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.dependencies import get_current_user, get_db
from app.models.group import Group, GroupMembership
from app.models.user import User
from app.schemas import (
    AddGroupMemberRequest,
    CreateGroupRequest,
    ErrorResponse,
    GroupDetailResponse,
    GroupMemberResponse,
    GroupSummaryResponse,
)

router = APIRouter(prefix="/groups", tags=["groups"])


def _api_error(status_code: int, code: str, message: str) -> HTTPException:
    return HTTPException(
        status_code=status_code,
        detail=ErrorResponse(code=code, message=message).model_dump(),
    )


async def _get_group_or_404(db: AsyncSession, group_id: uuid.UUID) -> Group:
    result = await db.execute(select(Group).where(Group.id == group_id))
    group = result.scalar_one_or_none()
    if group is None:
        raise _api_error(status.HTTP_404_NOT_FOUND, "GROUP_NOT_FOUND", "Group not found.")
    return group


async def _get_membership(
    db: AsyncSession, group_id: uuid.UUID, user_id: uuid.UUID
) -> GroupMembership | None:
    result = await db.execute(
        select(GroupMembership).where(
            GroupMembership.group_id == group_id,
            GroupMembership.user_id == user_id,
        )
    )
    return result.scalar_one_or_none()


async def _require_group_member(
    db: AsyncSession, group_id: uuid.UUID, current_user: User
) -> tuple[Group, GroupMembership]:
    group = await _get_group_or_404(db, group_id)
    membership = await _get_membership(db, group_id, current_user.id)
    if membership is None:
        raise _api_error(
            status.HTTP_403_FORBIDDEN,
            "NOT_GROUP_MEMBER",
            "You are not a member of this group.",
        )
    return group, membership


async def _require_owner_or_admin(db: AsyncSession, group_id: uuid.UUID, current_user: User) -> Group:
    group = await _get_group_or_404(db, group_id)
    if current_user.is_admin:
        return group

    membership = await _get_membership(db, group_id, current_user.id)
    if membership is None:
        raise _api_error(
            status.HTTP_403_FORBIDDEN,
            "NOT_GROUP_MEMBER",
            "You are not a member of this group.",
        )
    if membership.role != "owner":
        raise _api_error(
            status.HTTP_403_FORBIDDEN,
            "INSUFFICIENT_ROLE",
            "Owner role or admin access required.",
        )
    return group


@router.post("", response_model=GroupDetailResponse, status_code=status.HTTP_201_CREATED)
async def create_group(
    body: CreateGroupRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> GroupDetailResponse:
    if not current_user.is_admin:
        raise _api_error(
            status.HTTP_403_FORBIDDEN,
            "NOT_ADMIN",
            "Admin access required to create groups.",
        )

    name = body.name.strip()
    if not name:
        raise _api_error(
            status.HTTP_400_BAD_REQUEST,
            "INVALID_GROUP_NAME",
            "Group name must not be empty.",
        )

    group = Group(name=name, created_by=current_user.id)
    db.add(group)
    await db.flush()

    db.add(
        GroupMembership(
            group_id=group.id,
            user_id=current_user.id,
            role="owner",
        )
    )
    await db.commit()
    await db.refresh(group)

    return GroupDetailResponse(
        id=group.id,
        name=group.name,
        created_by=group.created_by,
        created_at=group.created_at,
        my_role="owner",
    )


@router.get("", response_model=list[GroupSummaryResponse])
async def list_groups(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[GroupSummaryResponse]:
    result = await db.execute(
        select(Group, GroupMembership.role)
        .join(GroupMembership, GroupMembership.group_id == Group.id)
        .where(GroupMembership.user_id == current_user.id)
        .order_by(Group.created_at)
    )

    return [
        GroupSummaryResponse(
            id=group.id,
            name=group.name,
            created_by=group.created_by,
            created_at=group.created_at,
            my_role=role,
        )
        for group, role in result.all()
    ]


@router.get("/{group_id}", response_model=GroupDetailResponse)
async def get_group(
    group_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> GroupDetailResponse:
    group, membership = await _require_group_member(db, group_id, current_user)
    return GroupDetailResponse(
        id=group.id,
        name=group.name,
        created_by=group.created_by,
        created_at=group.created_at,
        my_role=membership.role,
    )


@router.get("/{group_id}/members", response_model=list[GroupMemberResponse])
async def list_group_members(
    group_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[GroupMemberResponse]:
    await _require_group_member(db, group_id, current_user)

    result = await db.execute(
        select(GroupMembership, User)
        .join(User, User.id == GroupMembership.user_id)
        .where(GroupMembership.group_id == group_id)
        .order_by(GroupMembership.joined_at)
    )

    return [
        GroupMemberResponse(
            user_id=user.id,
            email=user.email,
            display_name=user.display_name,
            role=membership.role,
            joined_at=membership.joined_at,
        )
        for membership, user in result.all()
    ]


@router.post("/{group_id}/members", response_model=GroupMemberResponse, status_code=status.HTTP_201_CREATED)
async def add_group_member(
    group_id: uuid.UUID,
    body: AddGroupMemberRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> GroupMemberResponse:
    await _require_owner_or_admin(db, group_id, current_user)

    user_result = await db.execute(select(User).where(User.id == body.user_id))
    target_user = user_result.scalar_one_or_none()
    if target_user is None:
        raise _api_error(status.HTTP_404_NOT_FOUND, "USER_NOT_FOUND", "User not found.")

    existing_membership = await _get_membership(db, group_id, body.user_id)
    if existing_membership is not None:
        raise _api_error(
            status.HTTP_409_CONFLICT,
            "MEMBER_ALREADY_EXISTS",
            "User is already a group member.",
        )

    db.add(
        GroupMembership(
            group_id=group_id,
            user_id=body.user_id,
            role=body.role,
        )
    )
    await db.commit()

    created_result = await db.execute(
        select(GroupMembership, User)
        .join(User, User.id == GroupMembership.user_id)
        .where(
            GroupMembership.group_id == group_id,
            GroupMembership.user_id == body.user_id,
        )
    )
    membership, user = created_result.one()

    return GroupMemberResponse(
        user_id=user.id,
        email=user.email,
        display_name=user.display_name,
        role=membership.role,
        joined_at=membership.joined_at,
    )


@router.delete("/{group_id}/members/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_group_member(
    group_id: uuid.UUID,
    user_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> None:
    await _require_owner_or_admin(db, group_id, current_user)

    target_result = await db.execute(
        select(GroupMembership).where(
            GroupMembership.group_id == group_id,
            GroupMembership.user_id == user_id,
        )
    )
    target_membership = target_result.scalar_one_or_none()
    if target_membership is None:
        raise _api_error(
            status.HTTP_404_NOT_FOUND,
            "MEMBER_NOT_FOUND",
            "Membership not found for this user in the group.",
        )

    if target_membership.role == "owner":
        owner_count = await db.scalar(
            select(func.count())
            .select_from(GroupMembership)
            .where(
                GroupMembership.group_id == group_id,
                GroupMembership.role == "owner",
            )
        )
        if owner_count is not None and owner_count <= 1:
            raise _api_error(
                status.HTTP_409_CONFLICT,
                "LAST_OWNER",
                "Cannot remove the last group owner.",
            )

    await db.delete(target_membership)
    await db.commit()
