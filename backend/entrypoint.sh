#!/bin/sh
# TODO Phase 0: entrypoint script
# 1. Run database migrations:  alembic upgrade head
# 2. Start the API server:     exec uvicorn app.main:app --host 0.0.0.0 --port 8000
set -e

echo "Running database migrations..."
# alembic upgrade head

echo "Starting API server..."
# exec uvicorn app.main:app --host 0.0.0.0 --port 8000
