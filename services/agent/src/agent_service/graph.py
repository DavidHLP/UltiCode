"""The single LangGraph implementation of read-only loops and workflow interrupts."""

from __future__ import annotations

import asyncio
import json
from typing import TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import interrupt

from agent_loop import LoopResult, Model, ModelDecision, ModelLoopExceeded, ToolHandler


class LoopState(TypedDict, total=False):
    messages: list[dict[str, object]]
    trace: list[dict[str, object]]
    rounds: int
    decision: dict[str, object]
    answer: str


def _json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, default=str)

async def ainvoke_untraced(
    graph, input: object, config: dict[str, object] | None = None,
    *, context: dict[str, object] | None = None, durability: str | None = None,
):
    """Disable ambient LangSmith tracing without persisting request resources.

    ``durability="sync"`` keeps checkpoint writes on the calling task: the
    checkpointer shares one SQLite file with the canonical store, so a
    background checkpoint writer would race the node's own store transaction.
    """
    from langsmith import tracing_context

    extra = {"durability": durability} if durability is not None else {}
    with tracing_context(enabled=False):
        return await graph.ainvoke(input, config, context=context, **extra)


def build_readonly_graph(model: Model, tools: dict[str, ToolHandler], *, max_rounds: int = 4):
    """Build the sole model→conditional tools→model loop implementation."""
    if max_rounds < 1:
        raise ValueError("max_rounds must be at least 1")

    async def decide(state: LoopState) -> dict[str, object]:
        rounds = state.get("rounds", 0)
        if rounds >= max_rounds:
            raise ModelLoopExceeded(f"exceeded max_rounds={max_rounds}")
        decision = await model.decide(state["messages"])
        if not isinstance(decision, ModelDecision):
            raise TypeError("model decision must be ModelDecision")
        call = decision.tool_call
        patch: dict[str, object] = {
            "decision": {"text": decision.text, "tool_call": (
                {"name": call.name, "arguments": call.arguments} if call else None
            )},
            "rounds": rounds + 1,
        }
        if call is None:
            patch["answer"] = decision.text
            patch["messages"] = [*state["messages"], {"role": "assistant", "content": decision.text}]
        else:
            encoded = (
                {"tool": call.name, "args": call.arguments}
                if tools.get(call.name) is not None
                else {"tool": "unknown_tool", "args": {}}
            )
            patch["messages"] = [*state["messages"], {"role": "assistant", "content": _json(encoded)}]
        return patch

    async def execute_tool(state: LoopState) -> dict[str, object]:
        raw_call = state["decision"].get("tool_call")
        if not isinstance(raw_call, dict):
            raise ValueError("invalid checkpointed tool call")
        name, arguments = raw_call.get("name"), raw_call.get("arguments")
        if not isinstance(name, str) or not isinstance(arguments, dict):
            raise ValueError("invalid checkpointed tool call")
        handler = tools.get(name)
        if handler is None:
            result: object = {"error": "unknown_tool"}
            tool_name = "unknown_tool"
        else:
            try:
                result = await handler(arguments)
            except Exception:
                result = {"error": "tool_failed"}
            tool_name = name
        trace = [*state.get("trace", []), {
            "round": state["rounds"], "tool": "allowlisted", "tool_name": tool_name,
            "failed": isinstance(result, dict) and "error" in result,
        }]
        messages = [*state["messages"], {"role": "tool", "content": _json(result)}]
        if state["rounds"] >= max_rounds:
            raise ModelLoopExceeded(f"exceeded max_rounds={max_rounds}")
        return {"trace": trace, "messages": messages}

    graph = StateGraph(LoopState)
    graph.add_node("model", decide)
    graph.add_node("tool", execute_tool)
    graph.add_edge(START, "model")
    graph.add_conditional_edges(
        "model", lambda state: "tool" if state["decision"]["tool_call"] is not None else END,
        {"tool": "tool", END: END},
    )
    graph.add_edge("tool", "model")
    return graph.compile()


