"""U02 sample-only retrieval and sourced-analysis smoke path.

The retrieved corpus is agent-authored synthetic Markdown, not a real user submission, an UltiCode
DTO, or licensed user material. The input analyzed by this opt-in path is the authenticated user's
validated read-only submission projection; this path does not make a real model call.
"""

from __future__ import annotations

import asyncio
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from sourced_analysis import analyze_submission, first_wrong_answer_submission
from ulticode_client import UlticodeClient
from ulticode_tools import build_tools

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")
QUESTION = "Wrong Answer 状态说明了什么？"


async def main() -> int:
    async with UlticodeClient(APP_BASE, AUTH_BASE) as client:
        await client.login(
            os.environ["ULTICODE_E2E_USERNAME"], os.environ["ULTICODE_E2E_PASSWORD"]
        )
        tools = build_tools(client)
        matching = await first_wrong_answer_submission(tools)
        if matching is None:
            print("E2E SOURCED ANALYSIS FAIL | reason=no_wrong_answer_submission")
            return 1
        result = analyze_submission(matching, QUESTION)
        if not result["citations"]:
            print("E2E SOURCED ANALYSIS FAIL | reason=no_citation")
            return 1
        # A missing check must never read as a passing check: the count has to
        # match the citation count and every verdict has to be verified.
        checks = list(result["citation_checks"])  # type: ignore[arg-type]
        if len(checks) != len(result["citations"]) or any(  # type: ignore[arg-type]
            check.get("verdict") != "verified" for check in checks
        ):
            print("E2E SOURCED ANALYSIS FAIL | reason=unverifiable_citation")
            return 1

    print(
        f"OK sourced_analysis facts={len(result['facts'])} "
        f"hypotheses={len(result['hypotheses'])} citations={len(result['citations'])}"
    )
    print("E2E SOURCED ANALYSIS PASS | corpus=agent-authored-synthetic | input=validated-user-projection")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except Exception as exc:
        print(f"E2E SOURCED ANALYSIS FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
