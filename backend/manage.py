"""Admin bootstrap CLI.

Usage:
    python manage.py create-admin --email <email> --password <password>

This is the mandatory first step before any API interaction is possible.
Run inside the Docker container:
    docker-compose exec api python manage.py create-admin --email admin@example.com --password <password>

The command is idempotent: re-running with the same email does not create a duplicate.
The bootstrap admin has must_change_password=False — they supply their own password.
"""

import argparse
import asyncio
import sys

from passlib.context import CryptContext
from sqlalchemy import select

from app.config import settings  # noqa: F401 — ensures env is loaded before DB init
from app.database import async_session
from app.models.user import User

_pwd_context = CryptContext(schemes=["argon2"], deprecated="auto")


async def create_admin(email: str, password: str) -> None:
    async with async_session() as session:
        result = await session.execute(select(User).where(User.email == email))
        existing = result.scalar_one_or_none()

        if existing is not None:
            if existing.is_admin:
                print(f"Admin user '{email}' already exists. Nothing to do.")
                return
            else:
                print(
                    f"ERROR: A non-admin user with email '{email}' already exists. "
                    "Promote them manually if intended.",
                    file=sys.stderr,
                )
                sys.exit(1)

        admin_user = User(
            email=email,
            display_name="Admin",
            password_hash=_pwd_context.hash(password),
            must_change_password=False,
            is_admin=True,
            token_version=0,
        )
        session.add(admin_user)
        await session.commit()
        print(f"Admin user '{email}' created successfully.")


def main() -> None:
    parser = argparse.ArgumentParser(description="HomeBurrow management commands")
    subparsers = parser.add_subparsers(dest="command")

    create_admin_parser = subparsers.add_parser("create-admin", help="Bootstrap the first admin user")
    create_admin_parser.add_argument("--email", required=True, help="Admin email address")
    create_admin_parser.add_argument("--password", required=True, help="Admin password")

    args = parser.parse_args()

    if args.command == "create-admin":
        asyncio.run(create_admin(args.email, args.password))
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
