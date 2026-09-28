"""Real-model citation-support evaluation (DAV-45 acceptance #2, AI-executed).

The deterministic half — does the citation exist, is the quote verbatim from the
recorded source, do the provenance fields match — is `citation_integrity`. What it
cannot decide is whether the fragment *supports* the claim. The owner moved that
judgement to AI execution, so this entry point asks the model per worksheet row and
binds the answer through the same `load_verdicts` / `summarize` path the human
worksheet uses, which keeps the human review available as a later supplement.

Scope, stated in the output as well: the verdicts are **model-judged**, not
human-reviewed. `exists` is never the model's call — it comes from the
deterministic integrity gate — and only `supports` / `derivable` are asked.

The corpus is the agent-authored synthetic one, so the run is sample-only evidence;
authorized material is DAV-58.
"""

from __future__ import annotations

import asyncio
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from citation_review import build_worksheet, load_verdicts, summarize
from corpus_manifest import load_manifest
from deepseek_model import DeepseekModel, ModelProtocolError, model_label
from retrieval import MAX_RESULTS, keyword_search, load_sample_corpus
from sourced_analysis import analyze_submission, first_wrong_answer_submission
from ulticode_client import UlticodeClient
from ulticode_tools import build_tools

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")

#: The question whose retrieval covers the whole synthetic corpus. The review
#: question is per fragment — "does this quoted fragment support the claim?" — so
#: one claim is paired with every retrieved fragment rather than with one.
QUESTION = "submission status source citation record"

JUDGE_CONTRACT = (
    "You are checking citations, not answering the question. "
    "Given CLAIM and QUOTE, reply with ONE JSON object and nothing else: "
    '{"supports": <true if the quote supports the claim, else false>, '
    '"derivable": <true if the claim follows from SUBMISSION_FACTS alone, else false>}'
)


def _judgements(raw: str) -> tuple[bool, bool]:
    """The model's two booleans, or a protocol failure."""
    try:
        parsed = json.loads(raw)
    except ValueError:
        raise ModelProtocolError("citation judgement was not JSON") from None
    if not isinstance(parsed, dict):
        raise ModelProtocolError("citation judgement was not an object")
    supports = parsed.get("supports")
    derivable = parsed.get("derivable")
    if not isinstance(supports, bool) or not isinstance(derivable, bool):
        raise ModelProtocolError("citation judgement was not two booleans")
    return supports, derivable


def _verdict_file(rows: object) -> Path:
    return Path(os.environ.get("ULTICODE_CITATION_VERDICTS", "citation-verdicts.json"))


async def main() -> int:
    if os.environ.get("ULTICODE_CITATION_SUPPORT") != "1":
        print("SKIP reason=opt_in_not_set")
        return 0
    model_name = os.environ.get("DEEPSEEK_MODEL", "").strip()
    if not model_name:
        print("FAIL reason=deepseek_model_required")
        return 1
    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        print("FAIL reason=deepseek_api_key_required")
        return 1

    documents = load_sample_corpus()
    manifest = load_manifest()
    async with UlticodeClient(APP_BASE, AUTH_BASE) as client:
        await client.login(
            os.environ["ULTICODE_E2E_USERNAME"], os.environ["ULTICODE_E2E_PASSWORD"]
        )
        tools = build_tools(client)
        matching = await first_wrong_answer_submission(tools)
        if matching is None:
            print("FAIL reason=no_wrong_answer_submission")
            return 1
        analysis = analyze_submission(matching, QUESTION)

    hypotheses = analysis.get("hypotheses") or []
    if len(hypotheses) != 1:
        # The worksheet refuses to invent the claim link, so an analysis that does
        # not carry exactly one claim cannot be reviewed.
        print(f"FAIL reason=ambiguous_claim hypotheses={len(hypotheses)}")
        return 1
    # Every retrieved fragment, not only the ones the analysis kept: the review
    # question is per fragment. Each is checked by the deterministic integrity
    # gate before the model is asked anything.
    citations = [hit.as_model_dict() for hit in keyword_search(QUESTION, limit=MAX_RESULTS)]
    rows = list(
        build_worksheet(
            claim=str(hypotheses[0]),
            citations=citations,
            documents=documents,
            manifest=manifest,
        )
    )

    if len(rows) < 3:
        # The acceptance names three citations; saying so is better than reporting a
        # pass from one row.
        print(f"FAIL reason=insufficient_rows rows={len(rows)} required=3")
        return 1

    facts = json.dumps(matching, ensure_ascii=False, default=str)
    verdicts: list[dict[str, object]] = []
    calls = 0
    async with DeepseekModel(
        os.environ["DEEPSEEK_API_KEY"],
        tool_specs={},
        model=model_name,
        max_calls=len(rows),
        max_tokens=int(os.environ.get("DEEPSEEK_MAX_TOKENS", "2000")),
    ) as model:
        for item in rows:
            prompt = (
                f"{JUDGE_CONTRACT}\nCLAIM: {item.claim}\nQUOTE: {item.quote}\n"
                f"SUBMISSION_FACTS: {facts}"
            )
            decision = await model.decide([{"role": "user", "content": prompt}])
            calls += 1
            supports, derivable = _judgements(decision.text)
            verdicts.append(
                {
                    "chunk_id": item.chunk_id,
                    "review_id": item.review_id,
                    "claim": item.claim,
                    "quote": item.quote,
                    "verdicts": {
                        # Deterministic, never the model's call.
                        "exists": item.integrity_verdict == "verified",
                        "supports": supports,
                        "derivable": derivable,
                    },
                }
            )

    path = _verdict_file(rows)
    path.write_text(json.dumps(verdicts, ensure_ascii=False, indent=2), encoding="utf-8")
    # Read back through the same loader the human worksheet uses, so the verdicts
    # are bound to their rows before anything is summarised.
    loaded = load_verdicts(path, tuple(rows))
    summary = summarize(tuple(rows), loaded)

    print(
        f"OK citation_support model={model_label(model_name)} judge=model "
        f"rows={summary['reviewed']} calls={calls} "
        f"supports={summary['counts']['supports']} "
        f"not_supported={len(summary['not_supported'])} "
        f"integrity_unverified={len(summary['integrity_unverified'])} "
        f"gate_passed={summary['gate_passed']} verdicts={path}"
    )
    print(
        "E2E CITATION SUPPORT | reviewer=model | corpus=agent-authored-synthetic "
        "| human_review=not_performed"
    )
    if not summary["gate_passed"]:
        # A citation the model does not support, or one the integrity gate could
        # not verify, is a failed run — not a pass with a low score.
        print("FAIL reason=citation_gate_failed")
        return 1
    return 0


def main_sync() -> int:
    """Run :func:`main` in a fresh event loop, mapping protocol failures to exit 1."""
    try:
        return asyncio.run(main())
    except ModelProtocolError as exc:
        print(f"E2E CITATION SUPPORT FAIL error=ModelProtocolError detail={exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main_sync())
