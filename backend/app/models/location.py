import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, PrimaryKeyConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class CurrentLocation(Base):
    """
    Live location snapshot — one row per (user, group) pair.

    CONCERN-1: composite primary key (user_id, group_id); no surrogate key.
    CONCERN-2: sharing_enabled is per-group, toggled via
               PUT /groups/{group_id}/me/location-sharing.

    Future location history (non-goal for MVP) will be a separate
    `location_history` table with its own UUID PK. No schema changes to this
    table will be required when that feature is added.
    """

    __tablename__ = "current_locations"
    __table_args__ = (PrimaryKeyConstraint("user_id", "group_id"),)

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="RESTRICT"), nullable=False
    )
    # CONCERN-4: ondelete RESTRICT on groups.id
    group_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("groups.id", ondelete="RESTRICT"), nullable=False
    )
    latitude: Mapped[float] = mapped_column(Float, nullable=False)
    longitude: Mapped[float] = mapped_column(Float, nullable=False)
    accuracy: Mapped[float | None] = mapped_column(Float, nullable=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
    sharing_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
