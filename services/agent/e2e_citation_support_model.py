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
import atexit
import hashlib
import json
import os
import secrets
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from citation_review import build_worksheet, load_verdicts, summarize
from corpus_manifest import load_manifest
from deepseek_model import (
    DeepseekModel,
    ModelProtocolError,
    _reject_duplicate_keys,
    model_label,
)
from retrieval import load_sample_corpus
from sourced_analysis import analyze_submission, first_wrong_answer_submission
from ulticode_client import UlticodeClient
from ulticode_tools import build_tools

APP_BASE = os.environ.get("ULTICODE_APP_BASE", "http://localhost:9103")
AUTH_BASE = os.environ.get("ULTICODE_AUTH_BASE", "http://localhost:9101")

QUESTION = "Wrong Answer 状态说明了什么？"

#: The acceptance names three citations. On the agent-authored synthetic corpus the
#: analysis emits fewer, because a status-filtered retrieval only keeps fragments
#: whose text carries that status; authorized, richer material is DAV-58. The
#: threshold is configurable so that run can raise it without a code change.
DEFAULT_REQUIRED_ROWS = 3

#: The adapter's system message asks for `{"answer": ...}`, so the judgement is the
#: answer text rather than a competing envelope.
JUDGE_CONTRACT = (
    "You are checking citations, not answering the question. Given CLAIM, QUOTE and "
    "SUBMISSION_FACTS, make the answer a JSON object with exactly two boolean fields: "
    '{"supports": <does the quote support the claim>, '
    '"derivable": <does the claim follow from SUBMISSION_FACTS alone>}'
)


def _path_label(path: object) -> str:
    """A path safe to put on one evidence line: caller-supplied, so not verbatim."""
    return model_label(str(path))


def _judgements(raw: str) -> tuple[bool, bool]:
    """The model's two booleans, or a protocol failure.

    Duplicate keys are refused rather than last-write-wins: `{"supports": false,
    "supports": true}` must not read as support.
    """
    try:
        parsed = json.loads(raw, object_pairs_hook=_reject_duplicate_keys)
    except ValueError:
        raise ModelProtocolError("citation judgement was not JSON") from None
    if not isinstance(parsed, dict):
        raise ModelProtocolError("citation judgement was not an object")
    if set(parsed) != {"supports", "derivable"}:
        # The contract names exactly two fields; an extra explanation field would
        # otherwise ride along unread.
        raise ModelProtocolError("citation judgement had unexpected fields")
    supports = parsed.get("supports")
    derivable = parsed.get("derivable")
    if not isinstance(supports, bool) or not isinstance(derivable, bool):
        raise ModelProtocolError("citation judgement was not two booleans")
    return supports, derivable


def _verdict_file() -> Path:
    """Where this run's verdicts go.

    The default is run-scoped: two evaluations started from the documented working
    directory would otherwise write the same file, and the loser's verdicts would
    replace the winner's before either is read back.
    """
    override = os.environ.get("ULTICODE_CITATION_VERDICTS", "").strip()
    if override:
        return Path(override)
    # State, not the working directory: the documented invocation runs from
    # `services/agent`, and an artifact written there would dirty the checkout.
    configured = os.environ.get("XDG_STATE_HOME", "")
    if os.path.isabs(configured):
        state_home = Path(configured)
    else:
        home = Path.home()
        if not home.is_absolute():
            raise RuntimeError(
                "cannot locate a state directory: HOME is not absolute and "
                "XDG_STATE_HOME is unset"
            )
        state_home = home / ".local" / "state"
    return state_home / "ulticode" / f"citation-verdicts-{secrets.token_hex(4)}.json"


def _meta_path(path: Path) -> Path:
    return path.with_suffix(path.suffix + ".meta.json")


def _unlink_if_empty(candidate: Path) -> None:
    try:
        if candidate.exists() and candidate.stat().st_size == 0:
            candidate.unlink()
    except OSError:
        pass


