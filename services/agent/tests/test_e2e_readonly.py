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
        return {"items": [{"id": 7}], "total": 1, "page": page, "pageSize": page_size}

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
        return {
            "items": [
                {
                    "id": f"11111111-1111-4111-8111-1111111111{index:02d}",
                    "problemId": 7,
                    "language": "java",
                    "status": "Accepted",
                    "createdAt": "2026-09-24T00:00:00",
                }
                for index in range(page_size)
            ],
            "total": 999,
            "page": page,
            "pageSize": page_size,
        }


def test_readonly_smoke_does_not_print_authenticated_totals(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(e2e_readonly, "UlticodeClient", lambda *args, **kwargs: FakeClient())

    assert asyncio.run(e2e_readonly.main()) == 0
    output = capsys.readouterr().out
    assert "items=3" in output
    assert "total=999" not in output
    assert "pageSize" not in output


def test_readonly_smoke_rejects_malformed_submission_page(monkeypatch, capsys) -> None:
    class MalformedListingClient(FakeClient):
        async def list_my_submissions(self, *, page: int, page_size: int) -> dict[str, object]:
            return {"items": [], "total": 999, "page": 42, "pageSize": 10}

    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(e2e_readonly, "UlticodeClient", lambda *a, **k: MalformedListingClient())

    with pytest.raises(ValueError, match="invalid tool response"):
        asyncio.run(e2e_readonly.main())
    assert "E2E READ-ONLY PASS" not in capsys.readouterr().out


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


@pytest.mark.parametrize(
    "listing,reason",
    [
        ({"items": [{"id": 7}], "total": 1, "page": 42, "pageSize": 10}, "problem_listing_contract"),
        ({"items": [{"id": 7}, {"id": 8}, {"id": 9}, {"id": 10}], "total": 4, "page": 1, "pageSize": 3}, "problem_listing_contract"),
        ({"items": [{"id": "7"}], "total": 1, "page": 1, "pageSize": 3}, "problem_listing_contract"),
        ({"items": [{"id": True}], "total": 1, "page": 1, "pageSize": 3}, "problem_listing_contract"),
        ({"items": [{"id": 7}], "total": True, "page": 1, "pageSize": 3}, "problem_listing_contract"),
        ({"items": [{"id": 7}], "total": -1, "page": 1, "pageSize": 3}, "problem_listing_contract"),
        ({"items": [{"id": 7}], "total": 999, "page": 1, "pageSize": 3}, "problem_listing_contract"),
        ({"items": [{"id": 7}, {"id": 8}], "total": 3, "page": 1, "pageSize": 3}, "problem_listing_contract"),
        ({"items": [], "total": 0, "page": 1, "pageSize": 3}, "no_problem_to_inspect"),
        ({"items": [{"id": 7}], "total": 1, "page": True, "pageSize": 3}, "problem_listing_contract"),
        ({"items": [{"id": 7}], "total": 1, "page": 1, "pageSize": True}, "problem_listing_contract"),
        ({"items": [{"id": 7}], "total": 0, "page": 1, "pageSize": 3}, "problem_listing_contract"),
    ],
)
def test_readonly_smoke_rejects_malformed_problem_listing(
    listing: dict[str, object], reason: str, monkeypatch, capsys
) -> None:
    class MalformedProblemsClient(FakeClient):
        async def list_problems(self, *, page: int, page_size: int) -> dict[str, object]:
            return dict(listing)

    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(e2e_readonly, "UlticodeClient", lambda *a, **k: MalformedProblemsClient())

    assert asyncio.run(e2e_readonly.main()) == 1
    output = capsys.readouterr().out
    assert f"reason={reason}" in output
    assert "E2E READ-ONLY PASS" not in output
