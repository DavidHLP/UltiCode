"""Deterministic U02 sourced analysis over bounded synthetic evidence."""

from __future__ import annotations

import re
import unicodedata

from retrieval import keyword_search


_ALLOWED_STATUSES = {
    "Pending",
    "Judging",
    "Accepted",
    "Wrong Answer",
    "Time Limit Exceeded",
    "Memory Limit Exceeded",
    "Output Limit Exceeded",
    "Presentation Error",
    "Runtime Error",
    "Compile Error",
    "Sandbox Error",
    "System Error",
}


def _fact_text(value: object, *, name: str, max_length: int) -> str:
    if not isinstance(value, str) or not value.strip() or len(value) > max_length:
        raise ValueError("invalid submission facts")
    if any(unicodedata.category(char).startswith("C") for char in value):
        raise ValueError("invalid submission facts")
    if name == "id" and not re.fullmatch(
        r"(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}|sub-[0-9]{1,35})",
        value,
    ):
        raise ValueError("invalid submission facts")
    return value


def analyze_submission(submission: dict[str, object], question: str) -> dict[str, object]:
    if not isinstance(submission, dict):
        raise ValueError("invalid submission facts")
    submission_id = _fact_text(submission.get("id"), name="id", max_length=40)
    status = _fact_text(submission.get("status"), name="status", max_length=64)
    if status not in _ALLOWED_STATUSES:
        raise ValueError("invalid submission facts")
    facts = [f"提交 {submission_id} 的状态是 {status}。"]
    normalized_status = status.casefold()
    hits = tuple(
        hit for hit in keyword_search(question) if normalized_status in hit.text.casefold()
    )
    if not hits:
        return {
            "facts": facts,
            "hypotheses": ["当前没有检索到授权资料，不能据此提出具体诊断。"],
            "citations": [],
        }

    return {
        "facts": facts,
        "hypotheses": [
            "当前只有提交状态，没有源码或失败用例；不能据此定位具体代码行、复现失败输入或断言运行结果。",
        ],
        "citations": [hit.as_model_dict() for hit in hits],
    }
