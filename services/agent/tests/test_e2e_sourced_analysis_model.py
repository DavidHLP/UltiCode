import asyncio
import importlib.util
import json
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
        # The real adapter records token usage; doubles must honour that contract
        # so the smoke's accounting line is exercised, not skipped.
        self.usage: list[dict[str, int]] = []

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


def test_model_smoke_fails_closed_without_a_key(monkeypatch, capsys) -> None:
    smoke = module
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)

    assert asyncio.run(smoke.main()) == 1
    assert "reason=missing_api_key" in capsys.readouterr().out


def test_model_smoke_passes_a_one_call_output_cap_by_default(monkeypatch) -> None:
    """The cap must be visible in the construction, not only in the adapter.

    A live run with the wrong cap is the failure this guards: the request would
    carry an unbounded output and the loop could repeat.
    """
    smoke = module
    captured: dict[str, object] = {}

    class _CapturingModel:
        def __init__(self, api_key: str, **kwargs: object) -> None:
            captured["api_key_present"] = bool(api_key)
            captured.update(kwargs)
            self.usage: list[dict[str, int]] = []

        async def __aenter__(self) -> "_CapturingModel":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def decide(self, _messages: list[dict[str, object]]) -> object:
            captured["decide_calls"] = int(captured.get("decide_calls", 0)) + 1
            return type("Decision", (), {"tool_call": None, "text": "{}"})()

    for name in (
        "DEEPSEEK_MAX_CALLS",
        "DEEPSEEK_MAX_TOKENS",
        "DEEPSEEK_MAX_PROMPT_TOKENS",
        "DEEPSEEK_MODEL",
    ):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "placeholder-not-a-real-key")
    monkeypatch.setattr(smoke, "DeepseekModel", _CapturingModel)

    class _Client:
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            pass

        async def __aenter__(self) -> "_Client":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def login(self, *_args: object) -> None:
            return None

    async def _first(_tools: object) -> dict[str, object]:
        return {"id": "sub-1", "status": "Wrong Answer"}

    monkeypatch.setattr(smoke, "UlticodeClient", _Client)
    monkeypatch.setattr(smoke, "build_tools", lambda _client: {})
    monkeypatch.setattr(smoke, "first_wrong_answer_submission", _first)
    monkeypatch.setattr(
        smoke,
        "analyze_submission",
        lambda *_a, **_k: {"facts": ["f"], "hypotheses": ["h"], "citations": [{"chunk_id": "c"}]},
    )
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")

    asyncio.run(smoke.main())

    assert captured["max_calls"] == 1
    assert captured["max_tokens"] == 300
    assert captured["max_prompt_tokens"] == 24000
    assert captured["model"] == "deepseek-flash"
    assert captured["decide_calls"] == 1


def test_answer_payload_unwraps_the_evidence_json_from_the_envelope() -> None:
    inner = '{"facts":["f"],"hypotheses":["h"],"citations":["d"]}'
    envelope = json.dumps({"answer": inner})

    assert module._answer_payload(envelope) == inner
    # A bare contract object is passed through unchanged.
    assert module._answer_payload(inner) == inner
    # Malformed input is left for the contract validator to reject.
    assert module._answer_payload("not json") == "not json"


def test_usage_is_reported_even_when_decide_raises(monkeypatch, capsys) -> None:
    """A billed call that fails the protocol must still report its tokens.

    The provider records usage before the response is parsed, so printing it only
    on the success path loses the exact accounting you need after a failure.
    """
    smoke = module
    monkeypatch.setenv("DEEPSEEK_API_KEY", "placeholder-not-a-real-key")
    monkeypatch.setenv("ULTICODE_E2E_USERNAME", "tester")
    monkeypatch.setenv("ULTICODE_E2E_PASSWORD", "pw")

    class _BillingThenFailingModel:
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            self.usage: list[dict[str, int]] = []

        async def __aenter__(self) -> "_BillingThenFailingModel":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def decide(self, _messages: list[dict[str, object]]) -> object:
            # Usage is recorded by the adapter before parsing the decision.
            self.usage = [{"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}]
            raise RuntimeError("protocol failure after billing")

    class _Client:
        def __init__(self, *_args: object, **_kwargs: object) -> None:
            pass

        async def __aenter__(self) -> "_Client":
            return self

        async def __aexit__(self, *_args: object) -> None:
            return None

        async def login(self, *_args: object) -> None:
            return None

    async def _first(_tools: object) -> dict[str, object]:
        return {"id": "sub-1", "status": "Wrong Answer"}

    monkeypatch.setattr(smoke, "DeepseekModel", _BillingThenFailingModel)
    monkeypatch.setattr(smoke, "UlticodeClient", _Client)
    monkeypatch.setattr(smoke, "build_tools", lambda _client: {})
    monkeypatch.setattr(smoke, "first_wrong_answer_submission", _first)
    monkeypatch.setattr(
        smoke,
        "analyze_submission",
        lambda *_a, **_k: {"facts": ["f"], "hypotheses": ["h"], "citations": [{"chunk_id": "c"}]},
    )

    with pytest.raises(RuntimeError):
        asyncio.run(smoke.main())

    output = capsys.readouterr().out
    assert "E2E SOURCED MODEL USAGE | calls=1 total_tokens=15" in output
