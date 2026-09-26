"""Deterministic keyword retrieval evaluation for the U02 development slice."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from retrieval import keyword_search

_CASES_PATH = Path(__file__).resolve().parents[1] / "data" / "keyword_cases.json"


@dataclass(frozen=True)
class KeywordCase:
    case_id: str
    split: str
    query: str
    expected_doc_ids: tuple[str, ...]


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
        if (
            not isinstance(case_id, str)
            or not case_id
            or split not in {"development", "holdout"}
            or not isinstance(query, str)
            or not query.strip()
            or not isinstance(expected_doc_ids, list)
            or not all(isinstance(doc_id, str) and doc_id for doc_id in expected_doc_ids)
        ):
            raise ValueError("invalid keyword case")
        cases.append(
            KeywordCase(
                case_id=case_id,
                split=split,
                query=query,
                expected_doc_ids=tuple(expected_doc_ids),
            )
        )
    return tuple(cases)


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
