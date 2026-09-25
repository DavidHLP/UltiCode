"""DAV-41 U01 e2e — REAL model business Q&A over the read-only tool loop.

Prints only status, counts, and answer length. It withholds response bodies, tool names,
model answer content, cookies, and submission source.

Scope label: REAL model (DeepSeek) + REAL local UltiCode stack.

    ULTICODE_E2E_USERNAME=... ULTICODE_E2E_PASSWORD=... DEEPSEEK_API_KEY=... \
      uv run python e2e_model_qa.py
"""

from __future__ import annotations

import asyncio
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from agent_loop import run_tool_loop
from deepseek_model import DeepseekModel
from ulticode_client import UlticodeClient
from ulticode_tools import TOOL_SPECS, build_tools

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")

QUESTION = "第 7 题的标题和难度是什么？我在这道题上有提交记录吗？只依据工具结果回答。"


async def main() -> int:
    async with UlticodeClient(APP_BASE, AUTH_BASE) as client:
        await client.login(
            os.environ["ULTICODE_E2E_USERNAME"], os.environ["ULTICODE_E2E_PASSWORD"]
        )
        tools = build_tools(client)
        async with DeepseekModel(
            os.environ["DEEPSEEK_API_KEY"], tool_specs=TOOL_SPECS
        ) as model:
            result = await run_tool_loop(
                model, tools, QUESTION, max_rounds=4, total_timeout=90.0
            )

    tool_count = len(result.trace)
    failed_count = sum(bool(step["failed"]) for step in result.trace)
    print(
        f"OK model_qa rounds={result.rounds} "
        f"tool_count={tool_count} failed_count={failed_count}"
    )
    print(f"answer_chars={len(result.answer)} (content withheld from logs)")
    if failed_count or tool_count == 0:
        print("E2E MODEL QA FAIL | reason=tool_contract")
        return 1
    print("E2E MODEL QA PASS | scope=REAL model + REAL local stack")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except Exception as exc:
        print(f"E2E MODEL QA FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
