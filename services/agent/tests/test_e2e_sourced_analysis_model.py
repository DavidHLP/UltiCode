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
                "id": "11111111-1111-4111-8111-111111111111",
                "problemId": 7,
                "language": "java",
                "status": "Accepted",
                "createdAt": "2026-09-25T00:00:00",
            },
            {
                "id": "22222222-2222-4222-8222-222222222222",
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
        return {"items": self.items, "total": len(self.items), "page": page, "pageSize": page_size}


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
            text='{"facts":["提交 22222222-2222-4222-8222-222222222222 的状态是 Wrong Answer。"],"hypotheses":["当前只有提交状态，没有源码或失败用例；不能据此定位具体代码行、复现失败输入或断言运行结果。"],"citations":["sample-status-only"]}',
            tool_call=None,
        )


def _run_model(monkeypatch, capsys, model: FakeModel) -> tuple[int, str]:
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    monkeypatch.setattr(module, "UlticodeClient", lambda *args, **kwargs: FakeClient())
    monkeypatch.setattr(module, "DeepseekModel", lambda *args, **kwargs: model)
    return_code = asyncio.run(module.main())
    return return_code, capsys.readouterr().out


def test_real_model_smoke_sends_answer_contract_and_withholds_answer(monkeypatch, capsys) -> None:
    model = FakeModel()
    return_code, output = _run_model(monkeypatch, capsys, model)

    assert return_code == 0
    assert "E2E SOURCED MODEL PASS | corpus=agent-authored-synthetic | input=validated-user-projection | answer=withheld" in output
    assert "fact and hypothesis separated" not in output
    assert "SECRET" not in output
    assert model.messages
    prompt = str(model.messages[0]["content"])
    assert "facts, hypotheses, citations" in prompt
    assert "Wrong Answer" in prompt
    assert "source_code" not in prompt
    assert "userId" not in prompt


def test_real_model_smoke_rejects_unstructured_answer(monkeypatch, capsys) -> None:
    class InvalidModel(FakeModel):
        async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
            self.messages = messages
            return SimpleNamespace(text="just some prose", tool_call=None)

    return_code, output = _run_model(monkeypatch, capsys, InvalidModel())

    assert return_code == 1
    assert "reason=invalid_answer" in output
    assert "just some prose" not in output


@pytest.mark.parametrize(
    "answer",
    [
        '{"facts":["invented fact"],"hypotheses":["unverified"],"citations":["sample-status-only"]}',
        '{"facts":["提交 22222222-2222-4222-8222-222222222222 的状态是 Wrong Answer。"],"hypotheses":[],"citations":["sample-status-only"]}',
        '{"facts":["提交 22222222-2222-4222-8222-222222222222 的状态是 Wrong Answer。"],"hypotheses":[""],"citations":["sample-status-only"]}',
        '{"facts":["提交 22222222-2222-4222-8222-222222222222 的状态是 Wrong Answer。"],"hypotheses":"not-a-list","citations":["sample-status-only"]}',
        '{"facts":["提交 22222222-2222-4222-8222-222222222222 的状态是 Wrong Answer。"],"hypotheses":["提交一定因为空指针异常。"],"citations":["sample-status-only"]}',
    ],
)
def test_real_model_smoke_rejects_invalid_fact_or_hypothesis(
    answer: str, monkeypatch, capsys
) -> None:
    class InvalidModel(FakeModel):
        async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
            self.messages = messages
            return SimpleNamespace(text=answer, tool_call=None)

    return_code, output = _run_model(monkeypatch, capsys, InvalidModel())

    assert return_code == 1
    assert "reason=invalid_answer" in output
    assert answer not in output


def test_real_model_smoke_rejects_citation_outside_evidence(
    monkeypatch, capsys
) -> None:
    answer = (
        '{"facts":["提交 22222222-2222-4222-8222-222222222222 的状态是 Wrong Answer。"],'
        '"hypotheses":["当前只有提交状态，没有源码或失败用例；不能据此定位具体代码行、复现失败输入或断言运行结果。"],'
        '"citations":["invented-doc"]}'
    )

    class InventedCitationModel(FakeModel):
        async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
            self.messages = messages
            return SimpleNamespace(text=answer, tool_call=None)

    return_code, output = _run_model(monkeypatch, capsys, InventedCitationModel())

    assert return_code == 1
    assert "reason=invalid_answer" in output
    assert "invented-doc" not in output


@pytest.mark.parametrize(
    "answer",
    [
        '{"facts":["invented"],"facts":["提交 sub-2 的状态是 Wrong Answer。"],"hypotheses":["当前只有提交状态，没有源码或失败用例；不能据此定位具体代码行、复现失败输入或断言运行结果。"],"citations":["sample-status-only"]}',
    ],
)
def test_real_model_smoke_rejects_duplicate_answer_keys(answer, monkeypatch, capsys) -> None:
    class DuplicateKeyModel(FakeModel):
        async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
            self.messages = messages
            return SimpleNamespace(text=answer, tool_call=None)

    return_code, output = _run_model(monkeypatch, capsys, DuplicateKeyModel())

    assert return_code == 1
    assert "reason=invalid_answer" in output


def test_real_model_smoke_rejects_tool_call_even_with_text(monkeypatch, capsys) -> None:
    class ToolCallModel(FakeModel):
        async def decide(self, messages: list[dict[str, object]]) -> SimpleNamespace:
            self.messages = messages
            return SimpleNamespace(
                text="looks complete", tool_call=SimpleNamespace(name="get_problem")
            )

    return_code, output = _run_model(monkeypatch, capsys, ToolCallModel())

    assert return_code == 1
    assert "reason=tool_call" in output
    assert "looks complete" not in output
