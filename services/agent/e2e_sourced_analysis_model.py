"""U02 real-model sourced-analysis evaluation over authenticated read-only facts.

The local corpus remains agent-authored synthetic material. This opt-in path sends only the
validated submission projection and bounded retrieved evidence to a real DeepSeek model; it never
prints the model answer, source text, credentials, or submission contents.
"""

from __future__ import annotations

import asyncio
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from deepseek_model import DeepseekModel
from sourced_analysis import analyze_submission
from ulticode_client import UlticodeClient
from ulticode_tools import build_tools

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")
QUESTION = "Wrong Answer 状态说明了什么？只依据提交事实和带来源检索结果回答。"


async def main() -> int:
    async with UlticodeClient(APP_BASE, AUTH_BASE) as client:
        await client.login(
            os.environ["ULTICODE_E2E_USERNAME"], os.environ["ULTICODE_E2E_PASSWORD"]
        )
        tools = build_tools(client)
        listing = await tools["get_my_submissions"]({"page": 1, "pageSize": 100})
        items = listing["items"]  # type: ignore[index]
        matching_items = [item for item in items if item.get("status") == "Wrong Answer"]
        if not matching_items:
            print("E2E SOURCED MODEL FAIL | reason=no_wrong_answer_submission")
            return 1
        result = analyze_submission(matching_items[0], QUESTION)
        if not result["citations"]:
            print("E2E SOURCED MODEL FAIL | reason=no_citation")
            return 1
        evidence = json.dumps(
            {"facts": result["facts"], "citations": result["citations"]},
            ensure_ascii=False,
        )
        async with DeepseekModel(
            os.environ["DEEPSEEK_API_KEY"], tool_specs={"none": "No tool call; use supplied evidence."}
        ) as model:
            decision = await model.decide(
                [
                    {
                        "role": "user",
                        "content": (
                            "Analyze the submission using only the supplied evidence. "
                            "Separate confirmed facts from hypotheses. "
                            f"EVIDENCE_JSON={evidence}"
                        ),
                    }
                ]
            )
        if not decision.text:
            print("E2E SOURCED MODEL FAIL | reason=empty_answer")
            return 1

    print(
        "E2E SOURCED MODEL PASS | corpus=agent-authored-synthetic "
        "| input=validated-user-projection | answer=withheld"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except Exception as exc:
        print(f"E2E SOURCED MODEL FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
