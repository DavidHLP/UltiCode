import asyncio
import importlib.util
from pathlib import Path
from types import SimpleNamespace

import pytest

_module_spec = importlib.util.spec_from_file_location(
    "e2e_sourced_analysis_model",
    Path(__file__).parents[1] / "e2e_sourced_analysis_model.py",
)
assert _module_spec and _module_spec.loader
module = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(module)


class FakeClient:
    def __init__(self) -> None:
        self.items = [
            {
                "id": "sub-1",
                "problemId": 7,
                "language": "java",
                "status": "Accepted",
                "createdAt": "2026-09-25T00:00:00",
            },
            {
                "id": "sub-2",
                "problemId": 7,
                "language": "java",
                "status": "Wrong Answer",
                "createdAt": "2026-09-25T00:00:00",
            },
        ]
    async def __aenter__(self) -> "FakeClient":
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def login(self, username: str, password: str) -> dict[str, object]:
        return {}

    async def list_my_submissions(self, *, page: int, page_size: int) -> dict[str, object]:
        return {"items": self.items, "total": len(self.items), "page": page}


class FakeModel:
    def __init__(self, *args: object, **kwargs: object) -> None:
        self.messages: list[dict[str, object]] = []

    async def __aenter__(self) -> "FakeModel":
        return self

    async def __aexit__(self, *args: object) -> None:
        return None

    async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
        self.messages = messages
        return SimpleNamespace(
            text='{"facts":["提交 sub-2 的状态是 Wrong Answer。"],"hypotheses":["unverified"],"citations":["sample-status-only"]}',
            tool_call=None,
        )


def test_real_model_smoke_sends_only_projected_facts_and_withholds_answer(
    monkeypatch, capsys
) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(module, "UlticodeClient", lambda *args, **kwargs: FakeClient())
    model = FakeModel()
    monkeypatch.setattr(module, "DeepseekModel", lambda *args, **kwargs: model)

    assert asyncio.run(module.main()) == 0
    output = capsys.readouterr().out
    assert "E2E SOURCED MODEL PASS | corpus=agent-authored-synthetic | input=validated-user-projection | answer=withheld" in output
    assert "fact and hypothesis separated" not in output
    assert "SECRET" not in output
    assert model.messages
    assert "Wrong Answer" in str(model.messages[0]["content"])
    assert "source_code" not in str(model.messages[0]["content"])
    assert "userId" not in str(model.messages[0]["content"])


def test_real_model_smoke_rejects_unstructured_answer(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(module, "UlticodeClient", lambda *args, **kwargs: FakeClient())

    class InvalidModel(FakeModel):
        async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
            self.messages = messages
            return SimpleNamespace(text="just some prose", tool_call=None)

    model = InvalidModel()
    monkeypatch.setattr(module, "DeepseekModel", lambda *args, **kwargs: model)

    assert asyncio.run(module.main()) == 1
    output = capsys.readouterr().out
    assert "reason=invalid_answer" in output
    assert "just some prose" not in output


@pytest.mark.parametrize(
    "answer",
    [
        '{"facts":["invented fact"],"hypotheses":["unverified"],"citations":["sample-status-only"]}',
        '{"facts":["提交 sub-2 的状态是 Wrong Answer。"],"hypotheses":[],"citations":["sample-status-only"]}',
        '{"facts":["提交 sub-2 的状态是 Wrong Answer。"],"hypotheses":[""],"citations":["sample-status-only"]}',
        '{"facts":["提交 sub-2 的状态是 Wrong Answer。"],"hypotheses":"not-a-list","citations":["sample-status-only"]}',
    ],
)
def test_real_model_smoke_rejects_invalid_fact_or_hypothesis_structure(
    answer: str, monkeypatch, capsys
) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(module, "UlticodeClient", lambda *args, **kwargs: FakeClient())

    class InvalidModel(FakeModel):
        async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
            self.messages = messages
            return SimpleNamespace(text=answer, tool_call=None)

    model = InvalidModel()
    monkeypatch.setattr(module, "DeepseekModel", lambda *args, **kwargs: model)

    assert asyncio.run(module.main()) == 1
    output = capsys.readouterr().out
    assert "reason=invalid_answer" in output
    assert answer not in output


def test_real_model_smoke_rejects_unknown_citation(monkeypatch, capsys) -> None:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(module, "UlticodeClient", lambda *args, **kwargs: FakeClient())

    class InvalidCitationModel(FakeModel):
        async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
            self.messages = messages
            return SimpleNamespace(
                text='{"facts":["提交 sub-2 的状态是 Wrong Answer。"],"hypotheses":["unverified"],"citations":["missing-doc"]}',
                tool_call=None,
            )

    model = InvalidCitationModel()
    monkeypatch.setattr(module, "DeepseekModel", lambda *args, **kwargs: model)

    assert asyncio.run(module.main()) == 1
    output = capsys.readouterr().out
    assert "reason=invalid_answer" in output
    assert "missing-doc" not in output
