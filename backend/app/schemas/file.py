import uuid
from datetime import datetime

from pydantic import BaseModel


class GroupFileResponse(BaseModel):
    id: uuid.UUID
    group_id: uuid.UUID
    uploader_user_id: uuid.UUID
    original_filename: str
    mime_type: str
    size_bytes: int
    created_at: datetime
