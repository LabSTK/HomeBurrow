# TODO Phase 1: Auth routes
# POST /auth/login
# POST /auth/refresh
# POST /auth/logout
# GET  /auth/me
# POST /auth/change-password

from fastapi import APIRouter

router = APIRouter(prefix="/auth", tags=["auth"])
