import asyncio
import importlib.util
from pathlib import Path
from types import SimpleNamespace

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
        return SimpleNamespace(text="fact and hypothesis separated", tool_call=None)


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
