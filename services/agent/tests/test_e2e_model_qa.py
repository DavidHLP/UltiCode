import asyncio
import importlib.util
from pathlib import Path

import pytest

from agent_loop import ModelDecision, ToolCall

_module_spec = importlib.util.spec_from_file_location(
    "e2e_model_qa", Path(__file__).parents[1] / "e2e_model_qa.py"
)
assert _module_spec and _module_spec.loader
module = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(module)


class FakeClient:
    async def __aenter__(self) -> "FakeClient":
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def login(self, username: str, password: str) -> dict[str, object]:
        return {}

    async def get_problem(self, problem_id: int) -> dict[str, object]:
        return {
            "id": problem_id,
            "slug": "sample",
            "title": "Sample",
            "difficulty": "EASY",
            "submission_count": 1,
        }

    async def list_problem_submissions(
        self, problem_id: int, *, page: int, page_size: int
    ) -> dict[str, object]:
        return {
            "items": [
                {
                    "id": "11111111-1111-4111-8111-111111111111",
                    "language": "java",
                    "status": "Accepted",
                    "createdAt": "2026-09-25T00:00:00",
                    "problem": {"id": problem_id, "title": "Sample", "slug": "sample"},
                }
            ],
            "total": 1,
            "page": page,
            "pageSize": page_size,
        }


class FakeModel:
    def __init__(self, decisions: list[ModelDecision]) -> None:
        self._decisions = list(decisions)

    async def __aenter__(self) -> "FakeModel":
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def decide(self, messages: list[dict[str, object]]) -> ModelDecision:
        return self._decisions.pop(0)


def _run_model(monkeypatch, capsys, model: FakeModel) -> tuple[int, str]:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setattr(module, "UlticodeClient", lambda *args, **kwargs: FakeClient())
    monkeypatch.setattr(module, "DeepseekModel", lambda *args, **kwargs: model)
    return_code = asyncio.run(module.main())
    return return_code, capsys.readouterr().out


def test_model_qa_requires_problem_and_problem_scoped_submission_evidence(monkeypatch, capsys) -> None:
    model = FakeModel(
        [
            ModelDecision(tool_call=ToolCall("get_problem", {"id": 7})),
            ModelDecision(tool_call=ToolCall("get_problem_submissions", {"problemId": 7})),
            ModelDecision(text='{"title":"Sample","difficulty":"EASY","has_submission":true}'),
        ]
    )
    return_code, output = _run_model(monkeypatch, capsys, model)

    assert return_code == 0
    assert "E2E MODEL QA PASS | scope=REAL model + REAL local stack" in output


def test_model_qa_fails_when_only_problem_evidence_is_checked(monkeypatch, capsys) -> None:
    model = FakeModel(
        [
            ModelDecision(tool_call=ToolCall("get_problem", {"id": 7})),
            ModelDecision(text='{"title":"Sample","difficulty":"EASY","has_submission":false}'),
        ]
    )
    return_code, output = _run_model(monkeypatch, capsys, model)

    assert return_code == 1
    assert "reason=tool_contract" in output


def test_model_qa_rejects_wrong_answer_after_valid_tool_calls(monkeypatch, capsys) -> None:
    model = FakeModel(
        [
            ModelDecision(tool_call=ToolCall("get_problem", {"id": 7})),
            ModelDecision(tool_call=ToolCall("get_problem_submissions", {"problemId": 7})),
            ModelDecision(text='{"title":"Wrong","difficulty":"HARD","has_submission":false}'),
        ]
    )
    return_code, output = _run_model(monkeypatch, capsys, model)

    assert return_code == 1
    assert "reason=answer_contract" in output


def test_model_qa_rejects_successful_tools_for_other_problem_ids(monkeypatch, capsys) -> None:
    model = FakeModel(
        [
            ModelDecision(tool_call=ToolCall("get_problem", {"id": 8})),
            ModelDecision(tool_call=ToolCall("get_problem_submissions", {"problemId": 9})),
            ModelDecision(
                text='{"title":"Sample","difficulty":"EASY","has_submission":true}'
            ),
        ]
    )
    return_code, output = _run_model(monkeypatch, capsys, model)

    assert return_code == 1
    assert "reason=tool_contract" in output

def test_model_qa_rejects_duplicate_answer_keys(monkeypatch, capsys) -> None:
    model = FakeModel(
        [
            ModelDecision(tool_call=ToolCall("get_problem", {"id": 7})),
            ModelDecision(tool_call=ToolCall("get_problem_submissions", {"problemId": 7})),
            ModelDecision(text='{"title":"Wrong","title":"Sample","difficulty":"EASY","has_submission":true}'),
        ]
    )
    return_code, output = _run_model(monkeypatch, capsys, model)

    assert return_code == 1
    assert "reason=answer_contract" in output


def test_model_qa_reports_usage_even_when_the_loop_raises(monkeypatch, capsys) -> None:
    """A billed run must leave a cost record on the abort path too."""
    # Reuse the module this file already loaded via importlib.
    smoke = module

    class _BillingModel:
        def __init__(self, *_args: object, **kwargs: object) -> None:
            self.kwargs = kwargs
            self.usage: list[dict[str, int]] = [
                {"prompt_tokens": 20, "completion_tokens": 6, "total_tokens": 26}
            ]

        async def __aenter__(self) -> "_BillingModel":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

    async def _boom(*_args: object, **_kwargs: object) -> object:
        raise RuntimeError("loop aborted")

    class _Client:
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            pass

        async def __aenter__(self) -> "_Client":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def login(self, *_args: object) -> None:
            return None

    monkeypatch.setenv("DEEPSEEK_API_KEY", "placeholder-not-a-real-key")
    monkeypatch.setenv("DEEPSEEK_MODEL", "deepseek-flash")
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setattr(smoke, "DeepseekModel", _BillingModel)
    monkeypatch.setattr(smoke, "UlticodeClient", _Client)
    monkeypatch.setattr(smoke, "build_tools", lambda _client: {})
    monkeypatch.setattr(smoke, "run_tool_loop", _boom)

    with pytest.raises(RuntimeError):
        asyncio.run(smoke.main())

    output = capsys.readouterr().out
    assert "E2E MODEL QA USAGE | calls=1 total_tokens=26" in output
