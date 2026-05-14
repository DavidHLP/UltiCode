"""Flyway wrapper for database migration management."""

from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path
from typing import Any

from dotenv import load_dotenv

from .config import find_env_file, get_jdbc_url, load_config


class FlywayAdapter:
    """Wrapper for Flyway CLI operations (supports Docker and local CLI)."""

    def __init__(self, flyway_path: str | None = None) -> None:
        """Initialize Flyway adapter.

        Args:
            flyway_path: Path to Flyway CLI binary. If None, auto-detects.
        """
        self._db_manager_dir = Path(__file__).parent.parent.parent
        self._migrations_dir = self._db_manager_dir / "migrations"
        self._flyway_path = flyway_path or self._find_flyway()
        self._use_docker = self._flyway_path == "docker"
        self._load_environment()

    def _find_flyway(self) -> str:
        """Find Flyway CLI binary, falling back to Docker."""
        possible_paths = [
            "flyway",
            "/usr/local/bin/flyway",
            "/opt/flyway/bin/flyway",
            str(self._db_manager_dir / "flyway" / "flyway"),
        ]

        for path in possible_paths:
            if shutil.which(path):
                return path

        if shutil.which("docker"):
            return "docker"

        return "flyway"

    def _load_environment(self) -> None:
        """Load database configuration from .env file."""
        env_path = find_env_file(self._db_manager_dir)
        if env_path:
            load_dotenv(env_path)

    def _get_flyway_conf_path(self) -> Path:
        """Get path to flyway.conf file."""
        return self._db_manager_dir / "flyway.conf"

    def _build_docker_command(self, operation: str, **kwargs: Any) -> list[str]:
        """Build Docker command for Flyway."""
        jdbc_url = get_jdbc_url()
        user = os.getenv("DB_USER", "")
        password = os.getenv("DB_PASSWORD", "")

        cmd = [
            "docker", "run", "--rm", "--network", "host",
            "-v", f"{self._migrations_dir}:/flyway/migrations",
            "-e", f"FLYWAY_URL={jdbc_url}",
            "-e", f"FLYWAY_USER={user}",
            "-e", f"FLYWAY_PASSWORD={password}",
            "flyway/flyway:11-alpine",
            f"-locations=filesystem:/flyway/migrations",
        ]

        for key, value in kwargs.items():
            if value is not None and value is not False:
                if isinstance(value, bool):
                    cmd.append(f"-{key}=true")
                else:
                    cmd.append(f"-{key}={value}")

        cmd.append(operation)
        return cmd

    def _build_cli_command(self, operation: str, **kwargs: Any) -> list[str]:
        """Build CLI command for Flyway."""
        cmd = [self._flyway_path]

        conf_path = self._get_flyway_conf_path()
        if conf_path.exists():
            cmd.append(f"-configFiles={conf_path}")

        jdbc_url = get_jdbc_url()
        user = os.getenv("DB_USER", "")

        cmd.append(f"-url={jdbc_url}")
        if user:
            cmd.append(f"-user={user}")

        cmd.append(f"-locations=filesystem:{self._migrations_dir}")

        for key, value in kwargs.items():
            if value is not None and value is not False:
                if isinstance(value, bool):
                    cmd.append(f"-{key}=true")
                else:
                    cmd.append(f"-{key}={value}")

        cmd.append(operation)
        return cmd

    def _build_command(self, operation: str, **kwargs: Any) -> list[str]:
        """Build Flyway command with appropriate executor."""
        if self._use_docker:
            return self._build_docker_command(operation, **kwargs)
        return self._build_cli_command(operation, **kwargs)

    def _run(self, operation: str, **kwargs: Any) -> dict[str, Any]:
        """Run Flyway command and return result.

        Args:
            operation: Flyway operation (migrate, info, repair, etc.)
            **kwargs: Additional Flyway options

        Returns:
            Dict with returncode, stdout, stderr
        """
        cmd = self._build_command(operation, **kwargs)

        env = os.environ.copy()
        password = os.getenv("DB_PASSWORD", "")
        if password:
            env["FLYWAY_PASSWORD"] = password

        result = subprocess.run(
            cmd,
            cwd=self._db_manager_dir,
            capture_output=True,
            text=True,
            timeout=300,
            env=env,
        )

        return {
            "returncode": result.returncode,
            "stdout": result.stdout,
            "stderr": result.stderr,
            "command": " ".join(cmd),
        }

    def migrate(self, dry_run: bool = False, out_of_order: bool = False) -> dict[str, Any]:
        """Run Flyway migrate.

        Args:
            dry_run: If True, validate without applying changes
            out_of_order: If True, allow out-of-order migrations

        Returns:
            Migration result
        """
        if dry_run:
            return self._run("validate")
        kwargs = {}
        if out_of_order:
            kwargs["outOfOrder"] = True
        return self._run("migrate", **kwargs)

    def info(self) -> dict[str, Any]:
        """Get Flyway migration info.

        Returns:
            Migration status info
        """
        return self._run("info")

    def repair(self) -> dict[str, Any]:
        """Repair Flyway metadata table.

        Returns:
            Repair result
        """
        return self._run("repair")

    def baseline(self) -> dict[str, Any]:
        """Create baseline for existing database.

        Returns:
            Baseline result
        """
        return self._run("baseline")

    def clean(self, force: bool = False) -> dict[str, Any]:
        """Drop all database objects.

        Args:
            force: Skip confirmation if True

        Returns:
            Clean result
        """
        return self._run("clean", cleanDisabled="false" if force else None)

    def validate(self) -> dict[str, Any]:
        """Validate migration state.

        Returns:
            Validation result
        """
        return self._run("validate")

    def check_flyway_installed(self) -> bool:
        """Check if Flyway CLI or Docker is available.

        Returns:
            True if Flyway is available
        """
        if self._use_docker:
            try:
                result = subprocess.run(
                    ["docker", "images", "flyway/flyway"],
                    capture_output=True,
                    text=True,
                    timeout=10,
                )
                return result.returncode == 0 and "flyway/flyway" in result.stdout
            except (subprocess.TimeoutExpired, FileNotFoundError):
                return False

        try:
            result = subprocess.run(
                [self._flyway_path, "-v"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            return result.returncode == 0
        except (subprocess.TimeoutExpired, FileNotFoundError):
            return False

    @property
    def use_docker(self) -> bool:
        """Whether using Docker to run Flyway."""
        return self._use_docker


def get_flyway_adapter() -> FlywayAdapter:
    """Get Flyway adapter instance.

    Returns:
        Configured FlywayAdapter
    """
    return FlywayAdapter()
