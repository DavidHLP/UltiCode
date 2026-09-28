"""Deterministic U02 sourced analysis over bounded synthetic evidence."""

from __future__ import annotations

import re
from pathlib import Path
import unicodedata

from citation_integrity import check_citations
from corpus_manifest import assert_manifest_covers, load_manifest
from retrieval import (
    MAX_SOURCE_CHARS,
    SourceDocument,
    keyword_search,
    load_sample_corpus,
)


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


def validate_submission_facts(submission: dict[str, object]) -> tuple[str, str]:
    """Return the (id, status) pair after boundary validation.

    A blank status would otherwise match every retrieved fragment when used as a
    substring filter, so validation happens before any fact is built.
    """
    if not isinstance(submission, dict):
        raise ValueError("invalid submission facts")
    submission_id = _fact_text(submission.get("id"), name="id", max_length=40)
    status = _fact_text(submission.get("status"), name="status", max_length=64)
    if status not in _ALLOWED_STATUSES:
        raise ValueError("invalid submission facts")
    return submission_id, status


def analyze_submission(
    submission: dict[str, object],
    question: str,
    *,
    documents: tuple[SourceDocument, ...] | None = None,
    manifest_path: Path | None = None,
) -> dict[str, object]:
    """Return facts, hypotheses, citations, and per-citation integrity checks.

    ``citation_checks`` records whether each citation is traceable to its source
    document. A ``verified`` verdict means the citation and its text come from
    the recorded source; it does not mean the fragment supports the conclusion.

    ``documents`` defaults to the pinned sample corpus so the recorded baseline is
    unchanged, and that default is gated by ``load_sample_corpus``. A supplied corpus
    must arrive with the path to its own manifest, which is parsed and validated here
    the same way the checked-in manifests are (permission, scope, projection, source
    trust, then content digests bound to the exact text) before a single citation can
    be built from it. Entries assembled in memory are not accepted: a caller could
    forge them, and ``assert_manifest_covers`` alone does not validate their
    declarations. It must also declare itself agent-authored synthetic: this
    parameter is a test seam, so it may exercise the evidence path but may never
    launder a document into real or licensed material. How many citations an answer
    can emit is a property of the material in front of it, not of the default.
    """
    submission_id, status = validate_submission_facts(submission)
    facts = [f"提交 {submission_id} 的状态是 {status}。"]
    normalized_status = status.casefold()
    # One snapshot for retrieval and verification: a reload could check the
    # quotes against text the hits never came from.
    if documents is None:
        corpus = load_sample_corpus()
    else:
        # Fail closed: no manifest, no evidence. A caller that skips this hands the
        # model documents nothing binds to the text they claim to be.
        if manifest_path is None:
            raise ValueError("supplied corpus requires manifest validation")
        if not isinstance(documents, tuple) or not documents:
            raise ValueError("invalid corpus")
        # Parsed here, not passed in: validate_manifest is what checks permission,
        # scope, projection and source trust, and in-memory entries would skip it.
        assert_manifest_covers(load_manifest(Path(manifest_path)), documents)
        for document in documents:
            # Both checked-in loaders apply this bound; a supplied corpus must not
            # become the one path that ships whole documents into every citation and
            # from there into the model prompt.
            if not document.text or len(document.text) > MAX_SOURCE_CHARS:
                raise ValueError("supplied corpus document exceeds the source cap")
            if (
                document.sample_kind != "synthetic"
                or document.access_scope != "agent-authored-synthetic"
            ):
                raise ValueError("supplied corpus must be agent-authored synthetic")
        corpus = documents
    # The status is a hard requirement, so it narrows the corpus before ranking and
    # before the result limit: otherwise higher-ranked documents without it could
    # consume the slots a status-bearing document needs.
    hits = tuple(
        keyword_search(question, documents=corpus, require_text=normalized_status)
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
            for check in check_citations(citations, corpus)
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
