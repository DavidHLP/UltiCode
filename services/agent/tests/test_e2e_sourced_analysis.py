import asyncio
import importlib.util
from pathlib import Path

import pytest

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
        return {"items": self._items, "total": len(self._items), "page": page, "pageSize": page_size}


def _projected_submission(status: str) -> dict[str, object]:
    return {
        "id": "11111111-1111-4111-8111-111111111111",
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


def test_sourced_analysis_e2e_selects_matching_submission(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    items = [
        {**_projected_submission("Accepted"), "id": "11111111-1111-4111-8111-111111111111"},
        {**_projected_submission("Wrong Answer"), "id": "22222222-2222-4222-8222-222222222222"},
    ]
    monkeypatch.setattr(
        e2e_sourced_analysis,
        "UlticodeClient",
        lambda *args, **kwargs: FakeClient(items),
    )

    assert asyncio.run(e2e_sourced_analysis.main()) == 0
    output = capsys.readouterr().out
    assert "E2E SOURCED ANALYSIS PASS | corpus=agent-authored-synthetic | input=validated-user-projection" in output


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
    assert "reason=no_wrong_answer_submission" in output
    assert "sub-1" not in output
    assert "must-not-enter-analysis" not in output


def test_sourced_analysis_e2e_fails_without_citation(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(
        e2e_sourced_analysis,
        "UlticodeClient",
        lambda *args, **kwargs: FakeClient([_projected_submission("Wrong Answer")]),
    )
    monkeypatch.setattr(e2e_sourced_analysis, "QUESTION", "量子拓扑")

    assert asyncio.run(e2e_sourced_analysis.main()) == 1
    output = capsys.readouterr().out
    assert "reason=no_citation" in output
    assert "sub-1" not in output
    assert "must-not-enter-analysis" not in output


def test_sourced_analysis_e2e_finds_wrong_answer_on_later_page(monkeypatch, capsys) -> None:
    class PagedClient(FakeClient):
        async def list_my_submissions(
            self, *, page: int, page_size: int
        ) -> dict[str, object]:
            first = [
                {**_projected_submission("Accepted"), "id": f"11111111-1111-4111-8111-1111111111{n:02d}"}
                for n in range(page_size)
            ]
            second = [{**_projected_submission("Wrong Answer"), "id": "22222222-2222-4222-8222-222222222222"}]
            return {
                "items": first if page == 1 else second,
                "total": page_size + 1,
                "page": page,
                "pageSize": page_size,
            }

    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(e2e_sourced_analysis, "UlticodeClient", lambda *a, **k: PagedClient([]))

    assert asyncio.run(e2e_sourced_analysis.main()) == 0
    assert "E2E SOURCED ANALYSIS PASS" in capsys.readouterr().out


def test_sourced_analysis_e2e_scans_past_ten_pages(monkeypatch, capsys) -> None:
    target_page = 12
    total = 100 * (target_page - 1) + 1

    class DeepPagedClient(FakeClient):
        async def list_my_submissions(
            self, *, page: int, page_size: int
        ) -> dict[str, object]:
            if page == target_page:
                items = [
                    {**_projected_submission("Wrong Answer"), "id": "22222222-2222-4222-8222-222222222222"}
                ]
            else:
                items = [
                    {
                        **_projected_submission("Accepted"),
                        "id": f"11111111-1111-4111-8111-{(page * 1000 + n) % 10**12:012d}",
                    }
                    for n in range(page_size)
                ]
            return {"items": items, "total": total, "page": page, "pageSize": page_size}

    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(e2e_sourced_analysis, "UlticodeClient", lambda *a, **k: DeepPagedClient([]))

    assert asyncio.run(e2e_sourced_analysis.main()) == 0
    assert "E2E SOURCED ANALYSIS PASS" in capsys.readouterr().out


def test_sourced_analysis_e2e_rejects_cross_page_duplicate_ids(monkeypatch, capsys) -> None:
    class ShiftingClient(FakeClient):
        async def list_my_submissions(
            self, *, page: int, page_size: int
        ) -> dict[str, object]:
            # Later pages repeat the first page's records and hide a real Wrong
            # Answer behind them; a scan that counts repeats as progress would
            # return that record and report PASS on a self-contradictory page.
            items = [
                {
                    **_projected_submission("Accepted"),
                    "id": f"11111111-1111-4111-8111-{n % 10**12:012d}",
                }
                for n in range(page_size if page == 1 else page_size - 1)
            ]
            if page > 1:
                items.append(
                    {
                        **_projected_submission("Wrong Answer"),
                        "id": "22222222-2222-4222-8222-222222222222",
                    }
                )
            return {
                "items": items,
                "total": page_size * 2,
                "page": page,
                "pageSize": page_size,
            }

    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(e2e_sourced_analysis, "UlticodeClient", lambda *a, **k: ShiftingClient([]))

    with pytest.raises(ValueError, match="duplicate submission id across pages"):
        asyncio.run(e2e_sourced_analysis.main())
    assert "E2E SOURCED ANALYSIS PASS" not in capsys.readouterr().out

def test_sourced_analysis_e2e_rejects_duplicate_after_a_match_on_the_same_page(
    monkeypatch, capsys
) -> None:
    class MatchThenDuplicateClient(FakeClient):
        async def list_my_submissions(
            self, *, page: int, page_size: int
        ) -> dict[str, object]:
            if page == 1:
                items = [
                    {
                        **_projected_submission("Accepted"),
                        "id": f"11111111-1111-4111-8111-{n % 10**12:012d}",
                    }
                    for n in range(page_size)
                ]
            else:
                # A fresh Wrong Answer appears first; the repeated id sits after it,
                # so an early return would accept a self-contradictory page.
                items = [
                    {
                        **_projected_submission("Wrong Answer"),
                        "id": "22222222-2222-4222-8222-222222222222",
                    },
                    *(
                        {
                            **_projected_submission("Accepted"),
                            "id": f"11111111-1111-4111-8111-{n % 10**12:012d}",
                        }
                        for n in range(page_size - 1)
                    ),
                ]
            return {
                "items": items,
                "total": page_size * 2,
                "page": page,
                "pageSize": page_size,
            }

    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(
        e2e_sourced_analysis, "UlticodeClient", lambda *a, **k: MatchThenDuplicateClient([])
    )

    with pytest.raises(ValueError, match="duplicate submission id across pages"):
        asyncio.run(e2e_sourced_analysis.main())
    assert "E2E SOURCED ANALYSIS PASS" not in capsys.readouterr().out


def test_sourced_analysis_e2e_rejects_total_drift_during_scan(monkeypatch, capsys) -> None:
    class DriftingTotalClient(FakeClient):
        async def list_my_submissions(
            self, *, page: int, page_size: int
        ) -> dict[str, object]:
            if page == 1:
                return {
                    "items": [
                        {
                            **_projected_submission("Accepted"),
                            "id": f"11111111-1111-4111-8111-{n % 10**12:012d}",
                        }
                        for n in range(page_size)
                    ],
                    "total": page_size * 2,
                    "page": page,
                    "pageSize": page_size,
                }
            # The collection shrinks mid-scan; page 3 is never requested, so a
            # scan that trusts the smaller total reports absence on a real record.
            return {"items": [], "total": page_size, "page": page, "pageSize": page_size}

    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(
        e2e_sourced_analysis, "UlticodeClient", lambda *a, **k: DriftingTotalClient([])
    )

    with pytest.raises(ValueError, match="submission total changed during scan"):
        asyncio.run(e2e_sourced_analysis.main())
    assert "E2E SOURCED ANALYSIS PASS" not in capsys.readouterr().out


def test_sourced_analysis_e2e_fails_on_unverifiable_citation(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")

    submission = {
        "id": "11111111-1111-4111-8111-111111111111",
        "status": "Wrong Answer",
    }
    analysis = {
        "facts": ["fact"],
        "hypotheses": ["hypothesis"],
        "citations": [{"chunk_id": "x"}],
        "citation_checks": [
            {"chunk_id": "x", "verdict": "text_not_in_source", "detail": ""}
        ],
    }

    class _Client(FakeClient):
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            super().__init__([submission])

    async def _first(_tools: object) -> dict[str, object]:
        return submission

    monkeypatch.setattr(e2e_sourced_analysis, "UlticodeClient", _Client)
    monkeypatch.setattr(
        e2e_sourced_analysis, "analyze_submission", lambda *_a, **_k: analysis
    )
    monkeypatch.setattr(e2e_sourced_analysis, "first_wrong_answer_submission", _first)

    assert asyncio.run(e2e_sourced_analysis.main()) == 1
    assert "reason=unverifiable_citation" in capsys.readouterr().out


def test_sourced_analysis_e2e_fails_when_citation_checks_are_missing(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    analysis = {
        "facts": ["fact"],
        "hypotheses": ["hypothesis"],
        "citations": [{"chunk_id": "a"}, {"chunk_id": "b"}],
        # Fewer checks than citations: an absent check must not read as a pass.
        "citation_checks": [{"chunk_id": "a", "verdict": "verified", "detail": ""}],
    }

    async def _first(_tools: object) -> dict[str, object]:
        return {
            "id": "11111111-1111-4111-8111-111111111111",
            "status": "Wrong Answer",
        }

    monkeypatch.setattr(
        e2e_sourced_analysis,
        "UlticodeClient",
        lambda *args, **kwargs: FakeClient([]),
    )
    monkeypatch.setattr(
        e2e_sourced_analysis, "analyze_submission", lambda *_a, **_k: analysis
    )
    monkeypatch.setattr(e2e_sourced_analysis, "first_wrong_answer_submission", _first)

    assert asyncio.run(e2e_sourced_analysis.main()) == 1
    assert "reason=unverifiable_citation" in capsys.readouterr().out
