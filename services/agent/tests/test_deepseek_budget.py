import asyncio
import json

import httpx
import pytest

from deepseek_model import MAX_TOKENS, DeepseekModel, ModelBudgetExceeded, ModelProtocolError

_USAGE = {"prompt_tokens": 120, "completion_tokens": 30, "total_tokens": 150}


def _handler(captured: list[httpx.Request], usage: dict[str, object] | None = None) -> object:
    async def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        body: dict[str, object] = {
            "choices": [{"message": {"content": '{"answer":"ok"}'}}]
        }
        if usage is not None:
            body["usage"] = usage
        return httpx.Response(200, json=body)

    return handler


def test_request_carries_a_bounded_output_budget() -> None:
    captured: list[httpx.Request] = []

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(_handler(captured))
        ) as model:
            await model.decide([{"role": "user", "content": "hello"}])
            sent = model._max_tokens

        body = json.loads(captured[0].content)
        assert body["max_tokens"] == sent == MAX_TOKENS
        assert MAX_TOKENS <= 512

    asyncio.run(scenario())


def test_oversized_prompt_is_rejected_before_any_request() -> None:
    captured: list[httpx.Request] = []

    async def scenario() -> None:
        async with DeepseekModel(
            "key",
            tool_specs={},
            max_prompt_chars=10,
            transport=httpx.MockTransport(_handler(captured)),
        ) as model:
            with pytest.raises(ModelBudgetExceeded):
                await model.decide([{"role": "user", "content": "x" * 500}])
            assert model.calls_made == 0

    asyncio.run(scenario())
    assert captured == []


def test_call_budget_stops_the_loop_and_bounds_requests() -> None:
    captured: list[httpx.Request] = []

    async def scenario() -> None:
        async with DeepseekModel(
            "key",
            tool_specs={},
            max_calls=2,
            transport=httpx.MockTransport(_handler(captured)),
        ) as model:
            await model.decide([{"role": "user", "content": "a"}])
            await model.decide([{"role": "user", "content": "b"}])
            with pytest.raises(ModelBudgetExceeded):
                await model.decide([{"role": "user", "content": "c"}])
            assert model.calls_made == 2

    asyncio.run(scenario())
    assert len(captured) == 2


def test_token_usage_is_recorded_for_cost_accounting() -> None:
    captured: list[httpx.Request] = []

    async def scenario() -> None:
        async with DeepseekModel(
            "key",
            tool_specs={},
            transport=httpx.MockTransport(_handler(captured, dict(_USAGE))),
        ) as model:
            await model.decide([{"role": "user", "content": "a"}])
            await model.decide([{"role": "user", "content": "b"}])
            assert model.usage == [dict(_USAGE), dict(_USAGE)]
            assert sum(entry["total_tokens"] for entry in model.usage) == 300

    asyncio.run(scenario())


def test_missing_usage_is_reported_as_zero_not_guessed() -> None:
    captured: list[httpx.Request] = []

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(_handler(captured))
        ) as model:
            await model.decide([{"role": "user", "content": "a"}])
            assert model.usage == [
                {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
            ]

    asyncio.run(scenario())


def test_broken_usage_values_are_treated_as_zero() -> None:
    captured: list[httpx.Request] = []
    broken = {"prompt_tokens": -5, "completion_tokens": True, "total_tokens": "9"}

    async def scenario() -> None:
        async with DeepseekModel(
            "key",
            tool_specs={},
            transport=httpx.MockTransport(_handler(captured, broken)),
        ) as model:
            await model.decide([{"role": "user", "content": "a"}])
            assert model.usage == [
                {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
            ]

    asyncio.run(scenario())


def test_rejected_protocol_still_never_echoes_content() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": [{"message": {"content": "SECRET"}}]})

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(handler)
        ) as model:
            with pytest.raises(ModelProtocolError) as error:
                await model.decide([{"role": "user", "content": "a"}])
            assert "SECRET" not in str(error.value)

    asyncio.run(scenario())
