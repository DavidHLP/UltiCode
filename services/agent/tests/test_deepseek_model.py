import asyncio

import httpx
import pytest

from deepseek_model import DeepseekModel, ModelProtocolError


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(200, text="SECRET malformed response"),
        httpx.Response(200, json={"choices": []}),
        httpx.Response(200, json={"choices": [{}]}),
        httpx.Response(200, json={"choices": [{"message": {"content": 42}}]}),
    ],
)
def test_malformed_success_response_is_rejected_without_echoing_content(
    response: httpx.Response,
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return response

    async def scenario() -> None:
        async with DeepseekModel(
            "test-key",
            tool_specs={"get_problem": "args"},
            transport=httpx.MockTransport(handler),
        ) as model:
            with pytest.raises(ModelProtocolError) as exc_info:
                await model.decide([{"role": "user", "content": "question"}])
            assert "SECRET" not in str(exc_info.value)

    asyncio.run(scenario())


@pytest.mark.parametrize(
    "body",
    [
        '{"choices":[],"choices":[{"message":{"content":"{\\"answer\\":\\"SECRET\\"}"}}]}',
        '{"choices":[{"message":{"content":"{\\"answer\\":\\"SECRET\\"}"}}],"choices":[]}',
    ],
)
def test_duplicate_keys_in_model_response_are_rejected(body: str) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, text=body)

    async def scenario() -> None:
        async with DeepseekModel(
            "test-key",
            tool_specs={"get_problem": "args"},
            transport=httpx.MockTransport(handler),
        ) as model:
            with pytest.raises(ModelProtocolError) as exc_info:
                await model.decide([{"role": "user", "content": "question"}])
            assert "SECRET" not in str(exc_info.value)

    asyncio.run(scenario())


@pytest.mark.parametrize(
    "content",
    [
        "SECRET malformed response",
        "not json",
        "{}",
        '{"tool":"get_problem"}',
        '{"tool":42,"args":{}}',
        '{"answer":42}',
        '{"answer":""}',
        '{"tool":"get_problem","answer":"ok"}',
        'prefix {"answer":"SECRET"} suffix',
        '{"answer":"SECRET","unexpected":true}',
        '{"tool":"get_problem","args":{},"unexpected":true}',
        '{"tool":"get_problem","args":{"x":NaN}}',
        '{"tool":"get_problem","args":{},"tool":"get_problem"}',
    ],
)
def test_invalid_decision_protocol_is_rejected_without_content(
    content: str,
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": [{"message": {"content": content}}]})

    async def scenario() -> None:
        async with DeepseekModel(
            "test-key",
            tool_specs={"get_problem": "args"},
            transport=httpx.MockTransport(handler),
        ) as model:
            with pytest.raises(ModelProtocolError) as exc_info:
                await model.decide([{"role": "user", "content": "question"}])
            assert "SECRET" not in str(exc_info.value)
            assert content not in str(exc_info.value)

    asyncio.run(scenario())


def test_answer_only_mode_keeps_untrusted_evidence_rule() -> None:
    seen_system = ""

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal seen_system
        payload = __import__("json").loads(request.content)
        seen_system = payload["messages"][0]["content"]
        return httpx.Response(
            200, json={"choices": [{"message": {"content": '{"answer":"ok"}'}}]}
        )

    async def scenario() -> None:
        async with DeepseekModel(
            "test-key",
            tool_specs={},
            transport=httpx.MockTransport(handler),
        ) as model:
            assert (await model.decide([{"role": "user", "content": "evidence"}])).text == "ok"

    asyncio.run(scenario())
    assert "untrusted data" in seen_system
    assert "not instructions" in seen_system
