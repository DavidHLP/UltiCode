"""Human review worksheet and verdict intake for citation support.

DAV-45 and DAV-58 both require judging three separate things per citation:
whether the citation *exists*, whether the fragment *supports* the conclusion,
and whether the conclusion is *derivable* from the submission facts available.
Only the first is mechanical (:mod:`citation_integrity`); the other two need a
person or a model.

This module does the mechanical parts around that judgement and nothing else:
it builds a review worksheet from an answer plus its corpus, and it ingests
strictly-shaped verdicts. It never infers a verdict, and an incomplete verdict
set never counts as a pass.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from retrieval import SourceDocument

VERDICT_KEYS = ("exists", "supports", "derivable")


@dataclass(frozen=True)
class ReviewItem:
    """One citation awaiting judgement. Verdicts are deliberately unset."""

    chunk_id: str
    doc_id: str
    source_position: str
    permission: str
    quote: str
    claim: str
    verdicts: dict[str, bool | None]


def build_worksheet(
    answer: dict[str, object], documents: tuple[SourceDocument, ...]
) -> tuple[ReviewItem, ...]:
    """Pair each citation with the verbatim fragment and the claim to judge."""
    by_chunk = {document.chunk_id: document for document in documents}
    hypotheses = answer.get("hypotheses")
    claims = [str(item) for item in hypotheses] if isinstance(hypotheses, list) else []
    items: list[ReviewItem] = []
    for index, citation in enumerate(answer.get("citations") or []):
        if not isinstance(citation, dict):
            continue
        chunk_id = str(citation.get("chunk_id", ""))
        document = by_chunk.get(chunk_id)
        items.append(
            ReviewItem(
                chunk_id=chunk_id,
                doc_id=str(citation.get("doc_id", "")),
                source_position=document.source_position if document else "unknown",
                permission=str(citation.get("access_scope", "unknown")),
                quote=str(citation.get("text", ""))[:200],
                claim=claims[index] if index < len(claims) else "",
                verdicts={key: None for key in VERDICT_KEYS},
            )
        )
    return tuple(items)


def worksheet_to_json(items: tuple[ReviewItem, ...]) -> str:
    """Serialise a worksheet a person can fill in; verdicts start unset."""
    return json.dumps(
        [
            {
                "chunk_id": item.chunk_id,
                "doc_id": item.doc_id,
                "source_position": item.source_position,
                "permission": item.permission,
                "quote": item.quote,
                "claim": item.claim,
                "verdicts": item.verdicts,
            }
            for item in items
        ],
        ensure_ascii=False,
        indent=2,
    )


class VerdictError(ValueError):
    """The verdict set is missing, malformed, or contradicts the integrity gate."""


def load_verdicts(path: Path, items: tuple[ReviewItem, ...]) -> dict[str, dict[str, object]]:
    """Read verdicts, requiring one complete entry per reviewed citation."""
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list) or not raw:
        raise VerdictError("verdicts must be a non-empty list")
    verdicts: dict[str, dict[str, object]] = {}
    for entry in raw:
        if not isinstance(entry, dict):
            raise VerdictError("verdict entries must be objects")
        chunk_id = entry.get("chunk_id")
        if not isinstance(chunk_id, str) or not chunk_id:
            raise VerdictError("verdict entry needs a chunk_id")
        values = entry.get("verdicts")
        if not isinstance(values, dict) or any(
            not isinstance(values.get(key), bool) for key in VERDICT_KEYS
        ):
            raise VerdictError(f"{chunk_id}: all of {VERDICT_KEYS} must be booleans")
        verdicts[chunk_id] = {**{key: values[key] for key in VERDICT_KEYS}, "note": str(entry.get("note", ""))}
    expected = {item.chunk_id for item in items}
    missing = expected - set(verdicts)
    if missing:
        raise VerdictError(f"no verdict for: {sorted(missing)}")
    return verdicts


def summarize(
    items: tuple[ReviewItem, ...], verdicts: dict[str, dict[str, object]]
) -> dict[str, object]:
    """Counts plus an explicit pass decision. Disagreement is preserved."""
    counts = {key: 0 for key in VERDICT_KEYS}
    disagreements: list[str] = []
    for item in items:
        verdict = verdicts[item.chunk_id]
        for key in VERDICT_KEYS:
            counts[key] += 1 if verdict[key] else 0
        if not (verdict["exists"] and verdict["supports"] and verdict["derivable"]):
            disagreements.append(item.chunk_id)
    return {
        "reviewed": len(items),
        "counts": counts,
        "not_supported": disagreements,
        # Fails closed: any citation that is not fully supported blocks the gate.
        "gate_passed": bool(items) and not disagreements,
    }
