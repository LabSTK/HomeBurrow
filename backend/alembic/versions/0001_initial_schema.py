"""Initial schema — all MVP tables

Revision ID: 0001
Revises:
Create Date: 2026-04-29

Tables created:
    users
    groups
    group_memberships
    current_locations  — composite PK (user_id, group_id); see CONCERN-1
    messages
    files
    refresh_tokens

Foreign key policy (CONCERN-4):
    All FKs referencing groups.id use ondelete=RESTRICT.
    This is the safe default for MVP: deleting a group is blocked while any
    related rows exist (memberships, messages, files, current_locations).
    There is no DELETE /groups endpoint in MVP so this constraint will never
    be triggered in normal operation.

Location history note:
    current_locations is a live-snapshot table (one row per user per group,
    always upserted). A future location_history table can be added in a
    separate migration without modifying this table.
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("email", sa.String(), nullable=False),
        sa.Column("display_name", sa.String(), nullable=False),
        sa.Column("password_hash", sa.String(), nullable=False),
        sa.Column("must_change_password", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("is_admin", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column("token_version", sa.Integer(), nullable=False, server_default="0"),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.UniqueConstraint("email", name="uq_users_email"),
    )

    op.create_table(
        "groups",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("name", sa.String(), nullable=False),
        sa.Column(
            "created_by",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )

    op.create_table(
        "group_memberships",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        # CONCERN-4: RESTRICT — deleting a group is blocked while memberships exist
        sa.Column(
            "group_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("groups.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column(
            "role",
            sa.Enum("owner", "member", name="membership_role"),
            nullable=False,
        ),
        sa.Column(
            "joined_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )

    op.create_table(
        "current_locations",
        # CONCERN-1: composite primary key (user_id, group_id) — no surrogate id
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        # CONCERN-4: RESTRICT on groups.id
        sa.Column(
            "group_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("groups.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column("latitude", sa.Float(), nullable=False),
        sa.Column("longitude", sa.Float(), nullable=False),
        sa.Column("accuracy", sa.Float(), nullable=True),
        sa.Column("recorded_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        # CONCERN-2: per-group sharing toggle; see PUT /groups/{group_id}/me/location-sharing
        sa.Column("sharing_enabled", sa.Boolean(), nullable=False, server_default="false"),
        sa.PrimaryKeyConstraint("user_id", "group_id", name="pk_current_locations"),
    )

    op.create_table(
        "messages",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        # CONCERN-4: RESTRICT on groups.id
        sa.Column(
            "group_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("groups.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column(
            "sender_user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column("body", sa.Text(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.Column("edited_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "files",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        # CONCERN-4: RESTRICT on groups.id
        sa.Column(
            "group_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("groups.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column(
            "uploader_user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        # CONCERN-8: opaque UUID; never a filesystem path
        sa.Column("storage_key", sa.String(), nullable=False),
        sa.Column("original_filename", sa.String(), nullable=False),
        sa.Column("mime_type", sa.String(), nullable=False),
        sa.Column("size_bytes", sa.Integer(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )

    op.create_table(
        "refresh_tokens",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        # CASCADE: revoking a user removes all their refresh tokens automatically
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("token_hash", sa.String(), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked", sa.Boolean(), nullable=False, server_default="false"),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )


def downgrade() -> None:
    # Drop in reverse dependency order
    op.drop_table("refresh_tokens")
    op.drop_table("files")
    op.drop_table("messages")
    op.drop_table("current_locations")
    op.drop_table("group_memberships")
    op.drop_table("groups")
    op.drop_table("users")
    op.execute("DROP TYPE IF EXISTS membership_role")
