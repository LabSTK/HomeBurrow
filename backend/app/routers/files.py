import uuid
from urllib.parse import quote

from fastapi import APIRouter, Depends, File as FastapiFile, HTTPException, Response, UploadFile, status
from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.dependencies import get_current_user, get_db, get_storage
from app.models.file import File
from app.models.group import Group, GroupMembership
from app.models.user import User
from app.schemas import ErrorResponse, GroupFileResponse
from app.storage.base import StorageService

router = APIRouter(prefix="/groups", tags=["files"])


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


def _to_response(file: File) -> GroupFileResponse:
    return GroupFileResponse(
        id=file.id,
        group_id=file.group_id,
        uploader_user_id=file.uploader_user_id,
        original_filename=file.original_filename,
        mime_type=file.mime_type,
        size_bytes=file.size_bytes,
        created_at=file.created_at,
    )


def _content_disposition(filename: str) -> str:
    safe_filename = filename.replace('"', "").replace("\\", "").strip() or "download"
    encoded = quote(safe_filename)
    return f"attachment; filename=\"{safe_filename}\"; filename*=UTF-8''{encoded}"


@router.post(
    "/{group_id}/files",
    response_model=GroupFileResponse,
    status_code=status.HTTP_201_CREATED,
)
async def upload_group_file(
    group_id: uuid.UUID,
    upload: UploadFile = FastapiFile(...),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    storage: StorageService = Depends(get_storage),
) -> GroupFileResponse:
    await _require_group_member(db, group_id, current_user)

    original_filename = (upload.filename or "").strip()
    if not original_filename:
        raise _api_error(
            status.HTTP_400_BAD_REQUEST,
            "INVALID_FILENAME",
            "Uploaded file must have a filename.",
        )

    max_bytes = settings.MAX_UPLOAD_BYTES
    payload = await upload.read(max_bytes + 1)
    await upload.close()

    if not payload:
        raise _api_error(
            status.HTTP_400_BAD_REQUEST,
            "EMPTY_FILE",
            "Uploaded file is empty.",
        )
    if len(payload) > max_bytes:
        raise _api_error(
            status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            "FILE_TOO_LARGE",
            f"File exceeds maximum allowed size of {max_bytes} bytes.",
        )

    mime_type = (upload.content_type or "").strip() or "application/octet-stream"
    storage_key = storage.generate_key()

    try:
        await storage.save(storage_key, payload)
    except OSError:
        raise _api_error(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "FILE_STORAGE_WRITE_FAILED",
            "File could not be persisted.",
        )

    row = File(
        group_id=group_id,
        uploader_user_id=current_user.id,
        storage_key=storage_key,
        original_filename=original_filename,
        mime_type=mime_type,
        size_bytes=len(payload),
    )
    db.add(row)

    try:
        await db.commit()
    except SQLAlchemyError:
        await db.rollback()
        try:
            await storage.delete(storage_key)
        except OSError:
            raise _api_error(
                status.HTTP_500_INTERNAL_SERVER_ERROR,
                "FILE_PERSIST_AND_CLEANUP_FAILED",
                "File metadata persistence failed and cleanup did not complete.",
            )
        raise _api_error(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "FILE_METADATA_PERSIST_FAILED",
            "File metadata could not be persisted.",
        )

    await db.refresh(row)
    return _to_response(row)


@router.get(
    "/{group_id}/files",
    response_model=list[GroupFileResponse],
)
async def list_group_files(
    group_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> list[GroupFileResponse]:
    await _require_group_member(db, group_id, current_user)

    result = await db.execute(
        select(File)
        .where(File.group_id == group_id)
        .order_by(File.created_at.desc())
    )
    rows = result.scalars().all()
    return [_to_response(row) for row in rows]


@router.get(
    "/{group_id}/files/{file_id}/download",
)
async def download_group_file(
    group_id: uuid.UUID,
    file_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    storage: StorageService = Depends(get_storage),
) -> Response:
    await _require_group_member(db, group_id, current_user)

    result = await db.execute(
        select(File).where(
            File.id == file_id,
            File.group_id == group_id,
        )
    )
    row = result.scalar_one_or_none()
    if row is None:
        raise _api_error(
            status.HTTP_404_NOT_FOUND,
            "FILE_NOT_FOUND",
            "File not found in this group.",
        )

    try:
        payload = await storage.read(row.storage_key)
    except FileNotFoundError:
        raise _api_error(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "FILE_STORAGE_MISSING",
            "Stored file content is missing.",
        )
    except OSError:
        raise _api_error(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "FILE_STORAGE_READ_FAILED",
            "Stored file content could not be read.",
        )

    return Response(
        content=payload,
        media_type=row.mime_type,
        headers={
            "Content-Disposition": _content_disposition(row.original_filename),
            "Content-Length": str(len(payload)),
        },
    )
