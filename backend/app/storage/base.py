# TODO Phase 0: Storage abstraction
# Define abstract StorageService with:
#   generate_key() -> str       — returns a new opaque UUID string
#   save(key, data) -> None
#   read(key) -> bytes
#   delete(key) -> None
#   exists(key) -> bool
