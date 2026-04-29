from abc import ABC, abstractmethod
import uuid


class StorageService(ABC):
    def generate_key(self) -> str:
        """Return a new opaque UUID string. Callers must not interpret its format."""
        return str(uuid.uuid4())

    @abstractmethod
    async def save(self, key: str, data: bytes) -> None:
        """Persist data under the given key."""

    @abstractmethod
    async def read(self, key: str) -> bytes:
        """Return the data stored under key. Raises FileNotFoundError if absent."""

    @abstractmethod
    async def delete(self, key: str) -> None:
        """Remove the data stored under key. No-op if key does not exist."""

    @abstractmethod
    async def exists(self, key: str) -> bool:
        """Return True if data exists for the given key."""
