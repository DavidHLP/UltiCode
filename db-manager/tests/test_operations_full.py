from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

from db_manager.operations.migrate import migrate
from db_manager.operations.info import info
from db_manager.operations.repair import repair
from db_manager.operations.baseline import baseline
from db_manager.operations.clean import clean
from db_manager.operations.validate import validate


@pytest.fixture
def mock_adapter():
    adapter = MagicMock()
    adapter.use_docker = False
    adapter.check_flyway_installed.return_value = True
    adapter.migrate.return_value = {"returncode": 0, "stdout": "migrated", "stderr": ""}
    adapter.info.return_value = {"returncode": 0, "stdout": "info", "stderr": ""}
    adapter.repair.return_value = {"returncode": 0, "stdout": "repaired", "stderr": ""}
    adapter.baseline.return_value = {"returncode": 0, "stdout": "baseline", "stderr": ""}
    adapter.clean.return_value = {"returncode": 0, "stdout": "cleaned", "stderr": ""}
    adapter.validate.return_value = {"returncode": 0, "stdout": "valid", "stderr": ""}
    return adapter


class TestMigrateOperation:
    @patch("db_manager.operations.migrate.get_flyway_adapter")
    @patch("db_manager.operations.migrate.console.print")
    def test_migrate_success(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_get_adapter.return_value = mock_adapter
        result = migrate()
        assert result["success"] == 1
        assert result["errors"] == 0

    @patch("db_manager.operations.migrate.get_flyway_adapter")
    @patch("db_manager.operations.migrate.console.print")
    def test_migrate_dry_run(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_get_adapter.return_value = mock_adapter
        result = migrate(dry_run=True)
        assert result["success"] == 1
        mock_adapter.migrate.assert_called_once_with(dry_run=True, out_of_order=True)

    @patch("db_manager.operations.migrate.get_flyway_adapter")
    @patch("db_manager.operations.migrate.console.print")
    def test_migrate_failure(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_adapter.migrate.return_value = {"returncode": 1, "stdout": "", "stderr": "fail"}
        mock_get_adapter.return_value = mock_adapter
        result = migrate()
        assert result["success"] == 0
        assert result["errors"] == 1

    @patch("db_manager.operations.migrate.get_flyway_adapter")
    def test_migrate_flyway_not_installed(self, mock_get_adapter) -> None:
        adapter = MagicMock()
        adapter.check_flyway_installed.return_value = False
        mock_get_adapter.return_value = adapter
        result = migrate()
        assert result["success"] == 0
        assert result["errors"] == 1


class TestInfoOperation:
    @patch("db_manager.operations.info.get_flyway_adapter")
    @patch("db_manager.operations.info.console.print")
    def test_info_success_simple_output(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_adapter.info.return_value = {"returncode": 0, "stdout": "no table data", "stderr": ""}
        mock_get_adapter.return_value = mock_adapter
        result = info()
        assert result["success"] == 1
        assert result["errors"] == 0

    @patch("db_manager.operations.info.get_flyway_adapter")
    @patch("db_manager.operations.info.console.print")
    def test_info_success_table_output(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        stdout = (
            "| Category | Version | Description | Type | Installed On | State |\n"
            "|----------|---------|-------------|------|--------------|-------|\n"
            "| Versioned | 1 | init | SQL | 2024-01-01 | Success |"
        )
        mock_adapter.info.return_value = {"returncode": 0, "stdout": stdout, "stderr": ""}
        mock_get_adapter.return_value = mock_adapter
        result = info()
        assert result["success"] == 1
        assert result["errors"] == 0

    @patch("db_manager.operations.info.get_flyway_adapter")
    @patch("db_manager.operations.info.console.print")
    def test_info_failure(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_adapter.info.return_value = {"returncode": 1, "stdout": "", "stderr": "fail"}
        mock_get_adapter.return_value = mock_adapter
        result = info()
        assert result["success"] == 0
        assert result["errors"] == 1

    @patch("db_manager.operations.info.get_flyway_adapter")
    def test_info_flyway_not_installed(self, mock_get_adapter) -> None:
        adapter = MagicMock()
        adapter.check_flyway_installed.return_value = False
        mock_get_adapter.return_value = adapter
        result = info()
        assert result["success"] == 0
        assert result["errors"] == 1

    @patch("db_manager.operations.info.get_flyway_adapter")
    @patch("db_manager.operations.info.console.print")
    def test_info_docker_message(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_adapter.use_docker = True
        mock_adapter.info.return_value = {"returncode": 0, "stdout": "ok", "stderr": ""}
        mock_get_adapter.return_value = mock_adapter
        info()


class TestRepairOperation:
    @patch("db_manager.operations.repair.get_flyway_adapter")
    @patch("db_manager.operations.repair.console.print")
    def test_repair_success(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_get_adapter.return_value = mock_adapter
        result = repair()
        assert result["success"] == 1
        assert result["errors"] == 0

    @patch("db_manager.operations.repair.get_flyway_adapter")
    @patch("db_manager.operations.repair.console.print")
    def test_repair_failure(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_adapter.repair.return_value = {"returncode": 1, "stdout": "", "stderr": "fail"}
        mock_get_adapter.return_value = mock_adapter
        result = repair()
        assert result["success"] == 0
        assert result["errors"] == 1

    @patch("db_manager.operations.repair.get_flyway_adapter")
    def test_repair_flyway_not_installed(self, mock_get_adapter) -> None:
        adapter = MagicMock()
        adapter.check_flyway_installed.return_value = False
        mock_get_adapter.return_value = adapter
        result = repair()
        assert result["success"] == 0
        assert result["errors"] == 1


class TestBaselineOperation:
    @patch("db_manager.operations.baseline.get_flyway_adapter")
    @patch("db_manager.operations.baseline.console.print")
    def test_baseline_success(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_get_adapter.return_value = mock_adapter
        result = baseline()
        assert result["success"] == 1
        assert result["errors"] == 0

    @patch("db_manager.operations.baseline.get_flyway_adapter")
    @patch("db_manager.operations.baseline.console.print")
    def test_baseline_failure(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_adapter.baseline.return_value = {"returncode": 1, "stdout": "", "stderr": "fail"}
        mock_get_adapter.return_value = mock_adapter
        result = baseline()
        assert result["success"] == 0
        assert result["errors"] == 1

    @patch("db_manager.operations.baseline.get_flyway_adapter")
    def test_baseline_flyway_not_installed(self, mock_get_adapter) -> None:
        adapter = MagicMock()
        adapter.check_flyway_installed.return_value = False
        mock_get_adapter.return_value = adapter
        result = baseline()
        assert result["success"] == 0
        assert result["errors"] == 1


class TestCleanOperation:
    @patch("db_manager.operations.clean.get_flyway_adapter")
    @patch("db_manager.operations.clean.console.print")
    def test_clean_without_force(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_get_adapter.return_value = mock_adapter
        result = clean(force=False)
        assert result["success"] == 0
        assert result["errors"] == 1
        mock_adapter.clean.assert_not_called()

    @patch("db_manager.operations.clean.get_flyway_adapter")
    @patch("db_manager.operations.clean.console.print")
    def test_clean_force_success(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_get_adapter.return_value = mock_adapter
        result = clean(force=True)
        assert result["success"] == 1
        assert result["errors"] == 0
        mock_adapter.clean.assert_called_once_with(force=True)

    @patch("db_manager.operations.clean.get_flyway_adapter")
    @patch("db_manager.operations.clean.console.print")
    def test_clean_force_failure(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_adapter.clean.return_value = {"returncode": 1, "stdout": "", "stderr": "fail"}
        mock_get_adapter.return_value = mock_adapter
        result = clean(force=True)
        assert result["success"] == 0
        assert result["errors"] == 1

    @patch("db_manager.operations.clean.get_flyway_adapter")
    def test_clean_flyway_not_installed(self, mock_get_adapter) -> None:
        adapter = MagicMock()
        adapter.check_flyway_installed.return_value = False
        mock_get_adapter.return_value = adapter
        result = clean(force=True)
        assert result["success"] == 0
        assert result["errors"] == 1


class TestValidateOperation:
    @patch("db_manager.operations.validate.get_flyway_adapter")
    @patch("db_manager.operations.validate.console.print")
    def test_validate_success(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_get_adapter.return_value = mock_adapter
        result = validate()
        assert result["success"] == 1
        assert result["errors"] == 0

    @patch("db_manager.operations.validate.get_flyway_adapter")
    @patch("db_manager.operations.validate.console.print")
    def test_validate_failure(self, mock_print, mock_get_adapter, mock_adapter) -> None:
        mock_adapter.validate.return_value = {"returncode": 1, "stdout": "", "stderr": "fail"}
        mock_get_adapter.return_value = mock_adapter
        result = validate()
        assert result["success"] == 0
        assert result["errors"] == 1

    @patch("db_manager.operations.validate.get_flyway_adapter")
    def test_validate_flyway_not_installed(self, mock_get_adapter) -> None:
        adapter = MagicMock()
        adapter.check_flyway_installed.return_value = False
        mock_get_adapter.return_value = adapter
        result = validate()
        assert result["success"] == 0
        assert result["errors"] == 1
