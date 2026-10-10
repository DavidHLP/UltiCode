"""DAV-58 real-model six-category boundary evaluation entry point.

Opt-in only. It runs the *real* DeepSeek adapter through the real tool loop against
the client's session, over the synthetic boundary corpus, and records one row per
case: the tool trajectory, the final answer, the citation gate (exists / supports /
derivable) and whether the expected boundary behaviour held.

Scope label: REAL model + authorised synthetic corpus. It prints only fixed status
labels and counts — never credentials, cookies, model raw output, or private DTOs.
The artifact under the state directory carries the synthetic material and the
answers; stdout does not.

    ULTICODE_BOUNDARY_EVAL=1 DEEPSEEK_MODEL=<model> uv run python e2e_boundary_evaluation.py
"""

from __future__ import annotations

import asyncio
import argparse
import hashlib
import json
import math
import os
import secrets
import subprocess
import sys
import sqlite3
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))
# One directory up too, so this entry reuses the atomic-artifact pattern instead
# of re-deriving the reserve / no-clobber / rename logic.
sys.path.insert(0, str(Path(__file__).parent))

from boundary_evaluation import (
    BOUNDARY_ANSWER_CONTRACT,
    BOUNDARY_CASES_PATH,
    BOUNDARY_MANIFEST_PATH,
    JUDGE_CONTRACT,
    SEARCH_EVIDENCE_SPEC,
    BoundaryEvaluationError,
    SyntheticBoundaryClient,
    _combined_metering,
    _usd_from_micro,
    evaluate_boundary_cases,
    forged_citation_probe,
    load_boundary_cases,
    load_boundary_corpus,
    summarize_boundary,
    unsupported_claim_probe,
)
from corpus_manifest import parse_manifest_text
from deepseek_model import DeepseekModel, ModelBudgetExceeded, ModelProtocolError, model_label
from model_budget import BudgetLimitExceeded, authorized_model
from authorized_budget_period import POLICY, POLICY_ID, PeriodIdentity, policy_for
from e2e_citation_support_model import (
    _assert_artifact_directory,
    _claim_verdict_file as _claim_artifact,
    _discard_artifacts,
    _publish,
    _release_unfinished_claim,
)
from e2e_citation_support_model import _path_label
from retrieval import MAX_RESULTS
from ulticode_tools import TOOL_SPECS

OPT_IN = "ULTICODE_BOUNDARY_EVAL"
DEFAULT_MAX_CALLS = 64
DEFAULT_MAX_ROUNDS = 4
#: Two passes per case are a floor: the loop may spend several rounds before it
#: answers, and every emitted citation costs a judge call.
CALLS_PER_CASE_FLOOR = DEFAULT_MAX_ROUNDS + MAX_RESULTS


def _artifact_path() -> Path:
    override = os.environ.get("ULTICODE_BOUNDARY_EVAL_RESULT", "").strip()
    if override:
        return Path(override)
    configured = os.environ.get("XDG_STATE_HOME", "")
    if os.path.isabs(configured):
        state_home = Path(configured)
    else:
        home = Path.home()
        if not home.is_absolute():
            raise BoundaryEvaluationError("HOME is not absolute and XDG_STATE_HOME is unset")
        state_home = home / ".local" / "state"
    return state_home / "ulticode" / f"boundary-eval-{secrets.token_hex(4)}.json"


