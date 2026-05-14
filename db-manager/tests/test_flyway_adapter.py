from __future__ import annotations

import os
import subprocess
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from db_manager.flyway_adapter import FlywayAdapter, get_flyway_adapter


class TestFlywayAdapterInit:
    def test_init_with_explicit_path(self) -> None:
        adapter = FlywayAdapter(flyway_path="/custom/flyway")
        assert adapter._flyway_path == "/custom/flyway"
        assert adapter._use_docker is False

    @patch("db_manager.flyway_adapter.shutil.which")
    def test_init_auto_detects_flyway(self, mock_which: MagicMock) -> None:
        mock_which.side_effect = lambda x: x if x == "flyway" else None
        adapter = FlywayAdapter()
        assert adapter._flyway_path == "flyway"
        assert adapter._use_docker is False

    @patch("db_manager.flyway_adapter.shutil.which")
    def test_init_falls_back_to_docker(self, mock_which: MagicMock) -> None:
        mock_which.side_effect = lambda x: x if x == "docker" else None
        adapter = FlywayAdapter()
        assert adapter._flyway_path == "docker"
        assert adapter._use_docker is True

    @patch("db_manager.flyway_adapter.shutil.which")
    def test_init_falls_back_to_flyway_string(self, mock_which: MagicMock) -> None:
        mock_which.return_value = None
        adapter = FlywayAdapter()
        assert adapter._flyway_path == "flyway"
        assert adapter._use_docker is False


class TestBuildDockerCommand:
    @patch.dict(os.environ, {"DB_USER": "du", "DB_PASSWORD": "dp"}, clear=False)
    @patch("db_manager.flyway_adapter.get_jdbc_url")
    def test_build_docker_command_basic(self, mock_get_jdbc: MagicMock) -> None:
        mock_get_jdbc.return_value = "jdbc:mysql://h:3306/db?p=1"
        adapter = FlywayAdapter(flyway_path="docker")
        cmd = adapter._build_docker_command("migrate")
        assert cmd[0] == "docker"
        assert "flyway/flyway:11-alpine" in cmd
        assert "-e" in cmd
        assert "FLYWAY_URL=jdbc:mysql://h:3306/db?p=1" in cmd
        assert "FLYWAY_USER=du" in cmd
        assert "FLYWAY_PASSWORD=dp" in cmd
        assert cmd[-1] == "migrate"

    @patch.dict(os.environ, {"DB_USER": "u", "DB_PASSWORD": "p"}, clear=False)
    @patch("db_manager.flyway_adapter.get_jdbc_url")
    def test_build_docker_command_with_kwargs(self, mock_get_jdbc: MagicMock) -> None:
        mock_get_jdbc.return_value = "jdbc:mysql://h:3306/db"
        adapter = FlywayAdapter(flyway_path="docker")
        cmd = adapter._build_docker_command("migrate", outOfOrder=True, target="2")
        assert "-outOfOrder=true" in cmd
        assert "-target=2" in cmd

    @patch.dict(os.environ, {"DB_USER": "u", "DB_PASSWORD": "p"}, clear=False)
    @patch("db_manager.flyway_adapter.get_jdbc_url")
    def test_build_docker_command_skips_none_and_false(self, mock_get_jdbc: MagicMock) -> None:
        mock_get_jdbc.return_value = "jdbc:mysql://h:3306/db"
        adapter = FlywayAdapter(flyway_path="docker")
        cmd = adapter._build_docker_command("migrate", cleanDisabled=None, skip=False)
        assert "-cleanDisabled" not in cmd
        assert "-skip" not in cmd


