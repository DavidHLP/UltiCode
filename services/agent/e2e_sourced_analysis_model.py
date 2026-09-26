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
from sourced_analysis import analyze_submission, first_wrong_answer_submission
from ulticode_client import UlticodeClient
from ulticode_tools import build_tools

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")
QUESTION = "Wrong Answer 状态说明了什么？只依据提交事实和带来源检索结果回答。"
# The adapter in answer-only mode requires {"answer": "<text>"}. The evidence
# contract therefore has to be carried *inside* that answer string, otherwise the
# two protocols conflict and the model can satisfy only one of them.
ANSWER_CONTRACT = (
    "Reply with one JSON object of the form {\"answer\": \"<json string>\"}. The answer "
    "string must itself be a JSON object with exactly these keys: facts, hypotheses, citations. "
    "facts must quote EVIDENCE_JSON.facts verbatim; hypotheses must be a non-empty string array "
    "drawn from EVIDENCE_JSON.allowed_hypotheses; citations must contain only doc_id values "
    "from EVIDENCE_JSON.citations. Do not add prose outside the JSON object."
)


def _reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate key")
        result[key] = value
    return result


def _answer_payload(answer: str) -> str:
    """Unwrap the evidence JSON carried inside the adapter's answer string.

    The adapter parses the outer {"answer": ...} envelope; the evidence contract
    lives in the answer string, so it has to be unwrapped before validation.
    """
    try:
        envelope = json.loads(answer, object_pairs_hook=_reject_duplicate_keys)
    except (json.JSONDecodeError, ValueError):
        return answer
    if isinstance(envelope, dict) and set(envelope) == {"answer"} and isinstance(
        envelope["answer"], str
    ):
        return envelope["answer"]
    return answer


def _validate_answer(answer: str, evidence: dict[str, object]) -> None:
    try:
        parsed = json.loads(answer, object_pairs_hook=_reject_duplicate_keys)
    except (json.JSONDecodeError, ValueError) as exc:
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
    if not os.environ.get("DEEPSEEK_API_KEY"):
        # Fail closed: without a key the run must not touch the model at all.
        print("E2E SOURCED MODEL FAIL | reason=missing_api_key")
        return 1
    async with UlticodeClient(APP_BASE, AUTH_BASE) as client:
        await client.login(
            os.environ["ULTICODE_E2E_USERNAME"], os.environ["ULTICODE_E2E_PASSWORD"]
        )
        tools = build_tools(client)
        matching = await first_wrong_answer_submission(tools)
        if matching is None:
            print("E2E SOURCED MODEL FAIL | reason=no_wrong_answer_submission")
            return 1
        result = analyze_submission(matching, QUESTION)
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
            os.environ["DEEPSEEK_API_KEY"],
            tool_specs={},
            model=os.environ.get("DEEPSEEK_MODEL", "deepseek-flash"),
            # One decision per run with a bounded output: the worst case is a
            # single capped call, never an open-ended loop.
            max_calls=int(os.environ.get("DEEPSEEK_MAX_CALLS", "1")),
            max_tokens=int(os.environ.get("DEEPSEEK_MAX_TOKENS", "300")),
            max_prompt_tokens=int(os.environ.get("DEEPSEEK_MAX_PROMPT_TOKENS", "24000")),
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
        usage = model.usage
        if usage:
            total = sum(entry["total_tokens"] for entry in usage)
            print(f"E2E SOURCED MODEL USAGE | calls={len(usage)} total_tokens={total}")
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
