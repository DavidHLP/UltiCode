import asyncio
import importlib.util
from pathlib import Path

_module_spec = importlib.util.spec_from_file_location(
    "e2e_sourced_analysis", Path(__file__).parents[1] / "e2e_sourced_analysis.py"
)
assert _module_spec and _module_spec.loader
e2e_sourced_analysis = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e_sourced_analysis)


class FakeClient:
    def __init__(self, items: list[dict[str, object]]) -> None:
        self._items = items

    async def __aenter__(self) -> "FakeClient":
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def login(self, username: str, password: str) -> dict[str, object]:
        return {}

    async def list_my_submissions(self, *, page: int, page_size: int) -> dict[str, object]:
        return {"items": self._items, "total": len(self._items), "page": page}


def _projected_submission(status: str) -> dict[str, object]:
    return {
        "id": "sub-1",
        "problemId": 7,
        "userId": "must-not-enter-analysis",
        "language": "java",
        "code": "SECRET SOURCE",
        "status": status,
        "input": "SECRET INPUT",
        "errorDetail": "SECRET ERROR",
        "createdAt": "2026-09-25T00:00:00",
    }


def test_sourced_analysis_e2e_requires_projection_and_citation(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(
        e2e_sourced_analysis,
        "UlticodeClient",
        lambda *args, **kwargs: FakeClient([_projected_submission("Wrong Answer")]),
    )

    assert asyncio.run(e2e_sourced_analysis.main()) == 0
    output = capsys.readouterr().out
    output_lines = output.splitlines()
    assert "E2E SOURCED ANALYSIS PASS | corpus=agent-authored-synthetic | input=validated-user-projection" in output_lines
    assert "SECRET" not in output
    assert "must-not-enter-analysis" not in output


def test_sourced_analysis_e2e_fails_for_non_wrong_answer_status(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(
        e2e_sourced_analysis,
        "UlticodeClient",
        lambda *args, **kwargs: FakeClient([_projected_submission("Accepted")]),
    )

    assert asyncio.run(e2e_sourced_analysis.main()) == 1
    output = capsys.readouterr().out
    assert "reason=no_citation" in output
    assert "sub-1" not in output
    assert "must-not-enter-analysis" not in output


def test_sourced_analysis_e2e_fails_without_citation(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(
        e2e_sourced_analysis,
        "UlticodeClient",
        lambda *args, **kwargs: FakeClient([_projected_submission("Accepted")]),
    )
    monkeypatch.setattr(e2e_sourced_analysis, "QUESTION", "量子拓扑")

    assert asyncio.run(e2e_sourced_analysis.main()) == 1
    output = capsys.readouterr().out
    assert "reason=no_citation" in output
    assert "sub-1" not in output
    assert "must-not-enter-analysis" not in output
