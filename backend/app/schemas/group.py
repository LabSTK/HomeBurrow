import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

MembershipRole = Literal["owner", "member"]


class CreateGroupRequest(BaseModel):
    name: str = Field(min_length=1, max_length=120)


class GroupSummaryResponse(BaseModel):
    id: uuid.UUID
    name: str
    created_by: uuid.UUID
    created_at: datetime
    my_role: MembershipRole


class GroupDetailResponse(BaseModel):
    id: uuid.UUID
    name: str
    created_by: uuid.UUID
    created_at: datetime
    my_role: MembershipRole


class GroupMemberResponse(BaseModel):
    user_id: uuid.UUID
    email: str
    display_name: str
    role: MembershipRole
    joined_at: datetime


class AddGroupMemberRequest(BaseModel):
    user_id: uuid.UUID
    role: MembershipRole = "member"
