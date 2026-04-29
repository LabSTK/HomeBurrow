from datetime import datetime, timedelta, timezone
from hashlib import sha256
import secrets
import uuid

from jose import jwt
from passlib.context import CryptContext

from app.config import settings

_pwd_context = CryptContext(schemes=["argon2"], deprecated="auto")


def hash_password(plain: str) -> str:
    return _pwd_context.hash(plain)


def verify_password(plain: str, hashed: str) -> bool:
    return _pwd_context.verify(plain, hashed)


def create_access_token(user_id: uuid.UUID, token_version: int) -> str:
    expire = datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    payload = {
        "sub": str(user_id),
        "token_version": token_version,
        "exp": expire,
    }
    return jwt.encode(payload, settings.SECRET_KEY, algorithm="HS256")


def generate_refresh_token() -> str:
    """Return a cryptographically secure random token string."""
    return secrets.token_urlsafe(48)


def hash_token(token: str) -> str:
    """SHA-256 hash a refresh token for safe DB storage."""
    return sha256(token.encode()).hexdigest()


def refresh_token_expiry() -> datetime:
    return datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
