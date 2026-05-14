from __future__ import annotations

import subprocess
from unittest.mock import MagicMock, patch

import pytest
from click.testing import CliRunner

from db_manager.cli import main


@pytest.fixture
def runner() -> CliRunner:
    return CliRunner()


class TestMain:
    def test_main_help(self, runner: CliRunner) -> None:
        result = runner.invoke(main, ["--help"])
        assert result.exit_code == 0
        assert "UltiCode Database Management Tool" in result.output

    def test_main_version(self, runner: CliRunner) -> None:
        result = runner.invoke(main, ["--version"])
        assert result.exit_code == 0
        assert "0.3.0" in result.output


class TestMigrateCommand:
    @patch("db_manager.cli.migrate_op")
    def test_migrate_success(self, mock_migrate: MagicMock, runner: CliRunner) -> None:
        mock_migrate.return_value = {"success": 1, "errors": 0}
        result = runner.invoke(main, ["migrate"])
        assert result.exit_code == 0
        mock_migrate.assert_called_once_with(dry_run=False)

    @patch("db_manager.cli.migrate_op")
    def test_migrate_dry_run(self, mock_migrate: MagicMock, runner: CliRunner) -> None:
        mock_migrate.return_value = {"success": 1, "errors": 0}
        result = runner.invoke(main, ["migrate", "--dry-run"])
        assert result.exit_code == 0
        mock_migrate.assert_called_once_with(dry_run=True)

    @patch("db_manager.cli.migrate_op")
    def test_migrate_with_errors(self, mock_migrate: MagicMock, runner: CliRunner) -> None:
        mock_migrate.return_value = {"success": 0, "errors": 1}
        result = runner.invoke(main, ["migrate"])
        assert result.exit_code == 1

    @patch("db_manager.cli.migrate_op")
    def test_migrate_runtime_error(self, mock_migrate: MagicMock, runner: CliRunner) -> None:
        mock_migrate.side_effect = RuntimeError("migration failed")
        result = runner.invoke(main, ["migrate"])
        assert result.exit_code == 1
        assert "migration failed" in result.output

    @patch("db_manager.cli.migrate_op")
    def test_migrate_timeout(self, mock_migrate: MagicMock, runner: CliRunner) -> None:
        mock_migrate.side_effect = subprocess.TimeoutExpired(cmd=["flyway"], timeout=300)
        result = runner.invoke(main, ["migrate"])
        assert result.exit_code == 1


class TestInfoCommand:
    @patch("db_manager.cli.info_op")
    def test_info_success(self, mock_info: MagicMock, runner: CliRunner) -> None:
        mock_info.return_value = {"success": 1, "errors": 0}
        result = runner.invoke(main, ["info"])
        assert result.exit_code == 0

    @patch("db_manager.cli.info_op")
    def test_info_with_errors(self, mock_info: MagicMock, runner: CliRunner) -> None:
        mock_info.return_value = {"success": 0, "errors": 1}
        result = runner.invoke(main, ["info"])
        assert result.exit_code == 1

    @patch("db_manager.cli.info_op")
    def test_info_runtime_error(self, mock_info: MagicMock, runner: CliRunner) -> None:
        mock_info.side_effect = RuntimeError("info failed")
        result = runner.invoke(main, ["info"])
        assert result.exit_code == 1


class TestRepairCommand:
    @patch("db_manager.cli.repair_op")
    def test_repair_success(self, mock_repair: MagicMock, runner: CliRunner) -> None:
        mock_repair.return_value = {"success": 1, "errors": 0}
        result = runner.invoke(main, ["repair"])
        assert result.exit_code == 0

    @patch("db_manager.cli.repair_op")
    def test_repair_with_errors(self, mock_repair: MagicMock, runner: CliRunner) -> None:
        mock_repair.return_value = {"success": 0, "errors": 1}
        result = runner.invoke(main, ["repair"])
        assert result.exit_code == 1

    @patch("db_manager.cli.repair_op")
    def test_repair_runtime_error(self, mock_repair: MagicMock, runner: CliRunner) -> None:
        mock_repair.side_effect = RuntimeError("repair failed")
        result = runner.invoke(main, ["repair"])
        assert result.exit_code == 1


class TestBaselineCommand:
    @patch("db_manager.cli.baseline_op")
    def test_baseline_success(self, mock_baseline: MagicMock, runner: CliRunner) -> None:
        mock_baseline.return_value = {"success": 1, "errors": 0}
        result = runner.invoke(main, ["baseline"])
        assert result.exit_code == 0

    @patch("db_manager.cli.baseline_op")
    def test_baseline_with_errors(self, mock_baseline: MagicMock, runner: CliRunner) -> None:
        mock_baseline.return_value = {"success": 0, "errors": 1}
        result = runner.invoke(main, ["baseline"])
        assert result.exit_code == 1

    @patch("db_manager.cli.baseline_op")
    def test_baseline_runtime_error(self, mock_baseline: MagicMock, runner: CliRunner) -> None:
        mock_baseline.side_effect = RuntimeError("baseline failed")
        result = runner.invoke(main, ["baseline"])
        assert result.exit_code == 1


class TestCleanCommand:
    @patch("db_manager.cli.clean_op")
    def test_clean_force(self, mock_clean: MagicMock, runner: CliRunner) -> None:
        mock_clean.return_value = {"success": 1, "errors": 0}
        result = runner.invoke(main, ["clean", "--force"])
        assert result.exit_code == 0
        mock_clean.assert_called_once_with(force=True)

    @patch("db_manager.cli.clean_op")
    def test_clean_without_force(self, mock_clean: MagicMock, runner: CliRunner) -> None:
        mock_clean.return_value = {"success": 1, "errors": 0}
        result = runner.invoke(main, ["clean"])
        assert result.exit_code == 0
        mock_clean.assert_called_once_with(force=False)

    @patch("db_manager.cli.clean_op")
    def test_clean_with_errors(self, mock_clean: MagicMock, runner: CliRunner) -> None:
        mock_clean.return_value = {"success": 0, "errors": 1}
        result = runner.invoke(main, ["clean", "--force"])
        assert result.exit_code == 1

    @patch("db_manager.cli.clean_op")
    def test_clean_runtime_error(self, mock_clean: MagicMock, runner: CliRunner) -> None:
        mock_clean.side_effect = RuntimeError("clean failed")
        result = runner.invoke(main, ["clean", "--force"])
        assert result.exit_code == 1


class TestValidateCommand:
    @patch("db_manager.cli.validate_op")
    def test_validate_success(self, mock_validate: MagicMock, runner: CliRunner) -> None:
        mock_validate.return_value = {"success": 1, "errors": 0}
        result = runner.invoke(main, ["validate"])
        assert result.exit_code == 0

    @patch("db_manager.cli.validate_op")
    def test_validate_with_errors(self, mock_validate: MagicMock, runner: CliRunner) -> None:
        mock_validate.return_value = {"success": 0, "errors": 1}
        result = runner.invoke(main, ["validate"])
        assert result.exit_code == 1

    @patch("db_manager.cli.validate_op")
    def test_validate_runtime_error(self, mock_validate: MagicMock, runner: CliRunner) -> None:
        mock_validate.side_effect = RuntimeError("validate failed")
        result = runner.invoke(main, ["validate"])
        assert result.exit_code == 1
