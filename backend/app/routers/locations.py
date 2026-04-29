import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.dependencies import get_current_user, get_db
from app.models.group import Group, GroupMembership
from app.models.location import CurrentLocation
from app.models.user import User
from app.schemas import (
    CurrentLocationResponse,
    ErrorResponse,
    LocationSharingResponse,
    PostCurrentLocationRequest,
    SetLocationSharingRequest,
)

router = APIRouter(prefix="/groups", tags=["locations"])


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


async def _require_group_member(db: AsyncSession, group_id: uuid.UUID, current_user: User) -> None:
    await _get_group_or_404(db, group_id)
    membership = await db.execute(
        select(GroupMembership).where(
            GroupMembership.group_id == group_id,
            GroupMembership.user_id == current_user.id,
        )
    )
    if membership.scalar_one_or_none() is None:
        raise _api_error(
            status.HTTP_403_FORBIDDEN,
            "NOT_GROUP_MEMBER",
            "You are not a member of this group.",
        )


async def _get_current_location(
    db: AsyncSession,
    user_id: uuid.UUID,
    group_id: uuid.UUID,
) -> CurrentLocation | None:
    result = await db.execute(
        select(CurrentLocation).where(
            CurrentLocation.user_id == user_id,
            CurrentLocation.group_id == group_id,
        )
    )
    return result.scalar_one_or_none()


def _to_response(location: CurrentLocation) -> CurrentLocationResponse:
    return CurrentLocationResponse(
        user_id=location.user_id,
        group_id=location.group_id,
        latitude=location.latitude,
        longitude=location.longitude,
        accuracy=location.accuracy,
        recorded_at=location.recorded_at,
        updated_at=location.updated_at,
        sharing_enabled=location.sharing_enabled,
    )


@router.post("/{group_id}/locations/current", response_model=CurrentLocationResponse)
async def post_current_location(
    group_id: uuid.UUID,
    body: PostCurrentLocationRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> CurrentLocationResponse:
    await _require_group_member(db, group_id, current_user)

    now = datetime.now(timezone.utc)
    await db.execute(
        insert(CurrentLocation)
        .values(
            user_id=current_user.id,
            group_id=group_id,
            latitude=body.latitude,
            longitude=body.longitude,
            accuracy=body.accuracy,
            recorded_at=now,
            sharing_enabled=True,
        )
        .on_conflict_do_update(
            index_elements=[CurrentLocation.user_id, CurrentLocation.group_id],
            set_={
                "latitude": body.latitude,
                "longitude": body.longitude,
                "accuracy": body.accuracy,
                "recorded_at": now,
                "sharing_enabled": True,
            },
        )
    )
    await db.commit()

    location = await _get_current_location(db, current_user.id, group_id)
    if location is None:
        raise _api_error(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "LOCATION_UPSERT_FAILED",
            "Location update could not be persisted.",
        )

    return _to_response(location)


@router.get("/{group_id}/locations/current", response_model=list[CurrentLocationResponse])
async def list_current_locations(
    group_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[CurrentLocationResponse]:
    await _require_group_member(db, group_id, current_user)

    result = await db.execute(
        select(CurrentLocation)
        .where(
            CurrentLocation.group_id == group_id,
            CurrentLocation.sharing_enabled.is_(True),
        )
        .order_by(CurrentLocation.recorded_at.desc())
    )
    locations = result.scalars().all()
    return [_to_response(location) for location in locations]


@router.put("/{group_id}/me/location-sharing", response_model=LocationSharingResponse)
async def set_location_sharing(
    group_id: uuid.UUID,
    body: SetLocationSharingRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> LocationSharingResponse:
    await _require_group_member(db, group_id, current_user)

    location = await _get_current_location(db, current_user.id, group_id)
    if location is None:
        if body.sharing_enabled:
            raise _api_error(
                status.HTTP_409_CONFLICT,
                "LOCATION_NOT_SET",
                "Post your current location before enabling sharing.",
            )
        return LocationSharingResponse(sharing_enabled=False)

    location.sharing_enabled = body.sharing_enabled
    await db.commit()

    return LocationSharingResponse(sharing_enabled=location.sharing_enabled)