def _repository_provenance() -> dict[str, object]:
    agent_root = Path(__file__).resolve().parent
    repo_root = agent_root.parents[1]
    try:
        revision = subprocess.run(
            ["git", "rev-parse", "--verify", "HEAD"],
            cwd=repo_root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        status = subprocess.run(
            ["git", "status", "--porcelain", "--untracked-files=normal"],
            cwd=repo_root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        raise BoundaryEvaluationError("could not identify the evaluated checkout") from None
    if len(revision) != 40 or status:
        raise BoundaryEvaluationError("boundary evaluation requires a clean committed checkout")

    source_files = (
        "e2e_boundary_evaluation.py",
        "src/agent_loop.py",
        "src/boundary_evaluation.py",
        "src/citation_integrity.py",
        "src/citation_review.py",
        "src/corpus_manifest.py",
        "src/deepseek_model.py",
        "src/model_budget.py",
        "src/authorized_budget_period.py",
        "src/retrieval.py",
        "src/ulticode_tools.py",
    )
    try:
        source_hashes = {
            relative: hashlib.sha256((agent_root / relative).read_bytes()).hexdigest()
            for relative in source_files
        }
    except OSError:
        raise BoundaryEvaluationError("could not hash evaluated source files") from None
    return {
        "git_sha": revision,
        "clean": True,
        "source_sha256": source_hashes,
    }


def _digest_json(value: object) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def _publish_judge_receipt(
    raw: dict, artifact: Path, owned: dict[Path, tuple[int, int]], receipts: list[dict]
) -> dict[str, str]:
    index = raw.get("metering_receipt_index")
    if type(index) is not int or not 0 <= index < len(receipts):
        raise ValueError("judge_metering_index_invalid")
    meter = receipts[index]
    original = {key: value for key, value in meter.items() if key not in {"usage_known", "settled"}}
    if (raw.get("attempt_id") != meter.get("attempt_id")
            or raw.get("metering_receipt_sha256") != _digest_json(original)):
        raise ValueError("judge_metering_binding_invalid")
    raw["metering_receipt_sha256"] = _digest_json(meter)
    path = artifact.with_name(f"{artifact.name}.judge-{secrets.token_hex(6)}.json")
    text = (
        json.dumps(raw, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
    )
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    owned[path] = _publish(path, text)
    _assert_artifact_directory(path)
    return {"path": path.name, "sha256": digest}


def _publish_judge_evidence(
    records: list[dict[str, object]],
    probes: list[dict[str, object]],
    artifact: Path,
    owned: dict[Path, tuple[int, int]],
    loop_receipt_count: int,
    receipts: list[dict],
) -> None:
    for record in records:
        for item in record.get("citation_judgements", []):
            raw = item.pop("_raw_receipt", None)
            if isinstance(raw, dict):
                index = raw.get("metering_receipt_index")
                if isinstance(index, int):
                    raw["metering_receipt_index"] = loop_receipt_count + index
                item["receipt"] = _publish_judge_receipt(raw, artifact, owned, receipts)
    for probe in probes:
        raw = probe.pop("_judge_receipt_raw", None)
        if isinstance(raw, dict):
            index = raw.get("metering_receipt_index")
            if isinstance(index, int):
                raw["metering_receipt_index"] = loop_receipt_count + index
            probe["judge_receipt"] = _publish_judge_receipt(raw, artifact, owned, receipts)


def _int(name: str, default: int) -> int:
    raw = os.environ.get(name, str(default)).strip()
    try:
        value = int(raw)
    except ValueError:
        raise BoundaryEvaluationError(f"{name} must be an integer") from None
    if value < 1:
        raise BoundaryEvaluationError(f"{name} must be at least 1")
    return value


def _float(name: str, default: float) -> float:
    raw = os.environ.get(name, str(default)).strip()
    try:
        value = float(raw)
    except ValueError:
        raise BoundaryEvaluationError(f"{name} must be a number") from None
    if not math.isfinite(value) or value <= 0:
        raise BoundaryEvaluationError(f"{name} must be finite and positive")
    return value


def _period_receipts(entries: list[dict], snapshot: dict) -> list[dict]:
    reconciled = snapshot.get("unknown_usage_attempts") == 0 and snapshot.get("unsettled_attempts") == 0
    receipts = []
    for entry in entries:
        amount = entry.get("actual_micro_usd")
        known = type(amount) is int and amount >= 0
        receipts.append({**entry, "usage_known": known, "settled": known and reconciled})
    return receipts


async def main(expected: PeriodIdentity | None = None) -> int:
    if os.environ.get(OPT_IN) != "1":
        print("SKIP reason=opt_in_not_set")
        return 0

    manifest_bytes = BOUNDARY_MANIFEST_PATH.read_bytes()
    entries = parse_manifest_text(manifest_bytes.decode("utf-8"))
    manifest_digest = hashlib.sha256(manifest_bytes).hexdigest()
    documents = load_boundary_corpus(manifest=entries)
    case_bytes = BOUNDARY_CASES_PATH.read_bytes()
    cases = load_boundary_cases(text=case_bytes.decode("utf-8"), documents=documents)
    case_digest = hashlib.sha256(case_bytes).hexdigest()

    max_calls = _int("DEEPSEEK_MAX_CALLS", DEFAULT_MAX_CALLS)
    if max_calls < len(cases) * CALLS_PER_CASE_FLOOR:
        print(
            f"FAIL reason=call_budget_below_plan cases={len(cases)} "
            f"required={len(cases) * CALLS_PER_CASE_FLOOR} max_calls={max_calls}"
        )
        return 1

    if expected is None:
        print("FAIL reason=period_identity_required")
        return 1
    POLICY = policy_for(expected.policy_id)
    max_tokens = _int("DEEPSEEK_MAX_TOKENS", 2000)
    prompt_cap = POLICY["lanes"]["dav58_loop"].get("prompt_token_cap", POLICY["prompt_token_cap"])
    max_prompt_tokens = _int("DEEPSEEK_MAX_PROMPT_TOKENS", prompt_cap)
    max_rounds = _int("DEEPSEEK_MAX_ROUNDS", DEFAULT_MAX_ROUNDS)
    if (max_tokens > POLICY["lanes"]["dav58_loop"]["completion_token_cap"]
        or max_prompt_tokens > prompt_cap
        or max_rounds > POLICY["lanes"]["dav58_loop"]["rounds"]):
        print("FAIL reason=authorized_caps_exceeded")
        return 1
    try:
        model_name, budget = authorized_model(expected)
        budget_before = budget.snapshot()
    except (ValueError, BudgetLimitExceeded, sqlite3.Error, OSError) as error:
        print(f"FAIL reason=model_not_authorized detail={type(error).__name__}")
        return 1
    judge_max_calls = min(max_calls, _int("DEEPSEEK_JUDGE_MAX_CALLS", POLICY["lanes"]["dav58_judge"]["attempts"]), POLICY["lanes"]["dav58_judge"]["attempts"])
    timeout = _float("DEEPSEEK_TIMEOUT", 120.0)
    tool_specs = {**TOOL_SPECS, "search_evidence": SEARCH_EVIDENCE_SPEC}
    request_config = {
        "model": model_name,
        "thinking": "disabled",
        "temperature": 0,
        "max_calls_per_adapter": {"dav58_loop": min(max_calls, POLICY["lanes"]["dav58_loop"]["attempts"]), "dav58_judge": judge_max_calls},
        "period": asdict(expected),
        "continuation_audit": budget.continuation(),
        "purpose_limits": {name: dict(POLICY["lanes"][name]) for name in ("dav58_loop", "dav58_judge")},
        "max_prompt_tokens": max_prompt_tokens,
        "max_completion_tokens": max_tokens,
        "max_rounds": max_rounds,
        "timeout_seconds": timeout,
        "tool_specs_sha256": _digest_json(tool_specs),
    }

    try:
        repository = _repository_provenance()
    except BoundaryEvaluationError as error:
        print(f"FAIL reason=checkout_provenance detail={type(error).__name__}")
        return 1
    run_started_at_utc = datetime.now(timezone.utc).isoformat()

    artifact = _artifact_path()
    try:
        lock = _claim_artifact(artifact)
    except RuntimeError as error:
        print(f"FAIL reason=boundary_artifact_unusable detail={error}")
        return 1

    try:
        try:
            # The data plane is guaranteed synthetic: an in-memory client backs the
            # read-only tools so no real stack credential or user data can be sent
            # to the model or persisted to the artifact.
            client = SyntheticBoundaryClient()
            async with DeepseekModel(
                os.environ["DEEPSEEK_API_KEY"],
                tool_specs=tool_specs,
                model=model_name,
                max_calls=min(max_calls, POLICY["lanes"]["dav58_loop"]["attempts"]),
                timeout=timeout,
                max_tokens=max_tokens,
                max_prompt_tokens=max_prompt_tokens,
                budget=budget,
                budget_purpose="dav58_loop",
                thinking_type="disabled",
            ) as model:
                # The judging pass runs on a tool-less adapter: an independent
                # judge must not be steered by the loop's tool contract.
                async with DeepseekModel(
                    os.environ["DEEPSEEK_API_KEY"],
                    tool_specs={},
                    model=model_name,
                    max_calls=judge_max_calls,
                    timeout=timeout,
                    max_tokens=max_tokens,
                    max_prompt_tokens=max_prompt_tokens,
                    budget=budget,
                    budget_purpose="dav58_judge",
                    thinking_type="disabled",
                ) as judge:
                    prompt_schema = {
                        "answer_contract_sha256": hashlib.sha256(
                            BOUNDARY_ANSWER_CONTRACT.encode("utf-8")
                        ).hexdigest(),
                        "judge_contract_sha256": hashlib.sha256(
                            JUDGE_CONTRACT.encode("utf-8")
                        ).hexdigest(),
                        "answer_parser_source_sha256": repository["source_sha256"][
                            "src/boundary_evaluation.py"
                        ],
                        "answer_system_prompt_sha256": hashlib.sha256(
                            model._system.encode("utf-8")
                        ).hexdigest(),
                        "judge_system_prompt_sha256": hashlib.sha256(
                            judge._system.encode("utf-8")
                        ).hexdigest(),
                        "tool_schema_sha256": request_config["tool_specs_sha256"],
                    }
                    try:
                        records = await evaluate_boundary_cases(
                            cases,
                            model=model,
                            judge_model=judge,
                            client=client,
                            documents=documents,
                            manifest=entries,
                            max_rounds=max_rounds,
                            total_timeout=timeout,
                            model_version=model_label(model_name),
                        )
                        probes = [forged_citation_probe(documents)]
                        budget_stopped = any(
                            record.get("failure_handling", {}).get("error") == "ModelBudgetExceeded"
                            for record in records
                        )
                        try:
                            if budget_stopped:
                                probes.append({
                                    "probe": "unsupported_composite_claim",
                                    "gate_rejected": False,
                                    "error": "not_run_budget",
                                    "parse_error": "not_run_budget",
                                    "actualcitation": None,
                                    "citations": [],
                                    "malformed_citations": [],
                                    "integritychecks": [],
                                    "citation_checks": [],
                                    "worksheet": None,
                                    "judge_receipt": None,
                                })
                            else:
                                probes.append(await unsupported_claim_probe(judge, documents))
                        except Exception as error:  # noqa: BLE001 - recorded, not lost
                            probes.append({
                                "probe": "unsupported_composite_claim",
                                "inject": "program_level_control",
                                "gate_rejected": False,
                                "error": type(error).__name__,
                                "parse_error": type(error).__name__,
                                "actualcitation": None,
                                "citations": [],
                                "malformed_citations": [],
                                "integritychecks": [],
                                "citation_checks": [],
                                "worksheet": None,
                                "judge_receipt": None,
                            })
                    finally:
                        usage = list(model.usage) + list(judge.usage)
                        totals = [
                            entry.get("total_tokens")
                            for entry in usage
                            if isinstance(entry, dict)
                        ]
                        known = [
                            value for value in totals
                            if isinstance(value, int) and not isinstance(value, bool)
                        ]
                        run_total_tokens = sum(known) if len(known) == len(totals) else None
                        run_metering = _combined_metering(model, judge, 0, 0)
                        loop_receipt_count = len(model.metering)
                        receipts = list(model.metering) + list(judge.metering)
                        run_finished_at_utc = datetime.now(timezone.utc).isoformat()
                        printed = "unknown" if run_total_tokens is None else str(run_total_tokens)
                        print(
                            f"BOUNDARY EVAL USAGE | cases={len(cases)} "
                            f"calls={len(usage)} total_tokens={printed}"
                        )
        except ModelBudgetExceeded as error:
            print(f"FAIL reason=model_budget_exceeded detail={error}")
            return 1
        except ModelProtocolError as error:
            print(f"FAIL reason=model_protocol detail={error}")
            return 1
        except (BoundaryEvaluationError, KeyError) as error:
            print(f"FAIL reason=config detail={type(error).__name__}")
            return 1

        if len(records) != len(cases):
            print(f"FAIL reason=incomplete cases={len(records)} planned={len(cases)}")
            return 1

        summary = summarize_boundary(records)
        budget_snapshot = budget.snapshot()
        receipts = _period_receipts(receipts, budget_snapshot)
        print(
            f"BOUNDARY EVAL BUDGET | remaining_attempts={budget_snapshot['remaining_attempts']} "
            f"remaining_micro_usd={budget_snapshot['remaining_micro_usd']}"
        )
        owned: dict[Path, tuple[int, int]] = {}
        try:
            _publish_judge_evidence(
                list(records), probes, artifact, owned, loop_receipt_count, receipts
            )
            owned[artifact] = _publish(
                artifact,
                json.dumps(
                    {
                        "run": {
                            "started_at_utc": run_started_at_utc,
                            "finished_at_utc": run_finished_at_utc,
                            "repository": repository,
                            "target": {
                                "kind": "synthetic_in_memory",
                                "host": None,
                                "service_sha": None,
                            },
                            "provider": {
                                "endpoint_host": "api.deepseek.com",
                                # The alias the run asked for, kept separate from the
                                # sanitized response identities the provider actually
                                # returned (an alias can drift).
                                "requested_model": model_label(model_name),
                                "observed_response_models": sorted(
                                    {
                                        label
                                        for record in records
                                        for label in (
                                            list(record.get("loop_response_models") or [])
                                            + list(record.get("judge_response_models") or [])
                                        )
                                        if isinstance(label, str) and label
                                    }
                                ),
                            },
                            "configuration": request_config,
                            "configuration_sha256": _digest_json(request_config),
                            "prompt_schema": prompt_schema,
                            "usage": {
                                "calls": len(usage),
                                "total_tokens": run_total_tokens,
                                "metering": run_metering,
                                "actual_usd": _usd_from_micro(
                                    run_metering["actual_micro_usd"]
                                ),
                            },
                        },
                        "scope": "synthetic_nonproduction",
                        "model": model_label(model_name),
                        "thinking": "disabled",
                        "judge": "model",
                        "human_review": "not_performed",
                        "budget": budget_snapshot,
                        "authorized_period": {"identity": asdict(expected),
                                              "purposes": ["dav58_loop", "dav58_judge"],
                                              "before": budget_before, "after": budget_snapshot,
                                              "receipts": receipts},
                        "corpus": {
                            "manifest": BOUNDARY_MANIFEST_PATH.name,
                            "manifest_sha256": manifest_digest,
                            "documents": [
                                {"doc_id": d.doc_id, "version": d.version} for d in documents
                            ],
                        },
                        "cases": {
                            "file": BOUNDARY_CASES_PATH.name,
                            "sha256": case_digest,
                        },
                        "summary": summary,
                        "rows": list(records),
                        "probes": probes,
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
                f"FAIL reason=boundary_artifact_write_failed "
                f"detail={_path_label(artifact)} ({type(error).__name__})"
            )
            return 1

        probes_ok = all(bool(probe["gate_rejected"]) for probe in probes)
        counts = (
            f"cases={summary['cases']} behavior_met={summary['behavior_met']} "
            f"behavior_failed={summary['behavior_failed']} errors={summary['errors']} "
            f"exists_failed={summary['citation_exists']['failed']} "
            f"unsupported={summary['citation_supports']['unsupported']} "
            f"not_derivable={summary['citation_derivable']['not_derivable']} "
            f"probes_ok={'yes' if probes_ok else 'no'} "
            f"artifact={_path_label(artifact)}"
        )
        if budget_stopped:
            print(f"FAIL reason=model_budget_exceeded {counts}")
            return 1
        if not probes_ok:
            # A negative control that the gate failed to reject means the gate
            # itself is broken; every judgement above is then untrustworthy.
            print(f"FAIL reason=negative_control_failed {counts}")
            return 1
        if summary["errors"]:
            # A case that errored or never ran leaves the six-case contract unmet,
            # even though the partial rows are published.
            print(f"FAIL reason=boundary_incomplete {counts}")
            return 1
        if summary["behavior_failed"] or summary["citation_exists"]["failed"]:
            print(f"FAIL reason=boundary_gate_failed {counts}")
            return 1
        if summary["citation_supports"]["unsupported"] or summary["citation_supports"][
            "failed"
        ]:
            # Fail closed: an emitted citation the judge did not support, or a
            # malformed answer/citation, is not a pass with a low score. The
            # negative stays in the artifact.
            print(f"FAIL reason=citation_gate_failed {counts}")
            return 1
        if summary["citation_derivable"]["not_derivable"] or summary["citation_derivable"][
            "failed"
        ]:
            print(f"FAIL reason=citation_gate_failed {counts}")
            return 1
        print(f"OK boundary_eval scope=synthetic_nonproduction {counts}")
        print(
            f"E2E BOUNDARY | model={model_label(model_name)} judge=model "
            "corpus=agent-authored-synthetic human_review=not_performed"
        )
        return 0
    finally:
        _release_unfinished_claim(lock)


def _parse_identity(argv: list[str]) -> PeriodIdentity:
    parser = argparse.ArgumentParser(description="DAV-58 explicit authorized period identity", allow_abbrev=False)
    parser.add_argument("--period-id", required=True)
    parser.add_argument("--period-identity", required=True)
    parser.add_argument("--config-sha256", required=True)
    parser.add_argument("--policy-id", default=POLICY_ID)
    fields = {"--period-id", "--period-identity", "--config-sha256"}
    if any(sum(value.split("=", 1)[0] == field for value in argv) != 1 for field in fields):
        parser.error("each identity field must be provided exactly once")
    args = parser.parse_args(argv)
    return PeriodIdentity(args.period_id, args.config_sha256, args.period_identity, args.policy_id)


def main_sync(argv: list[str] | None = None) -> int:
    try:
        expected = _parse_identity(sys.argv[1:] if argv is None else argv) if os.environ.get(OPT_IN) == "1" else None
        return asyncio.run(main(expected))
    except Exception as error:  # noqa: BLE001 - fixed status label only
        print(f"FAIL error={type(error).__name__}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main_sync())
