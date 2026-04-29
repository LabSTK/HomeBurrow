from pydantic import BaseModel

from app.schemas.auth import (
    AccessTokenResponse,
    ChangePasswordRequest,
    LoginRequest,
    LogoutRequest,
    RefreshRequest,
    TokenResponse,
)
from app.schemas.user import (
    CreateUserRequest,
    ResetPasswordResponse,
    UpdateUserRequest,
    UserResponse,
)
from app.schemas.group import (
    AddGroupMemberRequest,
    GroupDetailResponse,
    GroupMemberResponse,
    GroupSummaryResponse,
    CreateGroupRequest,
)

__all__ = [
    "ErrorResponse",
    "AccessTokenResponse",
    "ChangePasswordRequest",
    "LoginRequest",
    "LogoutRequest",
    "RefreshRequest",
    "TokenResponse",
    "CreateUserRequest",
    "ResetPasswordResponse",
    "UpdateUserRequest",
    "UserResponse",
    "CreateGroupRequest",
    "GroupSummaryResponse",
    "GroupDetailResponse",
    "GroupMemberResponse",
    "AddGroupMemberRequest",
]


class ErrorResponse(BaseModel):
    """Shared error envelope used by all endpoints.

    ``code`` is machine-readable (e.g. "MUST_CHANGE_PASSWORD") so clients can
    handle specific errors programmatically. ``message`` is human-readable.
    """

    code: str
    message: str
