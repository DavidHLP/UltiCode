"""Deterministic citation integrity gate for sourced analysis.

This checks what can be decided without a model or a human: that every citation
points at a chunk that exists and that **every provenance field** on the
citation matches the recorded source document — chunk id, document id, version,
source path, source position, access scope, and the sample/real marker — and that
the carried text appears verbatim in that source. Together these catch the *wrong
citation* and *fabricated quote* classes outright.

It deliberately does not decide whether a fragment **supports** a conclusion.
That question needs the model or human pass tracked in DAV-58, so a verified
citation is never reported as a supported conclusion.

Note on strength: this gate is only as strong as the shape it is given. A
citation that carries nothing but a document id — which is what the current
real-model smoke asks the model to return — cannot be checked for a verbatim
quote, so membership alone is a weaker guarantee than this module provides.
"""

from __future__ import annotations

from dataclasses import dataclass

from retrieval import SourceDocument

VERDICTS = (
    "verified",
    "unknown_source",
    "provenance_mismatch",
    "text_not_in_source",
    "malformed",
)
#: Provenance fields a citation must carry and that must match the source.
PROVENANCE_FIELDS = (
    "chunk_id",
    "doc_id",
    "version",
    "source_path",
    "source_position",
    "access_scope",
    "sample_kind",
    "source_trust",
)
_REQUIRED_FIELDS = (*PROVENANCE_FIELDS, "text")
#: Retrieved text is always untrusted data; a citation claiming otherwise is
#: re-grading the source and must not verify.
EXPECTED_SOURCE_TRUST = "untrusted-data"


@dataclass(frozen=True)
class CitationCheck:
    chunk_id: str
    verdict: str
    detail: str


def _text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def _exact(value: object) -> str:
    """Untrimmed: identifiers are compared byte for byte, so ``" v1 "`` fails."""
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
            not _text(citation.get(field)) for field in _REQUIRED_FIELDS
        ):
            checks.append(
                CitationCheck("", "malformed", "a required citation field is missing or not text")
            )
            continue
        chunk_id = _text(citation["chunk_id"])
        document = by_chunk.get(chunk_id)
        if document is None:
            checks.append(CitationCheck(chunk_id, "unknown_source", "no such chunk"))
            continue
        if _exact(getattr(document, "source_trust", EXPECTED_SOURCE_TRUST)) != EXPECTED_SOURCE_TRUST:
            checks.append(
                CitationCheck(chunk_id, "provenance_mismatch", "source is not untrusted-data")
            )
            continue
        drifted = [
            field
            for field in PROVENANCE_FIELDS
            if _exact(citation[field]) != _exact(getattr(document, field, None))
        ]
        if drifted:
            checks.append(
                CitationCheck(
                    chunk_id, "provenance_mismatch", f"differs: {','.join(drifted)}"
                )
            )
            continue
        if _exact(citation["text"]) not in document.text:
            checks.append(
                CitationCheck(chunk_id, "text_not_in_source", "text is not verbatim")
            )
            continue
        checks.append(CitationCheck(chunk_id, "verified", ""))
    return tuple(checks)


def all_verified(checks: tuple[CitationCheck, ...]) -> bool:
    """A citation set passes only when it is non-empty and every entry verifies."""
    return bool(checks) and all(check.verdict == "verified" for check in checks)
