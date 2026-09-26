"""Deterministic keyword retrieval evaluation for the U02 development slice.

Every case declares its required evidence plus the allowed and forbidden answer,
and produces one record: retrieval hit, citation traceability, retrieval outcome,
expected versus observed behaviour, fabrication risk, tool calls and elapsed time.

Retrieval-level facts are measured here. Answer-level judgements — whether a cited
fragment supports a conclusion, and whether the answer completes the task — are
**not**: those need the model or human pass tracked in DAV-58, so the record
carries them as ``DEFERRED`` instead of passing them by default. A traceable
source id is not evidence that the fragment supports the claim.
"""

from __future__ import annotations

import json
import time
from dataclasses import dataclass
from pathlib import Path

from retrieval import SourceHit, keyword_search

_CASES_PATH = Path(__file__).resolve().parents[1] / "data" / "keyword_cases.json"
#: One-shot confirmation set. It lives in its own versioned file so the routine
#: test suite cannot silently consume it: only an explicitly authorised
#: evaluation may load it, and its single run is recorded rather than repeated.
CONFIRMATION_CASES_PATH = Path(__file__).resolve().parents[1] / "data" / "holdout-v2.json"
EXPECTED_BEHAVIORS = frozenset({"cite", "no_evidence", "refuse"})
#: "holdout" was already observed during an exploratory run, so it is kept
#: for continuity; "holdout2" is the never-seen confirmation set.
SPLITS = ("development", "holdout", "holdout2")
#: Answer-level dimensions this deterministic slice cannot decide.
DEFERRED = "deferred"


@dataclass(frozen=True)
class KeywordCase:
    case_id: str
    split: str
    query: str
    required_evidence: tuple[str, ...]
    answerable: bool
    expected_behavior: str
    allowed_behavior: str
    forbidden_behavior: str


@dataclass(frozen=True)
class CaseRecord:
    """One evaluated case.

    ``tool_calls`` counts retrieval calls only. ``observed_behavior`` is
    ``not_measured`` whenever the behaviour is an answer-level property this
    deterministic slice cannot observe.
    """

    case_id: str
    split: str
    retrieval_hit: bool
    citation_traceable: bool
    retrieval_outcome: str
    citation_support: str
    answer_completion: str
    expected_behavior: str
    observed_behavior: str
    allowed_behavior: str
    forbidden_behavior: str
    fabrication_risk: bool
    unexpected_doc_ids: tuple[str, ...]
    tool_calls: int
    elapsed_us: int


def load_cases(path: Path | None = None) -> tuple[KeywordCase, ...]:
    case_path = path or _CASES_PATH
    raw_cases = json.loads(case_path.read_text(encoding="utf-8"))
    if not isinstance(raw_cases, list):
        raise ValueError("keyword cases must be a list")
    cases: list[KeywordCase] = []
    for raw_case in raw_cases:
        if not isinstance(raw_case, dict):
            raise ValueError("invalid keyword case")
        case_id = raw_case.get("case_id")
        split = raw_case.get("split")
        query = raw_case.get("query")
        required_evidence = raw_case.get("required_evidence")
        answerable = raw_case.get("answerable")
        expected_behavior = raw_case.get("expected_behavior")
        allowed_behavior = raw_case.get("allowed_behavior")
        forbidden_behavior = raw_case.get("forbidden_behavior")
        if (
            not isinstance(case_id, str)
            or not case_id
            or split not in SPLITS
            or not isinstance(query, str)
            or not query.strip()
            or not isinstance(required_evidence, list)
            or not all(isinstance(doc_id, str) and doc_id for doc_id in required_evidence)
            or not isinstance(answerable, bool)
            or expected_behavior not in EXPECTED_BEHAVIORS
            or not isinstance(allowed_behavior, str)
            or not allowed_behavior.strip()
            or not isinstance(forbidden_behavior, str)
            or not forbidden_behavior.strip()
        ):
            raise ValueError("invalid keyword case")
        if answerable is not (expected_behavior == "cite"):
            raise ValueError("answerable must agree with expected_behavior")
        if expected_behavior == "cite" and not required_evidence:
            raise ValueError("a citable case must name its required evidence")
        cases.append(
            KeywordCase(
                case_id=case_id,
                split=split,
                query=query,
                required_evidence=tuple(required_evidence),
                answerable=answerable,
                expected_behavior=expected_behavior,
                allowed_behavior=allowed_behavior,
                forbidden_behavior=forbidden_behavior,
            )
        )
    return tuple(cases)


def _citation_traceable(hits: tuple[SourceHit, ...]) -> bool:
    # No hit means no citation to trace, so it is not evidence of traceability.
    if not hits:
        return False
    return all(
        hit.doc_id
        and hit.version
        and hit.chunk_id
        and hit.source_path
        and hit.source_position
        and hit.access_scope
        for hit in hits
    )


