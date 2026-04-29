import uuid
from datetime import datetime

from pydantic import BaseModel, EmailStr


class UserResponse(BaseModel):
    id: uuid.UUID
    email: str
    display_name: str
    is_admin: bool
    must_change_password: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class CreateUserRequest(BaseModel):
    email: EmailStr
    display_name: str
    password: str
    is_admin: bool = False


class UpdateUserRequest(BaseModel):
    email: EmailStr | None = None
    display_name: str | None = None
    is_admin: bool | None = None


class ResetPasswordResponse(BaseModel):
    """New plain-text password returned once to the admin. Never stored."""
    new_password: str
