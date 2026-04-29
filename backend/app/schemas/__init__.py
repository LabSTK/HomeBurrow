from pydantic import BaseModel


class ErrorResponse(BaseModel):
    """Shared error envelope used by all endpoints.

    ``code`` is machine-readable (e.g. "MUST_CHANGE_PASSWORD") so clients can
    handle specific errors programmatically. ``message`` is human-readable.
    """

    code: str
    message: str