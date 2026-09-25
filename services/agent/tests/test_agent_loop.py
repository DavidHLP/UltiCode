import asyncio

import pytest

from agent_loop import (
    ModelDecision,
    ModelLoopExceeded,
    ModelLoopTimeout,
    ToolCall,
    run_tool_loop,
)


class ScriptedModel:
    """Returns preloaded decisions and records the messages it was shown."""

    def __init__(self, decisions: list[ModelDecision]) -> None:
        self._decisions = list(decisions)
        self.seen: list[list[dict[str, object]]] = []

    async def decide(self, messages: list[dict[str, object]]) -> ModelDecision:
        self.seen.append(list(messages))
        if not self._decisions:
            raise AssertionError("model called more times than scripted")
        return self._decisions.pop(0)


class HangingModel:
    async def decide(self, messages: list[dict[str, object]]) -> ModelDecision:
        await asyncio.Event().wait()
        raise AssertionError("unreachable")


def test_normal_path_calls_tool_then_answers() -> None:
    async def handler(arguments: dict[str, object]) -> object:
        return {"id": arguments["id"], "title": "sample"}

    async def scenario() -> None:
        model = ScriptedModel(
            [
                ModelDecision(text="looking", tool_call=ToolCall("get_problem", {"id": 7})),
                ModelDecision(text="the title is sample"),
            ]
        )
        result = await run_tool_loop(
            model, {"get_problem": handler}, "问题 7 的标题？", max_rounds=4
        )
        assert result.answer == "the title is sample"
        assert result.rounds == 2
        assert [step["failed"] for step in result.trace] == [False]
        # the second model turn must see the tool result message
        tool_messages = [m for m in model.seen[1] if m["role"] == "tool"]
        assert len(tool_messages) == 1
        assert "sample" in str(tool_messages[0]["content"])

    asyncio.run(scenario())


def test_round_limit_is_raised_and_model_not_called_again() -> None:
    async def handler(arguments: dict[str, object]) -> object:
        return {"ok": True}

    async def scenario() -> None:
        model = ScriptedModel(
            [
                ModelDecision(tool_call=ToolCall("echo", {})),
                ModelDecision(tool_call=ToolCall("echo", {})),
            ]
        )
        with pytest.raises(ModelLoopExceeded, match="max_rounds=2"):
            await run_tool_loop(model, {"echo": handler}, "loop forever", max_rounds=2)
        # ScriptedModel raises if a third call happens; assert it saw exactly two.
        assert len(model.seen) == 2

    asyncio.run(scenario())


def test_total_timeout_is_raised_for_a_hanging_model() -> None:
    async def scenario() -> None:
        with pytest.raises(ModelLoopTimeout, match="total_timeout"):
            await run_tool_loop(
                HangingModel(), {}, "hang", max_rounds=3, total_timeout=0.05
            )

    asyncio.run(scenario())


def test_tool_failure_is_redacted_before_reaching_model() -> None:
    async def broken(arguments: dict[str, object]) -> object:
        raise RuntimeError("SECRET SOURCE user=u-secret input=stdin")

    async def scenario() -> None:
        model = ScriptedModel(
            [
                ModelDecision(tool_call=ToolCall("broken", {})),
                ModelDecision(text="recovered"),
            ]
        )
        result = await run_tool_loop(model, {"broken": broken}, "try it", max_rounds=4)
        assert result.answer == "recovered"
        assert result.trace == ({"round": 1, "tool": "allowlisted", "failed": True},)
        tool_messages = [m for m in model.seen[1] if m["role"] == "tool"]
        content = str(tool_messages[0]["content"])
        assert content == '{"error": "tool_failed"}'
        assert "SECRET" not in content
        assert "u-secret" not in content
        assert "stdin" not in content

def test_unknown_tool_is_reported_without_echoing_model_name() -> None:
    async def scenario() -> None:
        model = ScriptedModel(
            [
                ModelDecision(tool_call=ToolCall("SECRET user=u-secret", {})),
                ModelDecision(text="done"),
            ]
        )
        result = await run_tool_loop(model, {}, "call it", max_rounds=4)
        assert result.trace == ({"round": 1, "tool": "allowlisted", "failed": True},)
        tool_messages = [m for m in model.seen[1] if m["role"] == "tool"]
        assert str(tool_messages[0]["content"]) == '{"error": "unknown_tool"}'
        assert "SECRET" not in str(model.seen[1])

    asyncio.run(scenario())


def test_external_cancellation_propagates_and_is_not_swallowed() -> None:
    async def scenario() -> None:
        task = asyncio.create_task(
            run_tool_loop(HangingModel(), {}, "hang", max_rounds=3, total_timeout=30.0)
        )
        await asyncio.sleep(0.05)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert task.cancelled()

    asyncio.run(scenario())
