from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    DATABASE_URL: str
    SECRET_KEY: str
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30
    STORAGE_PATH: str

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
