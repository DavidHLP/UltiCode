"""Real-model answer-level evaluation over the U02 development split.

Fills the three columns `keyword_evaluation` leaves as `DEFERRED` /
`not_measured`: for each development case it generates an answer from the
retrieved fragments and then judges that answer, recording citation support,
task completion and the observed behaviour.

Scope is enforced by `answer_evaluation` itself: `holdout` and `holdout2` are
refused, so this entry can never consume a sealed set. Retrieval stays the pinned
keyword path — this adds an answer layer on top of it, not a second retriever.

Opt in with `ULTICODE_ANSWER_EVAL=1`. Like the other model entries the key is
read from the environment and never logged, and the run stops at
`deepseek_api_key_required` before any call.
"""

from __future__ import annotations

import asyncio
import json
import math
import os
import secrets
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))
# One directory up as well, so this entry point can reuse the atomic-artifact
# pattern the citation entry point already implements instead of re-deriving the
# reserve / no-clobber / rename logic.
sys.path.insert(0, str(Path(__file__).parent))

from answer_evaluation import (
    AnswerEvaluationError,
    development_cases,
    evaluate_answer_cases,
    summarize,
)
from deepseek_model import (
    DeepseekModel,
    ModelBudgetExceeded,
    ModelProtocolError,
    model_label,
)
from e2e_citation_support_model import (
    _claim_verdict_file as _claim_artifact,
    _publish,
    _discard_artifacts,
    _assert_artifact_directory,
    _release_unfinished_claim,
)
from keyword_evaluation import load_cases

OPT_IN = "ULTICODE_ANSWER_EVAL"
DEFAULT_MAX_CALLS = 64
# Two passes per case, each with a bounded retry envelope. Keep the preflight
# requirement and the evaluator's actual retry limit tied to the same value.
ATTEMPTS_PER_PASS = 3
CALLS_PER_CASE = 2 * ATTEMPTS_PER_PASS


def _artifact_path() -> Path:
    """A run-scoped artifact under state, never the working tree."""
    override = os.environ.get("ULTICODE_ANSWER_EVAL_RESULT", "").strip()
    if override:
        return Path(override)
    configured = os.environ.get("XDG_STATE_HOME", "")
    if os.path.isabs(configured):
        state_home = Path(configured)
    else:
        home = Path.home()
        if not home.is_absolute():
            raise RuntimeError("HOME is not absolute and XDG_STATE_HOME is unset")
        state_home = home / ".local" / "state"
    return state_home / "ulticode" / f"answer-eval-{secrets.token_hex(4)}.json"


def _int(name: str, default: int) -> int:
    raw = os.environ.get(name, str(default)).strip()
    try:
        value = int(raw)
    except ValueError:
        raise AnswerEvaluationError(f"{name} must be an integer") from None
    if value < 1:
        raise AnswerEvaluationError(f"{name} must be at least 1")
    return value


def _float(name: str, default: float) -> float:
    raw = os.environ.get(name, str(default)).strip()
    try:
        value = float(raw)
    except ValueError:
        raise AnswerEvaluationError(f"{name} must be a number") from None
    if not math.isfinite(value) or value <= 0:
        raise AnswerEvaluationError(f"{name} must be finite and positive")
    return value


def _corpus_identity(manifest_sha256: str, documents: tuple[object, ...]) -> dict[str, object]:
    """Which corpus produced the retrieved fragments, by digest and version.

    A row of judgements is meaningless without pinning the material it judged, so
    the manifest digest and each declared version travel with the artifact instead
    of being inferable only by rerunning against whatever the corpus is that day.

    Both are passed in from the pre-call snapshot: reading them here, at artifact
    time, would describe whatever the files hold *then*, not what was judged.
    """
    from corpus_manifest import MANIFEST_PATH

    return {
        "manifest": MANIFEST_PATH.name,
        "manifest_sha256": manifest_sha256,
        "documents": [
            {"doc_id": doc.doc_id, "version": doc.version} for doc in documents
        ],
    }


def _cases_identity(case_sha256: str) -> dict[str, object]:
    """Which case file the development split was read from, by digest."""
    from keyword_evaluation import _CASES_PATH

    return {
        "file": _CASES_PATH.name,
        "sha256": case_sha256,
        "split": "development",
    }


