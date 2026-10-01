import asyncio
import json

import httpx
import pytest

from deepseek_model import (
    MAX_PROMPT_TOKENS,
    MAX_TOKENS,
    PROMPT_TOKEN_UPPER_BYTES,
    DeepseekModel,
    ModelBudgetExceeded,
    ModelProtocolError,
)

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
            max_prompt_tokens=10,
            transport=httpx.MockTransport(_handler(captured)),
        ) as model:
            with pytest.raises(ModelBudgetExceeded):
                await model.decide([{"role": "user", "content": "x" * 500}])
            assert model.calls_made == 0

    asyncio.run(scenario())
    assert captured == []


def test_prompt_budget_is_measured_in_tokens_not_characters() -> None:
    captured: list[httpx.Request] = []
    # The system prompt alone is counted, so the usable user budget is smaller
    # than the nominal token cap.
    # ASCII is one UTF-8 byte per character, so bytes == chars here.
    budget_chars = MAX_PROMPT_TOKENS // PROMPT_TOKEN_UPPER_BYTES

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(_handler(captured))
        ) as model:
            with pytest.raises(ModelBudgetExceeded):
                await model.decide([{"role": "user", "content": "x" * (budget_chars + 500)}])
            assert model.calls_made == 0
            await model.decide([{"role": "user", "content": "x" * 100}])

    asyncio.run(scenario())
    assert len(captured) == 1


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


def test_missing_usage_is_reported_as_unknown_not_zero() -> None:
    captured: list[httpx.Request] = []

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(_handler(captured))
        ) as model:
            await model.decide([{"role": "user", "content": "a"}])
            assert model.usage == [
                {"prompt_tokens": None, "completion_tokens": None, "total_tokens": None}
            ]

    asyncio.run(scenario())


def test_broken_usage_values_are_treated_as_unknown() -> None:
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
                {"prompt_tokens": None, "completion_tokens": None, "total_tokens": None}
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


def test_billed_malformed_response_is_still_accounted() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": [], "usage": dict(_USAGE)})

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(handler)
        ) as model:
            with pytest.raises(ModelProtocolError):
                await model.decide([{"role": "user", "content": "a"}])
            # The provider billed this call; the cost record must survive the
            # protocol error, otherwise the spend is invisible exactly when
            # something already went wrong.
            assert model.usage == [dict(_USAGE)]
            assert model.calls_made == 1

    asyncio.run(scenario())


def test_non_dict_response_is_accounted_as_unknown() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=["not", "an", "object"])

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(handler)
        ) as model:
            with pytest.raises(ModelProtocolError):
                await model.decide([{"role": "user", "content": "a"}])
            # The request was sent, so the call is accounted as unknown rather
            # than silently dropped.
            assert model.usage == [
                {"prompt_tokens": None, "completion_tokens": None, "total_tokens": None}
            ]

    asyncio.run(scenario())


def test_billed_call_with_a_malformed_body_still_records_usage() -> None:
    """A sent-and-billed request must leave an accounting trace either way."""

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, text="not json at all")

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(handler)
        ) as model:
            with pytest.raises(ModelProtocolError):
                await model.decide([{"role": "user", "content": "a"}])
            # Recorded before parsing, so a billed call is never invisible.
            assert model.usage == [
                {"prompt_tokens": None, "completion_tokens": None, "total_tokens": None}
            ]

    asyncio.run(scenario())


def test_reported_usage_replaces_the_placeholder() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "choices": [{"message": {"content": '{"answer":"ok"}'}}],
                "usage": {"prompt_tokens": 7, "completion_tokens": 3, "total_tokens": 10},
            },
        )

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(handler)
        ) as model:
            await model.decide([{"role": "user", "content": "a"}])
            assert model.usage == [
                {"prompt_tokens": 7, "completion_tokens": 3, "total_tokens": 10}
            ]
            assert model.metering == [
                {"reserved_micro_usd": None, "actual_micro_usd": 6}
            ]

    asyncio.run(scenario())


def test_prompt_budget_is_enforced_in_bytes_not_a_guessed_ratio() -> None:
    """A character ratio can under-count; UTF-8 bytes are a real upper bound."""
    captured: list[httpx.Request] = []
    cjk = "错" * 200  # 3 UTF-8 bytes each, so bytes >> characters

    async def scenario() -> None:
        async with DeepseekModel(
            "key",
            tool_specs={},
            # 400 tokens: the character count alone would fit, the byte count must not.
            max_prompt_tokens=400,
            transport=httpx.MockTransport(_handler(captured)),
        ) as model:
            with pytest.raises(ModelBudgetExceeded):
                await model.decide([{"role": "user", "content": cjk}])
            assert model.calls_made == 0

    asyncio.run(scenario())
    assert captured == []


def test_provider_response_model_is_captured_and_sanitized() -> None:
    """Provider metadata is untrusted: it is recorded sanitized, never raw."""

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "model": "deepseek-v4.1-flash\nX-Injected: 1",
                "choices": [{"message": {"content": '{"answer":"ok"}'}}],
            },
        )

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(handler)
        ) as model:
            await model.decide([{"role": "user", "content": "a"}])
            assert model.response_models == ["deepseek-v4.1-flash?X-Injected??1"]

    asyncio.run(scenario())


@pytest.mark.parametrize("reported", [None, "", "   ", 7])
def test_missing_provider_response_model_is_unknown(reported: object) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        body: dict[str, object] = {
            "choices": [{"message": {"content": '{"answer":"ok"}'}}]
        }
        if reported is not None:
            body["model"] = reported
        return httpx.Response(200, json=body)

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(handler)
        ) as model:
            await model.decide([{"role": "user", "content": "a"}])
            assert model.response_models == ["unknown"]

    asyncio.run(scenario())


def test_response_identity_stays_aligned_with_every_sent_call() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": []})

    async def scenario() -> None:
        async with DeepseekModel(
            "key", tool_specs={}, transport=httpx.MockTransport(handler)
        ) as model:
            with pytest.raises(ModelProtocolError):
                await model.decide([{"role": "user", "content": "a"}])
            # A billed call with no reported model still leaves an identity slot.
            assert model.response_models == ["unknown"]
            assert len(model.response_models) == len(model.usage) == 1

    asyncio.run(scenario())
