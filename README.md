# HomeBurrow

A self-hostable private group sharing app for small trusted groups (family / friends / household).

Features: location sharing, group chat, file sharing — with privacy-first, self-hosted architecture.

---

## Architecture

| Component | Stack |
|---|---|
| Mobile | Kotlin Multiplatform + Compose Multiplatform (Android & iOS) |
| Backend | Python + FastAPI + async SQLAlchemy + PostgreSQL |
| Deployment | Docker Compose |

---

## Getting started

### Prerequisites

- Docker & Docker Compose
- Python 3.12+ (for local backend development without Docker)
- Android Studio / Xcode (for mobile development)

### 1. Configure environment

```sh
cp backend/.env.example .env
# Edit .env — at minimum set a strong SECRET_KEY
```

### 2. Start with Docker Compose

```sh
docker-compose up --build
```

The API will be available at `http://localhost:8000`.

### 3. Bootstrap the first admin user

Before the app can be used, create the initial admin account:

```sh
docker-compose exec api python manage.py create-admin --email admin@example.com --password <password>
```

This command is idempotent — re-running it will not create a duplicate.

---

## Backend local development (without Docker)

```sh
cd backend
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

Run the API (requires a running PostgreSQL):

```sh
alembic upgrade head
uvicorn app.main:app --reload
```

---

## Environment variables

| Variable | Description | Default in `.env.example` |
|---|---|---|
| `DATABASE_URL` | PostgreSQL connection string (asyncpg) | `postgresql+asyncpg://homeburrow:homeburrow@db:5432/homeburrow` |
| `SECRET_KEY` | JWT signing key (generate a random value) | placeholder |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | Access token lifetime | `15` |
| `REFRESH_TOKEN_EXPIRE_DAYS` | Refresh token lifetime | `30` |
| `STORAGE_PATH` | Path for uploaded file storage | `/app/storage_data` |
| `MAX_UPLOAD_BYTES` | Max upload size for group file uploads | `26214400` |

---

## Mobile app

### Build and run Android

```sh
./gradlew :composeApp:assembleDebug
```

### Build and run iOS

Open `iosApp/` in Xcode and run, or use the IDE run configuration.

---

## Project structure

```
HomeBurrow/
├── backend/                  # Python FastAPI backend
│   ├── app/
│   │   ├── models/           # SQLAlchemy ORM models
│   │   ├── schemas/          # Pydantic request/response schemas
│   │   ├── routers/          # API route handlers (auth, admin, groups, locations, chat, files)
│   │   └── storage/          # File storage abstraction + local disk implementation
│   ├── alembic/              # Database migrations
│   ├── manage.py             # Admin bootstrap CLI
│   └── requirements.txt
├── composeApp/               # KMP shared mobile code
│   └── src/
│       └── commonMain/kotlin/dev/labstk/homeburrow/
│           ├── di/           # Dependency injection (AppModule)
│           ├── network/      # Ktor API client + response models
│           ├── auth/         # Auth domain
│           ├── groups/       # Groups domain
│           ├── locations/    # Location sharing domain
│           ├── chat/         # Chat domain
│           └── files/        # File sharing domain
├── iosApp/                   # iOS entry point
└── docker-compose.yml
```
