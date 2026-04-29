from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.routers import admin, auth, chat, files, groups, locations
from app.schemas import ErrorResponse

app = FastAPI(title="HomeBurrow", version="0.1.0")

app.include_router(auth.router)
app.include_router(admin.router)
app.include_router(groups.router)
app.include_router(locations.router)
app.include_router(chat.router)
app.include_router(files.router)


@app.get("/health", tags=["ops"])
async def health() -> dict:
    return {"status": "ok"}


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(
            code="INTERNAL_ERROR",
            message="An unexpected error occurred.",
        ).model_dump(),
    )
