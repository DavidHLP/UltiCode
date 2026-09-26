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
ANSWER_CONTRACT = (
    "Return one JSON object string with exactly these keys: facts, hypotheses, citations. "
    "facts must quote EVIDENCE_JSON.facts verbatim; hypotheses must be a non-empty string array "
    "drawn from EVIDENCE_JSON.allowed_hypotheses; citations must contain only doc_id values "
    "from EVIDENCE_JSON.citations."
)


def _validate_answer(answer: str, evidence: dict[str, object]) -> None:
    try:
        parsed = json.loads(answer)
    except ValueError as exc:
        raise ValueError("invalid model answer") from exc
    if not isinstance(parsed, dict) or set(parsed) != {"facts", "hypotheses", "citations"}:
        raise ValueError("invalid model answer")
    facts = parsed["facts"]
    hypotheses = parsed["hypotheses"]
    citations = parsed["citations"]
    if not isinstance(facts, list) or not facts or not all(
        isinstance(item, str) and item.strip() for item in facts
    ):
        raise ValueError("invalid model answer")
    if not isinstance(hypotheses, list) or not hypotheses or not all(
        isinstance(item, str) and item.strip() for item in hypotheses
    ):
        raise ValueError("invalid model answer")
    if not isinstance(citations, list) or not citations or not all(
        isinstance(item, str) and item.strip() for item in citations
    ):
        raise ValueError("invalid model answer")
    allowed_facts = set(evidence["facts"])  # type: ignore[arg-type]
    allowed_hypotheses = set(evidence["allowed_hypotheses"])  # type: ignore[arg-type]
    allowed_citations = {
        citation["doc_id"]
        for citation in evidence["citations"]  # type: ignore[index]
    }
    if not set(facts) <= allowed_facts:
        raise ValueError("invalid model answer")
    if not set(hypotheses) <= allowed_hypotheses:
        raise ValueError("invalid model answer")
    if not set(citations) <= allowed_citations:
        raise ValueError("invalid model answer")


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
        evidence_payload = {
            "facts": result["facts"],
            "allowed_hypotheses": result["hypotheses"],
            "citations": result["citations"],
        }
        evidence = json.dumps(evidence_payload, ensure_ascii=False)
        async with DeepseekModel(
            os.environ["DEEPSEEK_API_KEY"], tool_specs={"none": "No tool call; use supplied evidence."}
        ) as model:
            decision = await model.decide(
                [
                    {
                        "role": "user",
                        "content": (
                            "Analyze the submission using only the supplied evidence. "
                            f"{ANSWER_CONTRACT} "
                            f"EVIDENCE_JSON={evidence}"
                        ),
                    }
                ]
            )
        if decision.tool_call is not None:
            print("E2E SOURCED MODEL FAIL | reason=tool_call")
            return 1
        if not decision.text:
            print("E2E SOURCED MODEL FAIL | reason=empty_answer")
            return 1
        try:
            _validate_answer(decision.text, evidence_payload)  # type: ignore[arg-type]
        except ValueError:
            print("E2E SOURCED MODEL FAIL | reason=invalid_answer")
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
