# TODO Phase 5: File sharing routes
# POST /groups/{group_id}/files
# GET  /groups/{group_id}/files
# GET  /groups/{group_id}/files/{file_id}/download

from fastapi import APIRouter

router = APIRouter(tags=["files"])
