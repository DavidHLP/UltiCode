"""Deterministic citation integrity gate for sourced analysis.

This checks what can be decided without a model or a human: that every citation
points at a chunk that exists, that its metadata matches that chunk, and that the
quoted text appears verbatim in the recorded source. It catches the *wrong
citation* and *fabricated quote* classes outright.

It deliberately does not decide whether a fragment **supports** a conclusion.
That question needs the model or human pass tracked in DAV-58, so a verified
citation is never reported as a supported conclusion.
"""

from __future__ import annotations

from dataclasses import dataclass

from retrieval import SourceDocument

VERDICTS = ("verified", "unknown_source", "metadata_mismatch", "text_not_in_source", "malformed")
_REQUIRED_TEXT_FIELDS = ("chunk_id", "doc_id", "version", "text")


@dataclass(frozen=True)
class CitationCheck:
    chunk_id: str
    verdict: str
    detail: str


def _text(value: object) -> str:
    return value if isinstance(value, str) else ""


def check_citations(
    citations: object, documents: tuple[SourceDocument, ...]
) -> tuple[CitationCheck, ...]:
    if not isinstance(citations, (list, tuple)):
        return (CitationCheck("", "malformed", "citations must be a sequence"),)
    by_chunk = {document.chunk_id: document for document in documents}
    checks: list[CitationCheck] = []
    for citation in citations:
        if not isinstance(citation, dict) or any(
            not _text(citation.get(field)).strip() for field in _REQUIRED_TEXT_FIELDS
        ):
            checks.append(CitationCheck("", "malformed", "citation fields missing or not text"))
            continue
        chunk_id = _text(citation["chunk_id"])
        document = by_chunk.get(chunk_id)
        if document is None:
            checks.append(CitationCheck(chunk_id, "unknown_source", "no such chunk"))
            continue
        if (
            _text(citation["doc_id"]) != document.doc_id
            or _text(citation["version"]) != document.version
        ):
            checks.append(
                CitationCheck(chunk_id, "metadata_mismatch", "doc_id or version differs")
            )
            continue
        quoted = _text(citation["text"])
        if quoted not in document.text:
            checks.append(
                CitationCheck(chunk_id, "text_not_in_source", "quote is not verbatim")
            )
            continue
        checks.append(CitationCheck(chunk_id, "verified", ""))
    return tuple(checks)


def all_verified(checks: tuple[CitationCheck, ...]) -> bool:
    return bool(checks) and all(check.verdict == "verified" for check in checks)
