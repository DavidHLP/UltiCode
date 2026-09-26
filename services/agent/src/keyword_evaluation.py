"""Deterministic keyword retrieval evaluation for the U02 development slice.

Besides the pass/miss summary, every case produces a record covering the
dimensions the DAV-45 acceptance list asks for: retrieval hit, citation
traceability, task completion, clarify/refuse behaviour, tool calls and elapsed
time. Citation *semantics* are deliberately not judged here — whether a
fragment supports a conclusion needs the model or human pass tracked in DAV-58,
so this module only proves every returned hit is traceable to a document.
"""

from __future__ import annotations

import json
import time
from dataclasses import dataclass
from pathlib import Path

from retrieval import SourceHit, keyword_search

_CASES_PATH = Path(__file__).resolve().parents[1] / "data" / "keyword_cases.json"
EXPECTED_BEHAVIORS = frozenset({"cite", "no_evidence", "refuse"})

# DAV-22 asks every case to declare its required evidence plus the allowed and
# forbidden behaviour. The behaviour pair is per behaviour class, not per case,
# so the dataset stays readable while the contract stays explicit.
ALLOWED_BEHAVIORS = {
    "cite": "answer from the retrieved fragment and cite it",
    "no_evidence": "state that no material supports an answer",
    "refuse": "state that the available data cannot answer this",
}
FORBIDDEN_BEHAVIORS = {
    "cite": "claim the code was run or that a specific line was located",
    "no_evidence": "answer from general knowledge without a cited fragment",
    "refuse": "name a code line, a runtime cause, or any detail absent from the projection",
}




@dataclass(frozen=True)
class KeywordCase:
    case_id: str
    split: str
    query: str
    expected_doc_ids: tuple[str, ...]
    answerable: bool
    expected_behavior: str

    @property
    def required_evidence(self) -> tuple[str, ...]:
        """DAV-22's required-evidence annotation: the documents an answer needs."""
        return self.expected_doc_ids



@dataclass(frozen=True)
class CaseRecord:
    """One evaluated case. ``tool_calls`` counts retrieval calls only."""

    case_id: str
    split: str
    retrieval_hit: bool
    citation_traceable: bool
    task_completion: str
    expected_behavior: str
    observed_behavior: str
    allowed_behavior: str
    forbidden_behavior: str
    fabrication_risk: bool
    unexpected_doc_ids: tuple[str, ...]
    tool_calls: int
    elapsed_ms: int


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
        expected_doc_ids = raw_case.get("expected_doc_ids")
        answerable = raw_case.get("answerable")
        expected_behavior = raw_case.get("expected_behavior")
        if (
            not isinstance(case_id, str)
            or not case_id
            or split not in {"development", "holdout"}
            or not isinstance(query, str)
            or not query.strip()
            or not isinstance(expected_doc_ids, list)
            or not all(isinstance(doc_id, str) and doc_id for doc_id in expected_doc_ids)
            or not isinstance(answerable, bool)
            or expected_behavior not in EXPECTED_BEHAVIORS
        ):
            raise ValueError("invalid keyword case")
        cases.append(
            KeywordCase(
                case_id=case_id,
                split=split,
                query=query,
                expected_doc_ids=tuple(expected_doc_ids),
                answerable=answerable,
                expected_behavior=expected_behavior,
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



def _completion(expected: set[str], actual: set[str]) -> str:
    if not expected:
        return "false_positive" if actual else "matched"
    if not expected <= actual:
        return "missed"
    return "matched" if actual == expected else "extra_hits"


def evaluate_case_records(
    cases: tuple[KeywordCase, ...], *, limit: int
) -> tuple[CaseRecord, ...]:
    records: list[CaseRecord] = []
    for case in cases:
        started = time.perf_counter()
        hits = keyword_search(case.query, limit=limit)
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        actual_doc_ids = {hit.doc_id for hit in hits}
        expected_doc_ids = set(case.expected_doc_ids)
        if case.expected_behavior == "refuse":
            # The corpus cannot answer this; a returned fragment must not be
            # mistaken for a supported answer.
            observed_behavior = "refuse_required"
            retrieval_hit = bool(actual_doc_ids)
        elif actual_doc_ids:
            observed_behavior = "answered_with_citation"
            retrieval_hit = expected_doc_ids <= actual_doc_ids
        else:
            observed_behavior = "no_evidence"
            retrieval_hit = not expected_doc_ids
        records.append(
            CaseRecord(
                case_id=case.case_id,
                split=case.split,
                retrieval_hit=retrieval_hit,
                citation_traceable=_citation_traceable(hits),
                task_completion=_completion(expected_doc_ids, actual_doc_ids),
                expected_behavior=case.expected_behavior,
                observed_behavior=observed_behavior,
                allowed_behavior=ALLOWED_BEHAVIORS[case.expected_behavior],
                forbidden_behavior=FORBIDDEN_BEHAVIORS[case.expected_behavior],
                fabrication_risk=case.expected_behavior == "refuse" and bool(actual_doc_ids),
                unexpected_doc_ids=tuple(sorted(actual_doc_ids - expected_doc_ids)),
                tool_calls=1,
                elapsed_ms=elapsed_ms,
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
            "task_matched": 0,
            "task_extra_hits": 0,
            "task_missed": 0,
            "task_false_positive": 0,
            "refuse_required": 0,
            "fabrication_risk": 0,
            "tool_calls": 0,
            "elapsed_ms": 0,
        }
        for split in ("development", "holdout")
    }
    for record in records:
        bucket = summary[record.split]
        bucket["total"] += 1
        bucket["retrieval_hit"] += record.retrieval_hit
        bucket["citation_traceable"] += record.citation_traceable
        bucket[f"task_{record.task_completion}"] += 1
        bucket["refuse_required"] += record.observed_behavior == "refuse_required"
        bucket["fabrication_risk"] += record.fabrication_risk
        bucket["tool_calls"] += record.tool_calls
        bucket["elapsed_ms"] += record.elapsed_ms
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
        for split in ("development", "holdout")
    }
    for case in cases:
        summary = results[case.split]
        summary["total"] += 1
        actual_doc_ids = {hit.doc_id for hit in keyword_search(case.query, limit=limit)}
        expected_doc_ids = set(case.expected_doc_ids)
        if actual_doc_ids - expected_doc_ids:
            summary["unexpected_hits"] += 1
        if not expected_doc_ids:
            if actual_doc_ids:
                summary["false_positive_no_evidence"] += 1
            else:
                summary["no_evidence"] += 1
        elif not expected_doc_ids.issubset(actual_doc_ids):
            summary["missed_expected"] += 1
        if actual_doc_ids == expected_doc_ids:
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
