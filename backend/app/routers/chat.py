# TODO Phase 4: Group chat routes
# GET  /groups/{group_id}/messages  (?before=<message_id>&limit=<n>)
# POST /groups/{group_id}/messages

from fastapi import APIRouter

router = APIRouter(tags=["chat"])
