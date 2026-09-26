"""Minimal terminating agent tool loop: model -> tool -> result -> model.

Bounds are explicit: ``max_rounds`` caps loop iterations and ``total_timeout``
caps wall-clock time. External cancellation propagates and is never swallowed.
Model output only chooses a tool plus arguments — it never supplies identity:
every tool runs against the server-side session of the injected client, so a
model cannot change ``user_id`` or any approval state.
"""

from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass, field
from typing import Awaitable, Callable, Protocol


class ModelLoopExceeded(RuntimeError):
    """Round budget exhausted before the model produced a final answer."""


class ModelLoopTimeout(RuntimeError):
    """Wall-clock budget exhausted before the model produced a final answer."""


@dataclass(frozen=True)
class ToolCall:
    name: str
    arguments: dict[str, object] = field(default_factory=dict)


@dataclass(frozen=True)
class ModelDecision:
    """One model turn: a final answer (``tool_call=None``) or a tool call."""

    text: str = ""
    tool_call: ToolCall | None = None


class Model(Protocol):
    async def decide(self, messages: list[dict[str, object]]) -> ModelDecision: ...


ToolHandler = Callable[[dict[str, object]], Awaitable[object]]


@dataclass(frozen=True)
class LoopResult:
    answer: str
    rounds: int
    trace: tuple[dict[str, object], ...]


async def run_tool_loop(
    model: Model,
    tools: dict[str, ToolHandler],
    user_input: str,
    *,
    max_rounds: int = 4,
    total_timeout: float = 30.0,
) -> LoopResult:
    """Run the loop until a final answer, round exhaustion, or timeout.

    Tool failures (including unknown tool names) are converted into bounded,
    redacted tool results and fed back to the model so the loop can recover;
    they never crash the loop. Detailed exception text stays outside the model
    context. Cancellation is not a failure result: ``asyncio.CancelledError``
    propagates to the caller untouched.
    """
    if max_rounds < 1:
        raise ValueError("max_rounds must be at least 1")
    if total_timeout <= 0:
        raise ValueError("total_timeout must be positive")

    messages: list[dict[str, object]] = [{"role": "user", "content": user_input}]
    trace: list[dict[str, object]] = []

    try:
        async with asyncio.timeout(total_timeout):
            for round_no in range(1, max_rounds + 1):
                decision = await model.decide(messages)
                if decision.tool_call is None:
                    messages.append({"role": "assistant", "content": decision.text})
                    return LoopResult(
                        answer=decision.text,
                        rounds=round_no,
                        trace=tuple(trace),
                    )

                call = decision.tool_call
                handler = tools.get(call.name)
                serialized_call = (
                    {"tool": call.name, "args": call.arguments}
                    if handler is not None
                    else {"tool": "unknown_tool", "args": {}}
                )
                messages.append(
                    {
                        "role": "assistant",
                        "content": json.dumps(serialized_call, ensure_ascii=False, default=str),
                    }
                )
                if handler is None:
                    result: object = {"error": "unknown_tool"}
                else:
                    try:
                        result = await handler(call.arguments)
                    except Exception:
                        result = {"error": "tool_failed"}
                failed = isinstance(result, dict) and "error" in result
                trace.append(
                    {
                        "round": round_no,
                        "tool": "allowlisted",
                        "tool_name": call.name if handler is not None else "unknown_tool",
                        "failed": failed,
                    }
                )
                messages.append(
                    {
                        "role": "tool",
                        "content": json.dumps(result, ensure_ascii=False, default=str),
                    }
                )
            raise ModelLoopExceeded(f"exceeded max_rounds={max_rounds}")
    except TimeoutError as exc:
        raise ModelLoopTimeout(f"exceeded total_timeout={total_timeout}s") from exc
