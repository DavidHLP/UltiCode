"""Bounded keyword retrieval for the U02 synthetic read-only slice."""

from __future__ import annotations

import re
from dataclasses import asdict, dataclass
from pathlib import Path

from corpus_manifest import assert_manifest_covers, load_manifest


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


def _validate_requirement(require_text: str | None) -> str | None:
    if require_text is None:
        return None
    if not isinstance(require_text, str) or not require_text.strip():
        raise ValueError("invalid search requirement")
    return require_text.casefold()


def load_sample_corpus(
    manifest: tuple[object, ...] | None = None,
) -> tuple[SourceDocument, ...]:
    # Resolving a symlinked root would adopt an external directory as trusted,
    # so every child would then pass the per-file containment check below.
    if _CORPUS_DIR.is_symlink():
        raise ValueError("sample corpus root must not be a symlink")
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
    corpus = tuple(documents)
    # Fail closed: a retrievable document that the authorization manifest does not
    # declare must never reach retrieval. The manifest also has to agree with the
    # document it claims to describe. A caller that already parsed the bytes it will
    # later identify passes them here, so validation and that identity cannot come
    # from two different reads of the file.
    assert_manifest_covers(manifest if manifest is not None else load_manifest(), corpus)
    return corpus


def keyword_search(
    query: object,
    *,
    limit: int = MAX_RESULTS,
    documents: tuple[SourceDocument, ...] | None = None,
    require_text: str | None = None,
) -> tuple[SourceHit, ...]:
    """Rank documents by shared query terms.

    ``documents`` defaults to the pinned sample corpus, so the recorded
    deterministic baseline is unchanged; an authorised corpus passes its own
    documents instead of duplicating the ranking logic.

    ``require_text`` restricts the corpus *before* ranking and before the result
    limit. A caller with a hard requirement — the submission status, for instance
    — needs that order: filtering afterwards would rank first and then drop
    documents the requirement excludes, so a lower-ranked document that satisfies
    it could be pushed out by higher-ranked ones that do not.
    """
    if isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= MAX_RESULTS:
        raise ValueError("invalid search limit")
    query_text = _validate_query(query)
    query_terms = _terms(query_text)
    if not query_terms:
        return ()
    requirement = _validate_requirement(require_text)

    hits: list[tuple[int, str, SourceDocument, tuple[str, ...]]] = []
    for document in documents if documents is not None else load_sample_corpus():
        haystack = document.text.casefold()
        if requirement is not None and requirement not in haystack:
            continue
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
