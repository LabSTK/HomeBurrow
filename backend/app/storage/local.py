import asyncio
from pathlib import Path

from app.config import settings
from app.storage.base import StorageService


class LocalStorageService(StorageService):
    """
    Local disk storage implementation.

    Maps an opaque UUID key to <STORAGE_PATH>/<key> internally.
    The path mapping is an implementation detail — never exposed outside this class.
    CONCERN-8: storage_key in the DB is always just the UUID, never a filesystem path.
    """

    def __init__(self) -> None:
        self._root = Path(settings.STORAGE_PATH)
        self._root.mkdir(parents=True, exist_ok=True)

    def _path(self, key: str) -> Path:
        return self._root / key

    async def save(self, key: str, data: bytes) -> None:
        path = self._path(key)
        await asyncio.to_thread(path.write_bytes, data)

    async def read(self, key: str) -> bytes:
        path = self._path(key)
        if not await asyncio.to_thread(path.exists):
            raise FileNotFoundError(f"Storage key not found: {key}")
        return await asyncio.to_thread(path.read_bytes)

    async def delete(self, key: str) -> None:
        path = self._path(key)
        await asyncio.to_thread(lambda: path.unlink(missing_ok=True))

    async def exists(self, key: str) -> bool:
        return await asyncio.to_thread(self._path(key).exists)
