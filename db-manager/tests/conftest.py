from __future__ import annotations

import os
from pathlib import Path
from typing import Any
from unittest.mock import MagicMock, patch

import pytest


@pytest.fixture
def mock_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in [
        "DATABASE_URL",
        "DB_HOST",
        "DB_PORT",
        "DB_USER",
        "DB_PASSWORD",
        "DB_NAME",
    ]:
        monkeypatch.delenv(key, raising=False)


@pytest.fixture
def tmp_env_file(tmp_path: Path) -> Path:
    return tmp_path / ".env"


@pytest.fixture
def mock_subprocess_run():
    with patch("db_manager.flyway_adapter.subprocess.run") as mock_run:
        yield mock_run


@pytest.fixture
def mock_flyway_adapter():
    adapter = MagicMock()
    adapter._use_docker = False
    adapter._flyway_path = "flyway"
    adapter.use_docker = False
    return adapter