async def run_readonly_graph(
    model: Model, tools: dict[str, ToolHandler], user_input: str, *,
    max_rounds: int = 4, total_timeout: float = 30.0,
) -> LoopResult:
    if max_rounds < 1:
        raise ValueError("max_rounds must be at least 1")
    if total_timeout <= 0:
        raise ValueError("total_timeout must be positive")
    graph = build_readonly_graph(model, tools, max_rounds=max_rounds)
    try:
        async with asyncio.timeout(total_timeout):
            state = await ainvoke_untraced(graph, {
                "messages": [{"role": "user", "content": user_input}], "trace": [], "rounds": 0,
            }, config={"recursion_limit": max_rounds * 2 + 2})
    except TimeoutError as exc:
        from agent_loop import ModelLoopTimeout
        raise ModelLoopTimeout(f"exceeded total_timeout={total_timeout}s") from exc
    return LoopResult(str(state.get("answer", "")), int(state.get("rounds", 0)), tuple(state.get("trace", [])))


class WorkflowState(TypedDict, total=False):
    thread_id: str
    run_id: str
    status: str
    draft_version: int
    question: str
    source_facts: dict[str, object]
    action: dict[str, object]
    action_result: dict[str, object]


class WorkflowContext(TypedDict, total=False):
    # Request-scoped only. LangGraph persists state, never these resources.
    execute_action: object
    analysis_runtime: tuple[Model, dict[str, ToolHandler]]

def build_workflow_graph(*, checkpointer, total_timeout: float = 30.0):
    """Checkpointed action graph; checkpoint values stay JSON-only."""
    async def await_action(state: WorkflowState) -> dict[str, object]:
        action = interrupt({
            "thread_id": state["thread_id"], "run_id": state["run_id"],
            "status": state.get("status"), "draft_version": state.get("draft_version"),
        })
        return {"action": action}

    async def dispatch(state: WorkflowState, runtime: Runtime[WorkflowContext]) -> dict[str, object]:
        action = state.get("action")
        if not isinstance(action, dict) or set(action) != {"kind", "payload", "expected_run", "expected_version"}:
            raise ValueError("invalid server workflow action")
        kind, payload = action["kind"], action["payload"]
        if kind not in {"analyze", "edit", "confirm", "save", "recover", "cancel"} or not isinstance(payload, dict):
            raise ValueError("unsupported workflow action")
        canonical = payload.get("_canonical_state")
        if not isinstance(canonical, dict) or set(canonical) != {"status", "run_id", "draft_version"}:
            raise ValueError("missing canonical workflow state")
        status, run_id, version = canonical["status"], canonical["run_id"], canonical["draft_version"]
        if (not isinstance(status, str) or not isinstance(run_id, str) or type(version) is not int
                or action["expected_run"] != run_id or type(action["expected_version"]) is not int):
            raise ValueError("invalid canonical workflow state")
        expected_version = action["expected_version"]
        if expected_version != version and not (kind == "edit" and expected_version + 1 == version):
            raise ValueError("stale workflow action")
        analysis_result: dict[str, object] = {}
        if kind == "analyze":
            analysis_runtime = runtime.context.get("analysis_runtime")
            if not isinstance(analysis_runtime, tuple) or len(analysis_runtime) != 2:
                raise RuntimeError("model_budget_blocked")
            model, tools = analysis_runtime
            from boundary_evaluation import BOUNDARY_ANSWER_CONTRACT
            prompt = f"{BOUNDARY_ANSWER_CONTRACT}\nINPUT_JSON " + json.dumps(
                {"question": state.get("question", ""), "facts": state.get("source_facts", {})},
                ensure_ascii=False, separators=(",", ":"),
            )
            loop = await run_readonly_graph(model, tools, prompt, max_rounds=4, total_timeout=total_timeout)
            analysis_result = {"answer": loop.answer, "trace": list(loop.trace), "rounds": loop.rounds}
        execute_action = runtime.context.get("execute_action")
        if not callable(execute_action):
            raise RuntimeError("workflow_action_unavailable")
        result = await execute_action(action, analysis_result)
        if not isinstance(result, dict) or any(
            key not in result for key in ("status", "run_id", "draft_version")
        ):
            raise RuntimeError("invalid_workflow_action_result")
        state_update = {
            key: result[key] for key in ("status", "run_id", "draft_version")
        }
        if "action_result" in result:
            state_update["action_result"] = result["action_result"]
        return state_update

    graph = StateGraph(WorkflowState, context_schema=WorkflowContext)
    graph.add_node("await_action", await_action)
    graph.add_node("dispatch", dispatch)
    graph.add_edge(START, "await_action")
    graph.add_edge("await_action", "dispatch")
    graph.add_edge("dispatch", "await_action")
    return graph.compile(checkpointer=checkpointer)