class TestBuildCliCommand:
    @patch.dict(os.environ, {"DB_USER": "cu"}, clear=False)
    @patch("db_manager.flyway_adapter.get_jdbc_url")
    def test_build_cli_command_without_conf(self, mock_get_jdbc: MagicMock, tmp_path: Path) -> None:
        mock_get_jdbc.return_value = "jdbc:mysql://h:3306/db"
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter._db_manager_dir = tmp_path
        cmd = adapter._build_cli_command("info")
        assert cmd[0] == "flyway"
        assert "-url=jdbc:mysql://h:3306/db" in cmd
        assert "-user=cu" in cmd
        assert cmd[-1] == "info"

    @patch.dict(os.environ, {"DB_USER": ""}, clear=False)
    @patch("db_manager.flyway_adapter.get_jdbc_url")
    def test_build_cli_command_omits_empty_user(self, mock_get_jdbc: MagicMock, tmp_path: Path) -> None:
        mock_get_jdbc.return_value = "jdbc:mysql://h:3306/db"
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter._db_manager_dir = tmp_path
        cmd = adapter._build_cli_command("info")
        assert "-user=" not in cmd

    @patch.dict(os.environ, {"DB_USER": "u"}, clear=False)
    @patch("db_manager.flyway_adapter.get_jdbc_url")
    def test_build_cli_command_with_conf_file(self, mock_get_jdbc: MagicMock, tmp_path: Path) -> None:
        mock_get_jdbc.return_value = "jdbc:mysql://h:3306/db"
        conf = tmp_path / "flyway.conf"
        conf.write_text("flyway.url=test\n")
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter._db_manager_dir = tmp_path
        cmd = adapter._build_cli_command("info")
        assert f"-configFiles={conf}" in cmd

    @patch.dict(os.environ, {"DB_USER": "u"}, clear=False)
    @patch("db_manager.flyway_adapter.get_jdbc_url")
    def test_build_cli_command_with_kwargs(self, mock_get_jdbc: MagicMock, tmp_path: Path) -> None:
        mock_get_jdbc.return_value = "jdbc:mysql://h:3306/db"
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter._db_manager_dir = tmp_path
        cmd = adapter._build_cli_command("migrate", outOfOrder=True)
        assert "-outOfOrder=true" in cmd


class TestRun:
    @patch("db_manager.flyway_adapter.subprocess.run")
    @patch.dict(os.environ, {"DB_PASSWORD": "secret"}, clear=False)
    def test_run_sets_password_env(self, mock_run: MagicMock) -> None:
        mock_run.return_value = MagicMock(returncode=0, stdout="ok", stderr="")
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter._db_manager_dir = Path("/tmp")
        result = adapter._run("info")
        call_kwargs = mock_run.call_args[1]
        assert call_kwargs["env"]["FLYWAY_PASSWORD"] == "secret"
        assert result["returncode"] == 0
        assert result["stdout"] == "ok"

    @patch("db_manager.flyway_adapter.subprocess.run")
    @patch.dict(os.environ, {"DB_PASSWORD": ""}, clear=False)
    def test_run_without_password(self, mock_run: MagicMock) -> None:
        mock_run.return_value = MagicMock(returncode=0, stdout="ok", stderr="")
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter._db_manager_dir = Path("/tmp")
        result = adapter._run("info")
        call_kwargs = mock_run.call_args[1]
        assert "FLYWAY_PASSWORD" not in call_kwargs["env"]

    @patch("db_manager.flyway_adapter.subprocess.run")
    def test_run_returns_error_result(self, mock_run: MagicMock) -> None:
        mock_run.return_value = MagicMock(returncode=1, stdout="", stderr="error")
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter._db_manager_dir = Path("/tmp")
        result = adapter._run("migrate")
        assert result["returncode"] == 1
        assert result["stderr"] == "error"
        assert "command" in result


