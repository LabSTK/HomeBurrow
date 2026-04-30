import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import and_, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.dependencies import get_current_user, get_db
from app.models.group import Group, GroupMembership
from app.models.message import Message
from app.models.user import User
from app.schemas import (
    ErrorResponse,
    GroupMessageResponse,
    ListGroupMessagesResponse,
    SendGroupMessageRequest,
)

router = APIRouter(prefix="/groups", tags=["chat"])


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


def _to_response(message: Message, sender_display_name: str) -> GroupMessageResponse:
    return GroupMessageResponse(
        id=message.id,
        group_id=message.group_id,
        sender_user_id=message.sender_user_id,
        sender_display_name=sender_display_name,
        body=message.body,
        created_at=message.created_at,
        edited_at=message.edited_at,
    )


@router.post(
    "/{group_id}/messages",
    response_model=GroupMessageResponse,
    status_code=status.HTTP_201_CREATED,
)
async def send_group_message(
    group_id: uuid.UUID,
    body: SendGroupMessageRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> GroupMessageResponse:
    await _require_group_member(db, group_id, current_user)

    message_body = body.body.strip()
    if not message_body:
        raise _api_error(
            status.HTTP_400_BAD_REQUEST,
            "INVALID_MESSAGE_BODY",
            "Message body must not be empty.",
        )

    row = Message(
        group_id=group_id,
        sender_user_id=current_user.id,
        body=message_body,
    )
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return _to_response(row, current_user.display_name)


@router.get("/{group_id}/messages", response_model=ListGroupMessagesResponse)
async def list_group_messages(
    group_id: uuid.UUID,
    before: uuid.UUID | None = Query(default=None),
    limit: int = Query(default=50, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> ListGroupMessagesResponse:
    await _require_group_member(db, group_id, current_user)

    query = (
        select(Message, User.display_name)
        .join(User, User.id == Message.sender_user_id)
        .where(Message.group_id == group_id)
    )

    if before is not None:
        cursor_result = await db.execute(
            select(Message.id, Message.created_at).where(
                Message.group_id == group_id,
                Message.id == before,
            )
        )
        cursor_row = cursor_result.one_or_none()
        if cursor_row is None:
            raise _api_error(
                status.HTTP_404_NOT_FOUND,
                "MESSAGE_NOT_FOUND",
                "Message cursor was not found in this group.",
            )

        cursor_id, cursor_created_at = cursor_row
        query = query.where(
            or_(
                Message.created_at < cursor_created_at,
                and_(
                    Message.created_at == cursor_created_at,
                    Message.id < cursor_id,
                ),
            )
        )

    result = await db.execute(
        query.order_by(Message.created_at.desc(), Message.id.desc()).limit(limit + 1)
    )
    rows = result.all()
    has_more = len(rows) > limit
    page_rows = rows[:limit]

    items = [_to_response(message, sender_display_name) for message, sender_display_name in page_rows]
    next_before = items[-1].id if has_more and items else None

    return ListGroupMessagesResponse(
        items=items,
        has_more=has_more,
        next_before=next_before,
    )
