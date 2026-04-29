# TODO Phase 2: Group and membership routes
# POST   /groups
# GET    /groups
# GET    /groups/{group_id}
# GET    /groups/{group_id}/members
# POST   /groups/{group_id}/members
# DELETE /groups/{group_id}/members/{user_id}

from fastapi import APIRouter

router = APIRouter(prefix="/groups", tags=["groups"])
