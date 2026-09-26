import asyncio
import importlib.util
from pathlib import Path

import pytest

_module_spec = importlib.util.spec_from_file_location(
    "e2e_readonly", Path(__file__).parents[1] / "e2e_readonly.py"
)
assert _module_spec and _module_spec.loader
e2e_readonly = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e_readonly)

class FakeClient:
    async def __aenter__(self) -> "FakeClient":
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def list_problems(self, *, page: int, page_size: int) -> dict[str, object]:
        return {"items": [{"id": 7}], "total": 1, "page": page}

    async def get_problem(self, problem_id: int) -> dict[str, object]:
        return {
            "id": problem_id,
            "slug": "sample",
            "title": "Sample",
            "difficulty": "EASY",
            "submission_count": 1,
        }

    async def login(self, username: str, password: str) -> dict[str, object]:
        return {}

    def cookie_names(self) -> list[str]:
        return ["access_token"]

    async def list_my_submissions(self, *, page: int, page_size: int) -> dict[str, object]:
        return {"items": [{"id": "11111111-1111-4111-8111-111111111111"}], "total": 999, "page": 42, "pageSize": 10}


def test_readonly_smoke_does_not_print_authenticated_totals(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(e2e_readonly, "UlticodeClient", lambda *args, **kwargs: FakeClient())

    assert asyncio.run(e2e_readonly.main()) == 0
    output = capsys.readouterr().out
    assert "items=1" in output
    assert "total=999" not in output
    assert "page=42" not in output


@pytest.mark.parametrize("detail", [{}, {"id": 99, "slug": "s", "title": "S", "difficulty": "EASY", "submission_count": 0}])
def test_readonly_smoke_rejects_invalid_problem_detail(detail, monkeypatch, capsys) -> None:
    class WrongDetailClient(FakeClient):
        async def get_problem(self, problem_id: int) -> dict[str, object]:
            return dict(detail)

    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(e2e_readonly, "UlticodeClient", lambda *args, **kwargs: WrongDetailClient())
    with pytest.raises(ValueError, match="invalid tool response"):
        asyncio.run(e2e_readonly.main())
    assert "E2E READ-ONLY PASS" not in capsys.readouterr().out
