"""Bounded keyword retrieval for the U02 synthetic read-only slice."""

from __future__ import annotations

import re
from dataclasses import asdict, dataclass
from pathlib import Path


MAX_QUERY_CHARS = 200
MAX_SOURCE_CHARS = 1_200
MAX_RESULTS = 3
_CORPUS_DIR = Path(__file__).resolve().parents[1] / "corpus"
_SAMPLE_DOCS = (
    ("sample-accepted-review", "v1", "accepted-submission-review.md"),
    ("sample-status-only", "v1", "status-only-learning.md"),
    ("sample-citation-record", "v1", "citation-record.md"),
)


@dataclass(frozen=True)
class SourceDocument:
    doc_id: str
    version: str
    source_path: str
    access_scope: str
    sample_kind: str
    text: str
    source_position: str

    @property
    def chunk_id(self) -> str:
        return f"{self.doc_id}:{self.version}:1"


@dataclass(frozen=True)
class SourceHit:
    doc_id: str
    version: str
    chunk_id: str
    source_path: str
    source_position: str
    access_scope: str
    sample_kind: str
    source_trust: str
    matched_terms: tuple[str, ...]
    text: str

    def as_model_dict(self) -> dict[str, object]:
        return {**asdict(self), "matched_terms": list(self.matched_terms)}


def _terms(value: str) -> tuple[str, ...]:
    return tuple(re.findall(r"[a-z0-9_]+|[一-鿿]", value.casefold()))


def _validate_query(query: object) -> str:
    if not isinstance(query, str) or not query.strip():
        raise ValueError("invalid search query")
    query = query.strip()
    if len(query) > MAX_QUERY_CHARS:
        raise ValueError("invalid search query")
    return query


def load_sample_corpus() -> tuple[SourceDocument, ...]:
    documents: list[SourceDocument] = []
    for doc_id, version, filename in _SAMPLE_DOCS:
        path = (_CORPUS_DIR / filename).resolve()
        if path.parent != _CORPUS_DIR.resolve():
            raise ValueError("invalid corpus path")
        text = path.read_text(encoding="utf-8").strip()
        if not text or len(text) > MAX_SOURCE_CHARS:
            raise ValueError("invalid corpus document")
        line_count = len(text.splitlines())
        documents.append(
            SourceDocument(
                doc_id=doc_id,
                version=version,
                source_path=f"services/agent/corpus/{filename}",
                access_scope="agent-authored-synthetic",
                sample_kind="synthetic",
                text=text,
                source_position=f"lines 1-{line_count}",
            )
        )
    return tuple(documents)


def keyword_search(query: object, *, limit: int = MAX_RESULTS) -> tuple[SourceHit, ...]:
    if isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= MAX_RESULTS:
        raise ValueError("invalid search limit")
    query_text = _validate_query(query)
    query_terms = _terms(query_text)
    if not query_terms:
        return ()

    hits: list[tuple[int, str, SourceDocument, tuple[str, ...]]] = []
    for document in load_sample_corpus():
        haystack = document.text.casefold()
        matched = tuple(term for term in query_terms if term in haystack)
        if matched:
            hits.append((len(set(matched)), document.doc_id, document, matched))
    hits.sort(key=lambda item: (-item[0], item[1]))
    return tuple(
        SourceHit(
            doc_id=document.doc_id,
            version=document.version,
            chunk_id=document.chunk_id,
            source_path=document.source_path,
            source_position=document.source_position,
            access_scope=document.access_scope,
            sample_kind=document.sample_kind,
            source_trust="untrusted-data",
            matched_terms=matched,
            text=document.text,
        )
        for _, _, document, matched in hits[:limit]
    )