def retrieval_outcome(required: set[str], actual: set[str]) -> str:
    if not required:
        return "false_positive" if actual else "matched"
    if not required <= actual:
        return "missed"
    return "matched" if actual == required else "extra_hits"


def evaluate_case_records(
    cases: tuple[KeywordCase, ...], *, limit: int
) -> tuple[CaseRecord, ...]:
    records: list[CaseRecord] = []
    for case in cases:
        started = time.perf_counter()
        hits = keyword_search(case.query, limit=limit)
        # Microseconds: the sample corpus answers in well under a millisecond, so
        # integer milliseconds truncated the whole latency dimension to zero.
        elapsed_us = int((time.perf_counter() - started) * 1_000_000)
        actual_doc_ids = {hit.doc_id for hit in hits}
        required_doc_ids = set(case.required_evidence)
        retrieval_hit = (
            required_doc_ids <= actual_doc_ids if required_doc_ids else not actual_doc_ids
        )
        if case.expected_behavior == "refuse":
            # No answer is produced here, so nothing about the answer can be
            # observed. Presenting the *expected* refusal as an observed one
            # would let a fabricated code line pass unnoticed; the risk is
            # recorded separately and the answer-level check stays deferred.
            observed_behavior = "not_measured"
        elif actual_doc_ids:
            observed_behavior = "answered_with_citation"
        else:
            observed_behavior = "no_evidence"
        records.append(
            CaseRecord(
                case_id=case.case_id,
                split=case.split,
                retrieval_hit=retrieval_hit,
                citation_traceable=_citation_traceable(hits),
                retrieval_outcome=retrieval_outcome(required_doc_ids, actual_doc_ids),
                citation_support=DEFERRED,
                answer_completion=DEFERRED,
                expected_behavior=case.expected_behavior,
                observed_behavior=observed_behavior,
                allowed_behavior=case.allowed_behavior,
                forbidden_behavior=case.forbidden_behavior,
                fabrication_risk=case.expected_behavior == "refuse" and bool(actual_doc_ids),
                unexpected_doc_ids=tuple(sorted(actual_doc_ids - required_doc_ids)),
                tool_calls=1,
                elapsed_us=elapsed_us,
            )
        )
    return tuple(records)


def summarize_records(
    records: tuple[CaseRecord, ...],
) -> dict[str, dict[str, int]]:
    summary = {
        split: {
            "total": 0,
            "retrieval_hit": 0,
            "citation_traceable": 0,
            "retrieval_matched": 0,
            "retrieval_extra_hits": 0,
            "retrieval_missed": 0,
            "retrieval_false_positive": 0,
            "answer_level_deferred": 0,
            "behavior_not_measured": 0,
            "fabrication_risk": 0,
            "tool_calls": 0,
            "elapsed_us": 0,
        }
        for split in SPLITS
    }
    for record in records:
        bucket = summary[record.split]
        bucket["total"] += 1
        bucket["retrieval_hit"] += record.retrieval_hit
        bucket["citation_traceable"] += record.citation_traceable
        bucket[f"retrieval_{record.retrieval_outcome}"] += 1
        bucket["answer_level_deferred"] += (
            record.citation_support == DEFERRED and record.answer_completion == DEFERRED
        )
        bucket["behavior_not_measured"] += record.observed_behavior == "not_measured"
        bucket["fabrication_risk"] += record.fabrication_risk
        bucket["tool_calls"] += record.tool_calls
        bucket["elapsed_us"] += record.elapsed_us
    return summary


def _evaluate(cases: tuple[KeywordCase, ...], limit: int) -> dict[str, dict[str, int]]:
    results = {
        split: {
            "total": 0,
            "passed": 0,
            "unexpected_hits": 0,
            "no_evidence": 0,
            "missed_expected": 0,
            "false_positive_no_evidence": 0,
        }
        for split in SPLITS
    }
    for case in cases:
        summary = results[case.split]
        summary["total"] += 1
        actual_doc_ids = {hit.doc_id for hit in keyword_search(case.query, limit=limit)}
        required_doc_ids = set(case.required_evidence)
        if actual_doc_ids - required_doc_ids:
            summary["unexpected_hits"] += 1
        if not required_doc_ids:
            if actual_doc_ids:
                summary["false_positive_no_evidence"] += 1
            else:
                summary["no_evidence"] += 1
        elif not required_doc_ids.issubset(actual_doc_ids):
            summary["missed_expected"] += 1
        if actual_doc_ids == required_doc_ids:
            summary["passed"] += 1
    return results


def evaluate_cases(
    cases: tuple[KeywordCase, ...], *, limit: int
) -> dict[str, dict[str, int]]:
    return _evaluate(cases, limit)


def compare_limits(
    cases: tuple[KeywordCase, ...], *, limits: tuple[int, ...]
) -> dict[int, dict[str, dict[str, int]]]:
    if not limits or any(limit < 1 for limit in limits):
        raise ValueError("invalid evaluation limits")
    return {limit: _evaluate(cases, limit) for limit in limits}
