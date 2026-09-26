"""Deterministic U02 sourced analysis over bounded synthetic evidence."""

from __future__ import annotations

import re
import unicodedata

from citation_integrity import check_citations
from retrieval import keyword_search, load_sample_corpus


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

_NO_EVIDENCE_HYPOTHESIS = "当前没有检索到授权资料，不能据此提出具体诊断。"
_METADATA_ONLY_HYPOTHESIS = (
    "当前只有提交状态，没有源码或失败用例；不能据此定位具体代码行、复现失败输入或断言运行结果。"
)


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
    """Return facts, hypotheses, citations, and per-citation integrity checks.

    ``citation_checks`` records whether each citation is traceable to its source
    document. A ``verified`` verdict means the citation and its text come from
    the recorded source; it does not mean the fragment supports the conclusion.
    """
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
            "hypotheses": [_NO_EVIDENCE_HYPOTHESIS],
            "citations": [],
            "citation_checks": [],
        }

    citations = [hit.as_model_dict() for hit in hits]
    return {
        "facts": facts,
        "hypotheses": [_METADATA_ONLY_HYPOTHESIS],
        "citations": citations,
        "citation_checks": [
            {"chunk_id": check.chunk_id, "verdict": check.verdict, "detail": check.detail}
            for check in check_citations(citations, load_sample_corpus())
        ],
    }


async def first_wrong_answer_submission(tools: dict[str, object]) -> dict[str, object] | None:
    """Return the first Wrong Answer submission in owner page order, scanning every reported page."""
    get_my_submissions = tools["get_my_submissions"]
    seen: set[str] = set()
    page = 1
    expected_total: int | None = None
    # ponytail: scan length follows the owner-reported total; a dishonest total only
    # costs extra read-only requests, and each page stays projection-validated.
    while True:
        listing = await get_my_submissions({"page": page, "pageSize": 100})
        items = listing["items"]
        total = listing["total"]
        if expected_total is None:
            expected_total = total
        elif total != expected_total:
            raise ValueError("submission total changed during scan")
        for item in items:
            submission_id = item["id"]
            if submission_id in seen:
                raise ValueError("duplicate submission id across pages")
            seen.add(submission_id)
        for item in items:
            if item.get("status") == "Wrong Answer":
                return item
        if not items or len(seen) >= total:
            return None
        page += 1
