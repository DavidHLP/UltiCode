import asyncio
import json
import stat
from pathlib import Path

import httpx
import pytest

from deepseek_model import DeepseekModel, ModelBudgetExceeded
from model_budget import ModelBudget


def test_live_request_uses_shared_reservation_and_disabled_thinking(tmp_path: Path) -> None:
    path = tmp_path / "state" / "budget.sqlite3"
    requests: list[httpx.Request] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(
            200,
            json={
                "choices": [{"message": {"content": '{"answer":"ok"}'}}],
                "usage": {"prompt_tokens": 12, "completion_tokens": 3, "total_tokens": 15},
            },
        )

    async def scenario() -> None:
        for _ in range(2):
            async with DeepseekModel(
                "test-key",
                tool_specs={},
                model="deepseek-flash",
                max_tokens=40,
                budget=ModelBudget(path),
                thinking_type="disabled",
                transport=httpx.MockTransport(handler),
            ) as model:
                await model.decide([{"role": "user", "content": "hello"}])

    asyncio.run(scenario())
    assert len(requests) == 2
    assert all(json.loads(request.content)["thinking"] == {"type": "disabled"} for request in requests)
    assert ModelBudget(path).snapshot()["attempts"] == 2
    assert stat.S_IMODE(path.stat().st_mode) == 0o600


def test_exhausted_shared_budget_blocks_http_request(tmp_path: Path) -> None:
    path = tmp_path / "budget.sqlite3"
    budget = ModelBudget(path)
    for _ in range(140):
        budget.reserve(1, 1)
    requests: list[httpx.Request] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"answer":"x"}'}}]})

    async def scenario() -> None:
        async with DeepseekModel(
            "test-key",
            tool_specs={},
            budget=ModelBudget(path),
            transport=httpx.MockTransport(handler),
        ) as model:
            with pytest.raises(ModelBudgetExceeded):
                await model.decide([{"role": "user", "content": "hello"}])
            assert model.calls_made == 0

    asyncio.run(scenario())
    assert requests == []
