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

## Self-hosting deployment notes

### Startup + migration behavior

The API container runs `backend/entrypoint.sh`, which executes:

```sh
alembic upgrade head
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

This means migrations are applied automatically on API startup. If a migration fails, the API container will not finish booting.

### Data persistence and storage volume

`docker-compose.yml` persists app data in named Docker volumes:

| Volume | Contents |
|---|---|
| `postgres_data` | PostgreSQL database data directory |
| `storage_data` | Uploaded files (mounted at `/app/storage_data`) |

`docker-compose down` keeps these volumes. `docker-compose down -v` deletes them (data loss).

### Reverse proxy + HTTPS

For any non-local deployment, put a reverse proxy (Nginx, Caddy, Traefik, etc.) in front of the API:

1. Terminate TLS at the proxy (`https://...` for client traffic).
2. Forward traffic to HomeBurrow API on internal network (`api:8000`).
3. Expose only proxy ports publicly (typically 80/443), not Postgres.
4. Keep database reachable only from trusted internal containers/networks.

### Backup and restore

Create backups from the project root:

```sh
mkdir -p backups
docker-compose exec -T db pg_dump -U homeburrow -d homeburrow > backups/homeburrow.sql
docker-compose exec -T api sh -c "tar czf - -C /app/storage_data ." > backups/storage_data.tgz
```

Restore backups (stop clients/API writes first):

```sh
cat backups/homeburrow.sql | docker-compose exec -T db psql -U homeburrow -d homeburrow
cat backups/storage_data.tgz | docker-compose exec -T api sh -c "rm -rf /app/storage_data/* && tar xzf - -C /app/storage_data"
```

### Deployment safety defaults

Before exposing the service beyond local development:

1. Set a strong random `SECRET_KEY`.
2. Change default database credentials in `.env` / `docker-compose.yml`.
3. Do not expose Postgres (`5432`) on public interfaces.
4. Keep `.env` private and never commit it.
5. Use HTTPS at the reverse proxy.

---

## Mobile app

### Build and run Android

```sh
./gradlew :composeApp:assembleDebug
```

### Build and run iOS

Open `iosApp/` in Xcode and run, or use the IDE run configuration.

### API base URL for self-hosted servers

The mobile clients have development defaults and must be updated for your deployment URL:

| Platform | Current location | Current default | What to set for self-hosting |
|---|---|---|---|
| Android | `composeApp/build.gradle.kts` (`API_BASE_URL`) | `http://10.0.2.2:8000` | Your LAN IP or domain (prefer `https://...`) |
| iOS | `composeApp/src/iosMain/kotlin/dev/labstk/homeburrow/MainViewController.kt` (`baseUrl`) | `http://localhost:8000` | Your LAN IP or domain (prefer `https://...`) |

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
