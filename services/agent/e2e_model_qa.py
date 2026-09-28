"""DAV-41 U01 e2e — REAL model business Q&A over the read-only tool loop.

Prints only status, counts, and answer length. It withholds response bodies, tool names,
model answer content, cookies, and submission source.

Scope label: REAL model (DeepSeek) + REAL local UltiCode stack.

    ULTICODE_E2E_USERNAME=... ULTICODE_E2E_PASSWORD=... DEEPSEEK_API_KEY=... \
      uv run python e2e_model_qa.py
"""

from __future__ import annotations

import asyncio
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from agent_loop import run_tool_loop
from deepseek_model import DeepseekModel, model_label
from ulticode_client import UlticodeClient
from ulticode_tools import TOOL_SPECS, build_tools

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")
QUESTION = (
    "第 7 题的标题和难度是什么？我在这道题上有提交记录吗？只依据工具结果回答。"
    "返回一个 JSON 对象字符串，键必须是 title、difficulty、has_submission。"
)


def _reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate key")
        result[key] = value
    return result


def _validate_answer(answer: str, problem: dict[str, object], has_submission: bool) -> bool:
    try:
        parsed = json.loads(answer, object_pairs_hook=_reject_duplicate_keys)
    except ValueError:
        return False
    return (
        isinstance(parsed, dict)
        and set(parsed) == {"title", "difficulty", "has_submission"}
        and parsed.get("title") == problem.get("title")
        and parsed.get("difficulty") == problem.get("difficulty")
        and parsed.get("has_submission") is has_submission
    )


class ModelNotNamed(RuntimeError):
    """A billed run must name the model instead of inheriting a default."""


def _priced_model() -> str:
    model = os.environ.get("DEEPSEEK_MODEL", "").strip()
    if not model:
        raise ModelNotNamed("DEEPSEEK_MODEL must be set for a real-model run")
    return model


def _report_usage(model: object) -> None:
    """Token accounting for billed calls. Values only, never prompt or answer text.

    An unreported block prints ``unknown``: the call was sent and may have been
    billed, so a zero would misstate the cost.
    """
    usage = getattr(model, "usage", None) or []
    if not usage:
        return
    totals = [entry.get("total_tokens") for entry in usage]
    if any(total is None for total in totals):
        print(
            f"E2E MODEL QA USAGE | calls={len(usage)} total_tokens=unknown "
            "reason=provider_did_not_report_usage"
        )
        return
    print(f"E2E MODEL QA USAGE | calls={len(usage)} total_tokens={sum(totals)}")




async def main() -> int:
    if not os.environ.get("DEEPSEEK_MODEL", "").strip():
        # Fail closed: the adapter default and the provider's current model
        # identifiers have both changed, so assume nothing on a billed run.
        print("E2E MODEL QA FAIL | reason=deepseek_model_required")
        return 1
    async with UlticodeClient(APP_BASE, AUTH_BASE) as client:
        await client.login(
            os.environ["ULTICODE_E2E_USERNAME"], os.environ["ULTICODE_E2E_PASSWORD"]
        )
        raw_tools = build_tools(client)
        observed_problem: dict[str, object] | None = None
        observed_submission_total: int | None = None

        def track(name: str, handler: object) -> object:
            async def tracked(arguments: dict[str, object]) -> object:
                nonlocal observed_problem, observed_submission_total
                result = await handler(arguments)  # type: ignore[operator]
                if name == "get_problem" and isinstance(result, dict) and result.get("id") == 7:
                    observed_problem = result
                if (
                    name == "get_problem_submissions"
                    and isinstance(arguments.get("problemId"), int)
                    and arguments["problemId"] == 7
                    and isinstance(result, dict)
                    and isinstance(result.get("total"), int)
                ):
                    observed_submission_total = result["total"]  # type: ignore[assignment]
                return result

            return tracked

        tools = {name: track(name, handler) for name, handler in raw_tools.items()}
        # Bound once so the evidence names the model that was billed.
        model_name = _priced_model()
        async with DeepseekModel(
            os.environ["DEEPSEEK_API_KEY"],
            tool_specs=TOOL_SPECS,
            # Named explicitly: the adapter default and the provider's current
            # identifiers have both changed, so a costed run must not assume one.
            model=model_name,
            # Cost guard rails: bounded output and a bounded number of calls.
            max_calls=int(os.environ.get("DEEPSEEK_MAX_CALLS", "8")),
            # Left at 300 deliberately: this loop may make up to 8 calls, and the
            # real-model run passes at 300. Only the single-call sourced-analysis
            # smoke needed a larger cap (see its `finish_reason=length` note).
            max_tokens=int(os.environ.get("DEEPSEEK_MAX_TOKENS", "300")),
        ) as model:
            try:
                result = await run_tool_loop(
                    model, tools, QUESTION, max_rounds=4, total_timeout=90.0
                )
            finally:
                _report_usage(model)

    tool_names = {step["tool_name"] for step in result.trace}
    failed_count = sum(bool(step["failed"]) for step in result.trace)
    print(
        f"OK model_qa model={model_label(model_name)} rounds={result.rounds} "
        f"tool_count={len(result.trace)} failed_count={failed_count}"
    )
    print(f"answer_chars={len(result.answer)} (content withheld from logs)")
    required_tools = {"get_problem", "get_problem_submissions"}
    if (
        failed_count
        or not required_tools.issubset(tool_names)
        or observed_problem is None
        or observed_submission_total is None
    ):
        print("E2E MODEL QA FAIL | reason=tool_contract")
        return 1
    if not _validate_answer(result.answer, observed_problem, observed_submission_total > 0):
        print("E2E MODEL QA FAIL | reason=answer_contract")
        return 1
    print("E2E MODEL QA PASS | scope=REAL model + REAL local stack")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except Exception as exc:
        print(f"E2E MODEL QA FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
