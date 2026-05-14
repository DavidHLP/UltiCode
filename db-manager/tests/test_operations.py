from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

from db_manager.operations._common import check_flyway_installed


class TestCheckFlywayInstalled:
    def test_returns_true_when_installed(self, mock_flyway_adapter: MagicMock) -> None:
        mock_flyway_adapter.check_flyway_installed.return_value = True
        result = check_flyway_installed(mock_flyway_adapter)
        assert result is True

    def test_returns_false_when_not_installed(self, mock_flyway_adapter: MagicMock, capsys: pytest.CaptureFixture) -> None:
        mock_flyway_adapter.check_flyway_installed.return_value = False
        result = check_flyway_installed(mock_flyway_adapter)
        assert result is False

    def test_prints_error_when_not_installed(self, mock_flyway_adapter: MagicMock) -> None:
        mock_flyway_adapter.check_flyway_installed.return_value = False
        with patch("db_manager.operations._common.console.print") as mock_print:
            check_flyway_installed(mock_flyway_adapter)
            mock_print.assert_called_once()
            call_args = mock_print.call_args[0][0]
            assert "Error: Flyway CLI not found" in call_args