def _discard_artifacts(path: Path) -> None:
    """Remove both artifacts, filled or not: they belong to this failed run."""
    for candidate in (path, _meta_path(path)):
        try:
            candidate.unlink()
        except OSError:
            pass


def _release_unfinished_claim(path: Path) -> None:
    """Remove placeholders this run claimed but never filled.

    An aborted run would otherwise leave empty files behind, and an explicit
    `ULTICODE_CITATION_VERDICTS` path would then refuse every later run.
    """
    for candidate in (path, _meta_path(path)):
        _unlink_if_empty(candidate)


def _claim_verdict_file(path: Path) -> None:
    """Take the destination before any billed call.

    A missing parent, a directory, or an already-claimed path is a failure of this
    run, and finding out after the model calls would waste them.
    """
    claimed: list[Path] = []
    for target in (path, _meta_path(path)):
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("x", encoding="utf-8"):
                pass
        except FileExistsError:
            # Roll back what this call claimed: a half-claimed pair would block
            # every later run that reuses an explicit path.
            for done in claimed:
                _unlink_if_empty(done)
            raise RuntimeError(
                f"verdict destination already exists: {_path_label(target)}"
            ) from None
        except OSError as error:
            for done in claimed:
                _unlink_if_empty(done)
            raise RuntimeError(
                f"verdict destination is not writable: {_path_label(target)} "
                f"({type(error).__name__})"
            ) from None
        claimed.append(target)
    atexit.register(_release_unfinished_claim, path)


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
    # Only the citations the analysis actually emitted are reviewed. Retrieved
    # fragments it did not cite are a different question (relevance of candidates)
    # and are deliberately not judged here.
    citations = analysis.get("citations") or []
    rows = list(
        build_worksheet(
            claim=str(hypotheses[0]),
            citations=citations,
            documents=documents,
            manifest=manifest,
        )
    )

    raw_required = os.environ.get("ULTICODE_CITATION_REQUIRED_ROWS", str(DEFAULT_REQUIRED_ROWS))
    try:
        required = int(raw_required)
    except ValueError:
        print(f"FAIL reason=citation_threshold_invalid raw={model_label(raw_required)}")
        return 1
    if required < DEFAULT_REQUIRED_ROWS:
        # The environment may raise the bar, never lower it below the acceptance's.
        print(
            f"FAIL reason=citation_threshold_below_minimum required={required} "
            f"minimum={DEFAULT_REQUIRED_ROWS}"
        )
        return 1
    if len(rows) < required:
        # Reported as a material gap, not as a pass from fewer rows: the corpus is
        # synthetic, and a status-filtered retrieval emits one citation per status.
        print(
            f"FAIL reason=insufficient_citations emitted={len(rows)} required={required} "
            f"corpus=agent-authored-synthetic"
        )
        return 1

    facts = json.dumps(matching, ensure_ascii=False, default=str)
    path = _verdict_file()
    try:
        # Claimed here, before any billed call: an unusable destination is a
        # failed run, not something to discover after paying for the judgements.
        _claim_verdict_file(path)
    except RuntimeError as error:
        print(f"FAIL reason=verdict_destination_unusable detail={error}")
        return 1

    unverified = [row.chunk_id for row in rows if row.integrity_verdict != "verified"]
    if unverified:
        # Judging an unverified citation would spend a call on a row that can never
        # pass the gate.
        print(f"FAIL reason=citation_integrity_failed rows={len(unverified)}")
        return 1

    verdicts: list[dict[str, object]] = []
    calls = 0
    try:
        max_calls = int(os.environ.get("DEEPSEEK_MAX_CALLS", "8"))
        max_tokens = int(os.environ.get("DEEPSEEK_MAX_TOKENS", "512"))
    except ValueError:
        print("FAIL reason=model_budget_invalid")
        return 1
    if max_calls < 1 or max_tokens < 1:
        print("FAIL reason=model_budget_invalid")
        return 1
    if max_calls < len(rows):
        # Otherwise some judgements are billed and then the adapter refuses the
        # rest, leaving a paid partial run.
        print(
            f"FAIL reason=call_budget_below_rows rows={len(rows)} max_calls={max_calls}"
        )
        return 1

    async with DeepseekModel(
        os.environ["DEEPSEEK_API_KEY"],
        tool_specs={},
        model=model_name,
        # The configured ceiling, not the row count: the adapter owns the guard.
        max_calls=max_calls,
        # A two-boolean judgement needs far less than a full analysis; 512 still
        # leaves room for a reasoning model's reasoning tokens, which are billed
        # inside the same budget. Raise it via the environment if a provider
        # truncates (`finish_reason=length`).
        max_tokens=max_tokens,
    ) as model:
        try:
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
        finally:
            # Every call is billed even when a later row fails to parse, so the
            # accounting is emitted on the failure path too.
            totals = [
                entry.get("total_tokens") for entry in model.usage if isinstance(entry, dict)
            ]
            known = [value for value in totals if isinstance(value, int)]
            printed = "unknown" if len(known) != len(totals) else str(sum(known))
            print(f"E2E CITATION SUPPORT USAGE | calls={len(model.usage)} total_tokens={printed}")

    # The full digest: a truncated one would weaken the binding between the
    # verdicts and the exact facts they were judged against.
    facts_digest = "sha256:" + hashlib.sha256(facts.encode("utf-8")).hexdigest()
    meta = {
        "judge": "model",
        "model": model_label(model_name),
        "human_review": "not_performed",
        "corpus": "agent-authored-synthetic",
        "submission_facts_digest": facts_digest,
        "required_rows": required,
    }
    try:
        # Metadata first, verdicts last: a reader keyed on the verdict file then
        # never sees verdicts whose sidecar is missing.
        _meta_path(path).write_text(
            json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        path.write_text(
            json.dumps(verdicts, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    except OSError as error:
        # Both artifacts go: a populated sidecar left next to a missing verdict
        # file would make every later run on this explicit path fail.
        _discard_artifacts(path)
        print(
            f"FAIL reason=verdict_write_failed detail={_path_label(path)} "
            f"({type(error).__name__})"
        )
        return 1
    # Read back through the same loader the human worksheet uses, so the verdicts
    # are bound to their rows before anything is summarised.
    loaded = load_verdicts(path, tuple(rows))
    summary = summarize(tuple(rows), loaded)

    counts = (
        f"model={model_label(model_name)} judge=model rows={summary['reviewed']} "
        f"calls={calls} supports={summary['counts']['supports']} "
        f"not_supported={len(summary['not_supported'])} "
        f"integrity_unverified={len(summary['integrity_unverified'])} "
        f"verdicts={_path_label(path)}"
    )
    if not summary["gate_passed"]:
        # A citation the model does not support is a failed run, not a pass with a
        # low score — so no line of this run may start with `OK`.
        print(f"FAIL reason=citation_gate_failed {counts}")
        return 1
    print(f"OK citation_support {counts}")
    print(
        "E2E CITATION SUPPORT | reviewer=model | corpus=agent-authored-synthetic "
        "| human_review=not_performed"
    )
    return 0


def main_sync() -> int:
    """Run :func:`main` in a fresh event loop, mapping failures to exit 1.

    A provider outage (non-200, transport error) must leave the same fixed,
    sanitized labels as the other entry points rather than a traceback; only the
    exception type is reported, never its message.
    """
    try:
        return asyncio.run(main())
    except ModelProtocolError as exc:
        print(
            "E2E CITATION SUPPORT FAIL error=ModelProtocolError "
            f"detail={model_label(str(exc))}"
        )
        return 1
    except Exception as exc:  # noqa: BLE001 - operational failures are reported, not raised
        print(f"E2E CITATION SUPPORT FAIL error={type(exc).__name__}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main_sync())