async def main() -> int:
    if os.environ.get(OPT_IN) != "1":
        print("SKIP reason=opt_in_not_set")
        return 0

    import hashlib

    from corpus_manifest import MANIFEST_PATH, parse_manifest_text
    from keyword_evaluation import _CASES_PATH
    from retrieval import load_sample_corpus

    # Snapshot the inputs once, before the first billed call: every case retrieves
    # from this corpus, and the artifact identifies it, so a file replaced mid-run
    # cannot make the judgements describe material the digest does not.
    #
    # The manifest is read as bytes **once**; the same bytes are parsed for
    # validation and hashed for the artifact. Reading it as text for one and bytes
    # for the other would let a mid-run replacement, or text-mode newline
    # translation, bind the recorded digest to different bytes than were validated.
    manifest_bytes = MANIFEST_PATH.read_bytes()
    manifest_entries = parse_manifest_text(manifest_bytes.decode("utf-8"))
    manifest_sha256 = hashlib.sha256(manifest_bytes).hexdigest()
    documents = load_sample_corpus(manifest=manifest_entries)
    case_bytes = _CASES_PATH.read_bytes()
    cases = development_cases(
        load_cases(text=case_bytes.decode("utf-8"), documents=documents)
    )
    case_sha256 = hashlib.sha256(case_bytes).hexdigest()
    if not cases:
        print("FAIL reason=no_development_cases")
        return 1

    # Configured ceiling, not the row count: the adapter owns the guard, and a
    # budget below the plan would bill a partial run before refusing the rest.
    max_calls = _int("DEEPSEEK_MAX_CALLS", DEFAULT_MAX_CALLS)
    if max_calls < len(cases) * CALLS_PER_CASE:
        print(
            f"FAIL reason=call_budget_below_plan cases={len(cases)} "
            f"required={len(cases) * CALLS_PER_CASE} max_calls={max_calls}"
        )
        return 1

    model_name = os.environ.get("DEEPSEEK_MODEL", "").strip()
    if not model_name:
        print("FAIL reason=deepseek_model_required")
        return 1
    if not os.environ.get("DEEPSEEK_API_KEY", "").strip():
        print("FAIL reason=deepseek_api_key_required")
        return 1

    artifact = _artifact_path()
    try:
        # Reserved before any billed call: an existing artifact must not be
        # clobbered, and an unusable destination is a failed run, not something to
        # discover after paying for a whole batch of judgements.
        lock = _claim_artifact(artifact)
    except RuntimeError as error:
        print(f"FAIL reason=answer_artifact_unusable detail={error}")
        return 1

    started = len(cases)
    try:
        try:
            async with DeepseekModel(
                os.environ["DEEPSEEK_API_KEY"],
                tool_specs={},
                model=model_name,
                max_calls=max_calls,
                # 40 sequential billed calls over a reasoning model: the adapter's 30s
                # default is per request, and one stall aborts the whole batch.
                timeout=_float("DEEPSEEK_TIMEOUT", 120.0),
                max_tokens=_int("DEEPSEEK_MAX_TOKENS", 4000),
                max_prompt_tokens=_int("DEEPSEEK_MAX_PROMPT_TOKENS", 24000),
            ) as model:
                try:
                    rows = await evaluate_answer_cases(
                        cases, model=model, documents=documents, attempts=ATTEMPTS_PER_PASS
                    )
                finally:
                    # Every sent request remains billed even if a later pass aborts.
                    totals = [e.get("total_tokens") for e in model.usage if isinstance(e, dict)]
                    known = [v for v in totals if isinstance(v, int)]
                    printed = "unknown" if len(known) != len(totals) else str(sum(known))
                    print(
                        f"ANSWER EVAL USAGE | cases={started} "
                        f"calls={len(model.usage)} total_tokens={printed}"
                    )
        except ModelBudgetExceeded as error:
            print(f"FAIL reason=model_budget_exceeded detail={error}")
            return 1
        except ModelProtocolError as error:
            # A truncated answer is a cap problem, not a contract problem: the fix is
            # DEEPSEEK_MAX_TOKENS, and reporting it as a bare protocol failure sends
            # the next reader looking at the JSON shape instead.
            detail = str(error)
            hint = " raise DEEPSEEK_MAX_TOKENS" if "finish_reason=length" in detail else ""
            print(f"FAIL reason=model_protocol detail={detail}{hint}")
            return 1
        except AnswerEvaluationError as error:
            print(f"FAIL reason=protocol detail={error}")
            return 1

        summary = summarize(rows)
        if summary["cases"] != started:
            # A short run would otherwise read as a completed evaluation.
            print(
                f"FAIL reason=incomplete cases={summary['cases']} planned={started}"
            )
            return 1

        owned = {}
        try:
            # Publish complete bytes without replacing a late-arriving destination.
            # Publication and cleanup stay in the directory reserved before billing.
            owned[artifact] = _publish(
                artifact,
                json.dumps(
                    {
                        "scope": "development_only",
                        "sealed_splits": ["holdout", "holdout2"],
                        "model": model_label(model_name),
                        "judge": "model",
                        "human_review": "not_performed",
                        # The classification is the judge's, from the returned text; the
                        # raw answers live in `rows[].answer_text`, never on stdout.
                        "behavior_source": "judge_classified_from_answer_text",
                        "corpus": _corpus_identity(manifest_sha256, documents),
                        "cases": _cases_identity(case_sha256),
                        "summary": summary,
                        "rows": [row.__dict__ for row in rows],
                    },
                    ensure_ascii=False,
                    indent=2,
                )
                + "\n",
            )
            _assert_artifact_directory(artifact)
        except OSError as error:
            _discard_artifacts(owned)
            print(
                f"FAIL reason=answer_artifact_write_failed "
                f"detail={artifact.name} ({type(error).__name__})"
            )
            return 1
    finally:
        # Every exit path — the named protocol failures, a write failure, or an
        # arbitrary runtime exception main_sync sanitizes — drops the reservation
        # now, not at process exit, so a later run can take the same destination.
        _release_unfinished_claim(lock)

    print(
        f"OK answer_eval scope=development_only "
        f"supported={summary['supported']} unsupported={summary['unsupported']} "
        f"not_applicable={summary['not_applicable']} "
        f"completed={summary['completed']} incomplete={summary['incomplete']} "
        f"behavior_match={summary['behavior_match']} deferred={summary['deferred']} "
        f"sealed=holdout,holdout2"
    )
    return 0


def main_sync() -> int:
    try:
        return asyncio.run(main())
    except Exception as error:  # noqa: BLE001 - fixed status label only
        print(f"FAIL error={type(error).__name__}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main_sync())
