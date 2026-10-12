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

from deepseek_model import DeepseekModel, model_label
from sourced_analysis import analyze_submission, first_wrong_answer_submission
from ulticode_client import UlticodeClient
from ulticode_tools import build_tools
from model_budget import authorized_model, acceptance_transport

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


class ModelNotNamed(RuntimeError):
    """A billed run must name the model instead of inheriting a default."""


def _priced_model() -> str:
    model = os.environ.get("DEEPSEEK_MODEL", "").strip()
    if not model:
        raise ModelNotNamed("DEEPSEEK_MODEL must be set for a real-model run")
    return model


def _report_usage(model: object) -> None:
    """Emit token accounting. Values only; no prompt, answer, or token content.

    An unreported block prints ``unknown`` rather than 0: the call was sent and
    may have been billed, so a zero would be a false claim about cost.
    """
    usage = getattr(model, "usage", None) or []
    if not usage:
        return
    totals = [entry.get("total_tokens") for entry in usage]
    if any(total is None for total in totals):
        print(
            f"E2E SOURCED MODEL USAGE | calls={len(usage)} total_tokens=unknown "
            "reason=provider_did_not_report_usage"
        )
        return
    print(f"E2E SOURCED MODEL USAGE | calls={len(usage)} total_tokens={sum(totals)}")


async def _main(artifact: Path | None = None) -> int:
    if not os.environ.get("DEEPSEEK_MODEL", "").strip():
        # Fail closed: the adapter default and the provider's current model
        # identifiers have both changed, so assume nothing on a billed run.
        print("E2E MODEL FAIL | reason=deepseek_model_required")
        return 1
    if not os.environ.get("DEEPSEEK_API_KEY"):
        # Fail closed: without a key the run must not touch the model at all.
        print("E2E SOURCED MODEL FAIL | reason=missing_api_key")
        return 1
    try:
        model_name, model_budget = authorized_model()
    except ValueError:
        print("E2E SOURCED MODEL FAIL | reason=model_configuration_invalid")
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
        # The evidence sent to the model must already verify; a drifted doc id,
        # version or fabricated quote would otherwise reach the model unchecked.
        # Fail closed without trusting the shape: a missing or short check list
        # is as disqualifying as a failed one.
        checks = list(result.get("citation_checks") or [])  # type: ignore[union-attr]
        cited = sorted(str(c.get("chunk_id")) for c in result["citations"])  # type: ignore[union-attr]
        checked = sorted(str(c.get("chunk_id")) for c in checks)
        unverified = [
            check
            for check in checks
            if not isinstance(check, dict) or check.get("verdict") != "verified"
        ]
        if unverified or checked != cited:
            print("E2E SOURCED MODEL FAIL | reason=unverifiable_citation")
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
            model=model_name,
            max_calls=int(os.environ.get("DEEPSEEK_MAX_CALLS", "1")),
            max_tokens=int(os.environ.get("DEEPSEEK_MAX_TOKENS", "2000")),
            max_prompt_tokens=int(os.environ.get("DEEPSEEK_MAX_PROMPT_TOKENS", "24000")),
            budget=model_budget,
            budget_purpose="prior_source" if getattr(model_budget, "_identity", None) is not None else "ordinary",
            transport=acceptance_transport(model_budget, "prior_source"),
            thinking_type="disabled",
        ) as model:
            try:
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
            finally:
                # The provider bills the call before the protocol is parsed, so the
                # usage record has to be emitted even when decide() raises.
                _report_usage(model)
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
        if artifact is not None:
            from e2e_citation_support_model import _publish, _read_published_artifact
            raw = json.dumps({
                "schema": "ulticode-prior-five-raw-record-v1", "name": "本人提交检索分析",
                "attempt_ids": [entry["attempt_id"] for entry in model.metering],
                "provider_exchanges": getattr(model._transport, "exchanges", []),
                "records": {"owner_verified": True, "facts": result["facts"],
                            "citations": result["citations"], "citation_checks": checks,
                            "retrieval_calls": [{"query": QUESTION, "results": result["citations"]}],
                            "model_answer": decision.text},
            }, ensure_ascii=True)
            _publish(artifact, raw)
            _read_published_artifact(artifact, raw)

    print(
        f"E2E SOURCED MODEL PASS | model={model_label(model_name)} "
        "| corpus=agent-authored-synthetic "
        "| input=validated-user-projection | answer=withheld"
    )
    return 0


async def main() -> int:
    target = os.environ.get("ULTICODE_SOURCE_ANALYSIS_ARTIFACT")
    if not target:
        return await _main()
    from e2e_citation_support_model import _claim_verdict_file, _release_unfinished_claim
    path = Path(target)
    lock = _claim_verdict_file(path)
    try:
        return await _main(path)
    finally:
        _release_unfinished_claim(lock)


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except Exception as exc:
        print(f"E2E SOURCED MODEL FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
