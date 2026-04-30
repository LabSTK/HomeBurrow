import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class SendGroupMessageRequest(BaseModel):
    body: str = Field(min_length=1)


class GroupMessageResponse(BaseModel):
    id: uuid.UUID
    group_id: uuid.UUID
    sender_user_id: uuid.UUID
    sender_display_name: str
    body: str
    created_at: datetime
    edited_at: datetime | None


class ListGroupMessagesResponse(BaseModel):
    items: list[GroupMessageResponse]
    has_more: bool
    next_before: uuid.UUID | None = None
