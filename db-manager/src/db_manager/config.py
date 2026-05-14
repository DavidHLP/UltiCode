"""Configuration management for database connection."""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse

from dotenv import load_dotenv


def find_env_file(start_path: Path | None = None) -> Path | None:
    """Search for .env file upward from start_path."""
    if start_path is None:
        start_path = Path.cwd()

    current = start_path.resolve()
    for parent in [current] + list(current.parents):
        env_path = parent / ".env"
        if env_path.exists():
            return env_path
    return None


def load_config() -> dict[str, Any]:
    """Load database configuration from .env file.

    Supports two formats:
    1. DATABASE_URL=mysql://user:pass@host:port/db
    2. Separate DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME
    """
    env_path = find_env_file(Path(__file__).parent.parent.parent)
    if env_path:
        load_dotenv(env_path)

    database_url = os.getenv("DATABASE_URL", "")

    if database_url:
        return _parse_database_url(database_url)

    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "23306")),
        "user": os.getenv("DB_USER", ""),
        "password": os.getenv("DB_PASSWORD", ""),
        "database": os.getenv("DB_NAME", "ulticode"),
    }


def _parse_database_url(url: str) -> dict[str, Any]:
    """Parse MySQL DATABASE_URL into components."""
    parsed = urlparse(url)

    query_params = parse_qs(parsed.query)

    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 23306,
        "user": unquote(parsed.username or ""),
        "password": unquote(parsed.password or ""),
        "database": parsed.path.lstrip("/") or "ulticode",
        "query": query_params,
    }


def get_database_url(config: dict[str, Any] | None = None) -> str:
    """Build MySQL connection URL from config dict (SQLAlchemy format)."""
    if config is None:
        config = load_config()

    user = config.get("user", "")
    password = config.get("password", "")
    host = config.get("host", "localhost")
    port = config.get("port", 23306)
    database = config.get("database", "ulticode")

    return f"mysql+pymysql://{user}:{password}@{host}:{port}/{database}"


def get_jdbc_url(config: dict[str, Any] | None = None) -> str:
    """Build JDBC connection URL from config dict (Flyway format).

    Flyway requires JDBC URLs, not SQLAlchemy URLs.
    """
    if config is None:
        config = load_config()

    user = config.get("user", "")
    password = config.get("password", "")
    host = config.get("host", "localhost")
    port = config.get("port", 23306)
    database = config.get("database", "ulticode")

    return (
        f"jdbc:mysql://{host}:{port}/{database}"
        f"?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8"
    )


if __name__ == "__main__":
    cfg = load_config()
    print(f"Host: {cfg['host']}")
    print(f"Port: {cfg['port']}")
    print(f"User: {cfg['user']}")
    print(f"Database: {cfg['database']}")
