"""Canonical workflow values and offline draft construction."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from typing import Literal

from sourced_analysis import analyze_submission, validate_submission_facts


Status = Literal[
    "draft", "analyzing", "awaiting_confirmation", "confirmed", "saving",
    "saved", "unknown", "failed", "cancelled",
]


@dataclass(frozen=True)
class WorkflowAction:
    kind: str
    payload: dict[str, object]
    expected_run: str
    expected_version: int


def canonical_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def params_digest(owner_id: str, source_submission_id: str, draft_version: int, title: str, content: str) -> str:
    encoded = json.dumps(
        [owner_id, source_submission_id.lower(), draft_version, title, content],
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def make_draft(submission: dict[str, object], question: str) -> dict[str, object]:
    submission_id, status = validate_submission_facts(submission)
    analysis = analyze_submission({"id": submission_id, "status": status}, question)
    facts = list(analysis["facts"])
    hypotheses = list(analysis["hypotheses"])
    return {
        "title": f"学习计划 {submission_id}",
        "content": "\n".join([*facts, *hypotheses, "下一步：核对上述可见事实，选择一项待验证假设进行练习。"]),
        "facts": facts,
        "hypotheses": hypotheses,
        "citations": list(analysis.get("citations", [])),
        "citation_checks": list(analysis.get("citation_checks", [])),
        "sample_scope": "synthetic corpus metadata only; source code and test data are not available",
    }
