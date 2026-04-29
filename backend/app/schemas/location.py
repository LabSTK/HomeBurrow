import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class PostCurrentLocationRequest(BaseModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    accuracy: float | None = Field(default=None, ge=0)


class CurrentLocationResponse(BaseModel):
    user_id: uuid.UUID
    group_id: uuid.UUID
    latitude: float
    longitude: float
    accuracy: float | None
    recorded_at: datetime
    updated_at: datetime
    sharing_enabled: bool


class SetLocationSharingRequest(BaseModel):
    sharing_enabled: bool


class LocationSharingResponse(BaseModel):
    sharing_enabled: bool
