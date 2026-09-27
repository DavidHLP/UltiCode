"""Human review worksheet and verdict intake for citation support.

DAV-45 and DAV-58 both require judging three separate things per citation:
whether the citation *exists*, whether the fragment *supports* the conclusion,
and whether the conclusion is *derivable* from the submission facts available.
Only the first is mechanical (:mod:`citation_integrity`); the other two need a
person or a model.

This module does the mechanical parts around that judgement and nothing else. It
never infers a verdict, and an incomplete verdict set never counts as a pass.

A worksheet must not launder an unverified citation into something that looks
review-ready, so every row is built defensively:

* provenance and permission come from the resolved document and its manifest
  entry, never from the fields the citation itself claims;
* the quoted fragment is passed through in full, not truncated;
* the integrity gate runs first and its verdict travels with the row;
* the claim under review is passed in explicitly — the citation is never linked to
  a conclusion by guessing a position in some list.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from citation_integrity import all_verified, check_citations
from corpus_manifest import assert_manifest_covers
from corpus_manifest import ManifestEntry
from retrieval import SourceDocument

VERDICT_KEYS = ("exists", "supports", "derivable")


def review_row_id(chunk_id: str, claim: str, quote: str) -> str:
    """Identify one worksheet row by what it actually asks the reviewer.

    A verdict is only meaningful for the claim and the quote it was written
    against, so the id covers both; keying on chunk id alone would let a verdict
    file be reused across worksheets.
    """
    import hashlib

    digest = hashlib.sha256()
    for part in (chunk_id, claim, quote):
        digest.update(part.encode("utf-8"))
        digest.update(b"\0")
    return f"{chunk_id}:{digest.hexdigest()[:16]}"


@dataclass(frozen=True)
class ReviewItem:
    """One citation awaiting judgement. Verdicts are deliberately unset."""

    review_id: str
    chunk_id: str
    doc_id: str
    source_position: str
    permission: str
    permission_scope: str
    access_scope: str
    quote: str
    claim: str
    integrity_verdict: str
    verdicts: dict[str, bool | None]


def build_worksheet(
    *,
    claim: str,
    citations: object,
    documents: tuple[SourceDocument, ...],
    manifest: tuple[ManifestEntry, ...],
) -> tuple[ReviewItem, ...]:
    """Build rows for ``claim`` from ``citations``.

    ``claim`` is required: the data does not record which conclusion a citation
    supports, so this module will not invent that link.
    """
    if not isinstance(claim, str) or not claim.strip():
        raise ValueError("claim must be stated explicitly for review")
    # A stale manifest would label the wrong permission for a resolved
    # citation, so the pair is validated before any manifest field is read.
    assert_manifest_covers(manifest, documents)
    by_chunk = {document.chunk_id: document for document in documents}
    by_doc = {entry.doc_id: entry for entry in manifest}
    rows: list[ReviewItem] = []
    seen_chunks: set[str] = set()
    if not isinstance(citations, list):
        raise VerdictError("citations must be a list")
    for index, citation in enumerate(citations):
        if not isinstance(citation, dict):
            # Dropping it would let the remaining rows pass a gate that never saw
            # this entry, so the whole worksheet is refused.
            raise VerdictError(f"citation {index} is not an object")
        chunk_id = str(citation.get("chunk_id", ""))
        if chunk_id in seen_chunks:
            # Two rows sharing a chunk id would collapse into one expected id, so a
            # single verdict could approve a fragment nobody reviewed.
            raise VerdictError(f"duplicate citation for chunk {chunk_id!r}")
        seen_chunks.add(chunk_id)
        checks = check_citations([citation], documents)
        verdict = checks[0].verdict if checks else "malformed"
        document = by_chunk.get(chunk_id)
        # Identity and authorization metadata come from the resolved document and
        # its manifest entry only. An unresolved chunk must never inherit the
        # permission of a doc_id the citation merely claims.
        doc_id = document.doc_id if document else str(citation.get("doc_id", ""))
        entry = by_doc.get(doc_id) if document else None
        rows.append(
            ReviewItem(
                review_id=review_row_id(chunk_id, claim, str(citation.get("text", ""))),
                chunk_id=chunk_id,
                doc_id=doc_id,
                source_position=document.source_position if document else "unknown",
                # Permission is a manifest fact; access_scope is a different thing.
                permission=entry.permission if entry else "undeclared",
                permission_scope=entry.scope if entry else "undeclared",
                access_scope=document.access_scope if document else "unknown",
                # Full fragment: a reviewer must not judge a truncated quote.
                quote=str(citation.get("text", "")),
                claim=claim,
                integrity_verdict=verdict,
                verdicts={key: None for key in VERDICT_KEYS},
            )
        )
    return tuple(rows)


def worksheet_to_json(items: tuple[ReviewItem, ...]) -> str:
    """Serialise a worksheet a person can fill in; verdicts start unset."""
    return json.dumps(
        [
            {
                # The reviewer fills this file in and feeds it back, so the id the
                # loader binds on has to be present.
                "review_id": item.review_id,
                "chunk_id": item.chunk_id,
                "doc_id": item.doc_id,
                "source_position": item.source_position,
                "permission": item.permission,
                "permission_scope": item.permission_scope,
                "access_scope": item.access_scope,
                "quote": item.quote,
                "claim": item.claim,
                "integrity_verdict": item.integrity_verdict,
                "verdicts": item.verdicts,
            }
            for item in items
        ],
        ensure_ascii=False,
        indent=2,
    )


class VerdictError(ValueError):
    """The verdict set is missing, malformed, or contradicts the integrity gate."""


def _reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    """Duplicate keys would silently keep the last value and erase a rejection."""
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise VerdictError(f"duplicate key in a verdict object: {key!r}")
        result[key] = value
    return result


def load_verdicts(path: Path, items: tuple[ReviewItem, ...]) -> dict[str, dict[str, object]]:
    """Read verdicts, requiring one complete entry per reviewed citation."""
    try:
        raw = json.loads(
            path.read_text(encoding="utf-8"), object_pairs_hook=_reject_duplicate_keys
        )
    except VerdictError:
        raise
    except ValueError as error:
        raise VerdictError(f"verdict file is not valid JSON: {error}") from None
    if not isinstance(raw, list) or not raw:
        raise VerdictError("verdicts must be a non-empty list")
    verdicts: dict[str, dict[str, object]] = {}
    for entry in raw:
        if not isinstance(entry, dict):
            raise VerdictError("verdict entries must be objects")
        chunk_id = entry.get("chunk_id")
        if not isinstance(chunk_id, str) or not chunk_id:
            raise VerdictError("verdict entry needs a chunk_id")
        review_id = entry.get("review_id")
        if not isinstance(review_id, str) or not review_id:
            raise VerdictError("verdict entry needs a review_id")
        if review_id in verdicts:
            # Last-write-wins would erase an earlier rejection.
            raise VerdictError(f"duplicate verdict for review {review_id!r}")
        values = entry.get("verdicts")
        if not isinstance(values, dict) or any(
            not isinstance(values.get(key), bool) for key in VERDICT_KEYS
        ):
            raise VerdictError(f"{chunk_id}: all of {VERDICT_KEYS} must be booleans")
        verdicts[review_id] = {
            **{key: values[key] for key in VERDICT_KEYS},
            "note": str(entry.get("note", "")),
        }
    expected = {item.review_id for item in items}
    missing = expected - set(verdicts)
    if missing:
        raise VerdictError(f"no verdict for: {sorted(missing)}")
    unexpected = set(verdicts) - expected
    if unexpected:
        # Exact set equality: a stale verdict must not pass unnoticed.
        raise VerdictError(f"verdicts for unreviewed chunks: {sorted(unexpected)}")
    return verdicts


def summarize(
    items: tuple[ReviewItem, ...], verdicts: dict[str, dict[str, object]]
) -> dict[str, object]:
    """Counts plus an explicit pass decision. Disagreement is preserved."""
    counts = {key: 0 for key in VERDICT_KEYS}
    disagreements: list[str] = []
    unverified: list[str] = []
    for item in items:
        verdict = verdicts[item.review_id]
        for key in VERDICT_KEYS:
            counts[key] += 1 if verdict[key] else 0
        if not (verdict["exists"] and verdict["supports"] and verdict["derivable"]):
            disagreements.append(item.chunk_id)
        if item.integrity_verdict != "verified":
            unverified.append(item.chunk_id)
    return {
        "reviewed": len(items),
        "counts": counts,
        "not_supported": disagreements,
        "integrity_unverified": unverified,
        # Fails closed on two independent counts: a human must support every
        # citation, and every citation must already have passed the integrity
        # gate. An unverified citation can never be waved through by judgement.
        "gate_passed": bool(items) and not disagreements and not unverified,
    }
