"""Minimal terminating agent tool loop: model -> tool -> result -> model.

Bounds are explicit: ``max_rounds`` caps loop iterations and ``total_timeout``
caps wall-clock time. External cancellation propagates and is never swallowed.
Model output only chooses a tool plus arguments — it never supplies identity:
every tool runs against the server-side session of the injected client, so a
model cannot change ``user_id`` or any approval state.
"""

from __future__ import annotations

import asyncio
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

    from agent_service.graph import run_readonly_graph

    try:
        async with asyncio.timeout(total_timeout):
            return await run_readonly_graph(
                model, tools, user_input, max_rounds=max_rounds, total_timeout=total_timeout
            )
    except TimeoutError as exc:
        raise ModelLoopTimeout(f"exceeded total_timeout={total_timeout}s") from exc
