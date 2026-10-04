"""Deterministic U02 sourced analysis over bounded synthetic evidence."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
import unicodedata

from citation_integrity import check_citations
from corpus_manifest import (
    SYNTHETIC_PERMISSIONS,
    assert_manifest_covers,
    load_manifest,
    validate_entries,
)
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
#: The exact scope the checked-in synthetic corpus declares. The seam requires this
#: value verbatim: a substring test would accept arbitrary scope text that happens to
#: contain the phrase while still claiming licensed or user material, and the
#: worksheet publishes this string as `permission_scope`.
SYNTHETIC_SCOPE = (
    "synthetic sample corpus for the local deterministic slice; "
    "not user or licensed material"
)

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


def _validated_corpus(
    documents: tuple[SourceDocument, ...] | None,
    manifest_path: Path | None,
    *,
    synthetic_only: bool,
    accepted: tuple[str, str] | None,
) -> tuple[SourceDocument, ...]:
    """Parse, bind and apply a material policy to a caller-supplied corpus.

    The manifest is parsed here rather than handed in: ``load_manifest`` is what
    checks permission, scope, projection and source trust, and entries assembled in
    memory would skip every one of those. ``assert_manifest_covers`` then binds each
    declared field and content digest to the exact text, and the source cap stops a
    single document from shipping whole into every citation and on into the prompt.

    ``synthetic_only`` is the unit-seam rule: a test fixture may exercise the
    evidence path but may never present itself as real or licensed material.
    ``accepted`` is the acceptance rule instead: the caller pins the permission and
    scope it will take, and the manifest either declares exactly those or the run
    stops — a corpus document cannot grant itself a policy.
    """
    if documents is None:
        return load_sample_corpus()
    if manifest_path is None:
        # Fail closed: a caller that skips this hands the model documents nothing
        # binds to the text they claim to be.
        raise ValueError("supplied corpus requires manifest validation")
    if not isinstance(documents, tuple) or not documents:
        raise ValueError("invalid corpus")
    entries = load_manifest(Path(manifest_path))
    assert_manifest_covers(entries, documents)
    for document in documents:
        if not document.text or len(document.text) > MAX_SOURCE_CHARS:
            raise ValueError("supplied corpus document exceeds the source cap")
    if synthetic_only:
        for entry in entries:
            if (entry.sample_kind, entry.access_scope) != (
                "synthetic",
                "agent-authored-synthetic",
            ):
                raise ValueError("supplied corpus must declare the synthetic material")
        for document in documents:
            if (
                document.sample_kind != "synthetic"
                or document.access_scope != "agent-authored-synthetic"
            ):
                raise ValueError("supplied corpus must be agent-authored synthetic")
        for entry in entries:
            # The document fields are only half of the claim: load_manifest accepts a
            # synthetic document whose manifest entry declares a licensed permission,
            # and that is precisely what this seam must never let through.
            if entry.permission not in SYNTHETIC_PERMISSIONS:
                raise ValueError("supplied corpus must declare a synthetic permission")
            if entry.scope != SYNTHETIC_SCOPE:
                raise ValueError("supplied corpus must declare the synthetic scope")
    if accepted is not None:
        permission, scope = accepted
        for entry in entries:
            if entry.permission != permission or entry.scope != scope:
                raise ValueError("supplied corpus declarations not accepted")
    return documents


def _analyze_with_corpus(
    submission_id: str,
    status: str,
    question: str,
    corpus: tuple[SourceDocument, ...],
) -> dict[str, object]:
    facts = [f"提交 {submission_id} 的状态是 {status}。"]
    normalized_status = status.casefold()
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
    unchanged. This parameter is a **test seam**: a supplied corpus must carry its
    own manifest and is restricted to agent-authored synthetic material, so it can
    exercise the evidence path but can never present itself as real or licensed
    material. The acceptance workflow uses ``analyze_authorized_submission`` with an
    explicitly pinned permission and scope instead.
    """
    submission_id, status = validate_submission_facts(submission)
    corpus = _validated_corpus(
        documents, manifest_path, synthetic_only=True, accepted=None
    )
    return _analyze_with_corpus(submission_id, status, question, corpus)


@dataclass(frozen=True)
class ValidatedCorpus:
    """A corpus its preflight already parsed, bound and pinned.

    The acceptance pipeline reads the manifest **once** and carries this immutable
    snapshot through retrieval, the worksheet and the verdict metadata, so an update
    mid-run cannot leave the worksheet and the analyzer describing different material.
    The analyzer still re-checks the binding and the policy against the snapshot, so
    constructing one by hand buys nothing over supplying a manifest path: the entries
    and documents have to agree, and the pinned declarations have to match.
    """

    documents: tuple[SourceDocument, ...]
    entries: tuple[object, ...]
    accepted_permission: str
    accepted_scope: str
    accepted_sample_kind: str
    accepted_access_scope: str
    #: Digest of the manifest bytes the preflight validated, when it read one. Empty
    #: for a hand-assembled snapshot; the acceptance path fills it so the verdict
    #: metadata can bind the verdicts to the declaration that authorised them.
    manifest_digest: str = ""


def analyze_authorized_submission(
    submission: dict[str, object],
    question: str,
    *,
    validated: ValidatedCorpus,
) -> dict[str, object]:
    """The acceptance path: same core, over a corpus its preflight already validated.

    Nothing in the corpus decides what it is allowed to be. The run pins the material
    class — permission, scope, sample kind and access scope — and every entry must
    declare exactly those; the documents and the entries are re-bound here (content
    digests, declared fields) so a hand-assembled snapshot cannot skip either check.
    Synthetic fixtures pass because they declare what they are; authorised material
    passes because the run pinned its policy — not because the file said so.
    """
    submission_id, status = validate_submission_facts(submission)
    entries = tuple(validated.entries)
    if not validated.documents or not entries:
        raise ValueError("invalid corpus")
    # The snapshot's entries never went through `load_manifest` in *this* process if a
    # caller assembled one, so the declaration rules are re-applied here: parsing once
    # at preflight must not be something a forged wrapper can skip.
    validate_entries(entries)
    assert_manifest_covers(entries, validated.documents)
    for document in validated.documents:
        if not document.text or len(document.text) > MAX_SOURCE_CHARS:
            raise ValueError("supplied corpus document exceeds the source cap")
    expected = (
        validated.accepted_permission,
        validated.accepted_scope,
        validated.accepted_sample_kind,
        validated.accepted_access_scope,
    )
    for entry in entries:
        declared = (entry.permission, entry.scope, entry.sample_kind, entry.access_scope)
        if declared != expected:
            raise ValueError("supplied corpus declarations not accepted")
    return _analyze_with_corpus(submission_id, status, question, tuple(validated.documents))


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