class TestMigrate:
    @patch.object(FlywayAdapter, "_run")
    def test_migrate_calls_run(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.migrate()
        mock_run.assert_called_once_with("migrate")

    @patch.object(FlywayAdapter, "_run")
    def test_migrate_dry_run_calls_validate(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.migrate(dry_run=True)
        mock_run.assert_called_once_with("validate")

    @patch.object(FlywayAdapter, "_run")
    def test_migrate_out_of_order(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.migrate(out_of_order=True)
        mock_run.assert_called_once_with("migrate", outOfOrder=True)


class TestInfo:
    @patch.object(FlywayAdapter, "_run")
    def test_info_calls_run(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.info()
        mock_run.assert_called_once_with("info")


class TestRepair:
    @patch.object(FlywayAdapter, "_run")
    def test_repair_calls_run(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.repair()
        mock_run.assert_called_once_with("repair")


class TestBaseline:
    @patch.object(FlywayAdapter, "_run")
    def test_baseline_calls_run(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.baseline()
        mock_run.assert_called_once_with("baseline")


class TestClean:
    @patch.object(FlywayAdapter, "_run")
    def test_clean_force(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.clean(force=True)
        mock_run.assert_called_once_with("clean", cleanDisabled="false")

    @patch.object(FlywayAdapter, "_run")
    def test_clean_no_force(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.clean(force=False)
        mock_run.assert_called_once_with("clean", cleanDisabled=None)


class TestValidate:
    @patch.object(FlywayAdapter, "_run")
    def test_validate_calls_run(self, mock_run: MagicMock) -> None:
        mock_run.return_value = {"returncode": 0}
        adapter = FlywayAdapter(flyway_path="flyway")
        adapter.validate()
        mock_run.assert_called_once_with("validate")


class TestCheckFlywayInstalled:
    @patch("db_manager.flyway_adapter.subprocess.run")
    def test_docker_installed(self, mock_run: MagicMock) -> None:
        mock_run.return_value = MagicMock(returncode=0, stdout="flyway/flyway\n")
        adapter = FlywayAdapter(flyway_path="docker")
        assert adapter.check_flyway_installed() is True

    @patch("db_manager.flyway_adapter.subprocess.run")
    def test_docker_not_installed(self, mock_run: MagicMock) -> None:
        mock_run.return_value = MagicMock(returncode=1, stdout="")
        adapter = FlywayAdapter(flyway_path="docker")
        assert adapter.check_flyway_installed() is False

    @patch("db_manager.flyway_adapter.subprocess.run")
    def test_docker_timeout(self, mock_run: MagicMock) -> None:
        mock_run.side_effect = subprocess.TimeoutExpired(cmd=["docker"], timeout=10)
        adapter = FlywayAdapter(flyway_path="docker")
        assert adapter.check_flyway_installed() is False

    @patch("db_manager.flyway_adapter.subprocess.run")
    def test_cli_installed(self, mock_run: MagicMock) -> None:
        mock_run.return_value = MagicMock(returncode=0, stdout="Flyway 9.0")
        adapter = FlywayAdapter(flyway_path="flyway")
        assert adapter.check_flyway_installed() is True

    @patch("db_manager.flyway_adapter.subprocess.run")
    def test_cli_not_installed(self, mock_run: MagicMock) -> None:
        mock_run.return_value = MagicMock(returncode=1, stdout="")
        adapter = FlywayAdapter(flyway_path="flyway")
        assert adapter.check_flyway_installed() is False

    @patch("db_manager.flyway_adapter.subprocess.run")
    def test_cli_timeout(self, mock_run: MagicMock) -> None:
        mock_run.side_effect = subprocess.TimeoutExpired(cmd=["flyway"], timeout=10)
        adapter = FlywayAdapter(flyway_path="flyway")
        assert adapter.check_flyway_installed() is False

    @patch("db_manager.flyway_adapter.subprocess.run")
    def test_cli_file_not_found(self, mock_run: MagicMock) -> None:
        mock_run.side_effect = FileNotFoundError()
        adapter = FlywayAdapter(flyway_path="flyway")
        assert adapter.check_flyway_installed() is False


class TestUseDockerProperty:
    def test_use_docker_true(self) -> None:
        adapter = FlywayAdapter(flyway_path="docker")
        assert adapter.use_docker is True

    def test_use_docker_false(self) -> None:
        adapter = FlywayAdapter(flyway_path="flyway")
        assert adapter.use_docker is False


class TestGetFlywayAdapter:
    def test_returns_instance(self) -> None:
        adapter = get_flyway_adapter()
        assert isinstance(adapter, FlywayAdapter)
