"""Evaluation-only vector retrieval for the U02 keyword-vs-vector comparison.

This module exists to produce comparison evidence, not to serve the agent: the
read-only main path keeps using :func:`retrieval.keyword_search`. Qdrant and the
embedding runtime are imported lazily so ``uv sync --locked`` for the default
runtime stays unchanged, and the caller supplies a single-node Qdrant URL.

Scope limits from the U02 plan: one embedding model, one vector store, no
reranker, no second index, no migration of the working keyword path.
"""

from __future__ import annotations

import os
from typing import Protocol

from retrieval import SourceDocument

COLLECTION = "u02-eval"
EMBED_MODEL = "BAAI/bge-small-en-v1.5"
VECTOR_SIZE = 384
#: Cosine relevance floor: below this the nearest point is not evidence.
MIN_SCORE = 0.35


class Embedder(Protocol):
    def embed(self, texts: list[str]) -> list[list[float]]: ...


class FastembedEmbedder:
    """One small ONNX embedding model. Loaded once per comparison run."""

    def __init__(self, model_name: str = EMBED_MODEL) -> None:
        from fastembed import TextEmbedding  # noqa: PLC0415 - evaluation-only

        self._model = TextEmbedding(model_name=model_name)

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [vector.tolist() for vector in self._model.embed(texts)]


def qdrant_url() -> str:
    url = os.environ.get("QDRANT_URL")
    if not url:
        raise ValueError("QDRANT_URL must point at the single-node Qdrant service")
    return url


def build_index(
    client: object,
    documents: tuple[SourceDocument, ...],
    *,
    embedder: Embedder | None = None,
    allow_recreate: bool = False,
) -> int:
    """Upsert one point per source document and return how many were indexed.

    ``recreate_collection`` drops an existing collection, so it only runs on a
    collection this harness owns *and* the caller opted into. A pre-existing
    collection on a shared instance is never silently deleted.
    """
    vectors = (embedder or FastembedEmbedder()).embed([doc.text for doc in documents])
    if len(vectors) != len(documents):
        raise ValueError("embedding count did not match the corpus")
    config = {"size": VECTOR_SIZE, "distance": "Cosine"}
    if allow_recreate:
        client.recreate_collection(collection_name=COLLECTION, vectors_config=config)  # type: ignore[attr-defined]
    else:
        # Non-destructive create: a check-then-recreate sequence could still
        # delete a collection another process created in between.
        try:
            client.create_collection(collection_name=COLLECTION, vectors_config=config)  # type: ignore[attr-defined]
        except Exception as error:  # noqa: BLE001 - qdrant raises its own conflict type
            if "already exist" in str(error).lower():
                raise ValueError(
                    f"collection {COLLECTION!r} already exists; pass allow_recreate=True "
                    "only on a disposable instance"
                ) from None
            raise
    client.upsert(  # type: ignore[attr-defined]
        collection_name=COLLECTION,
        points=[
            {
                "id": index,
                "vector": vector,
                "payload": {
                    "doc_id": document.doc_id,
                    "version": document.version,
                    "source_position": document.source_position,
                    "access_scope": document.access_scope,
                },
            }
            for index, (document, vector) in enumerate(zip(documents, vectors))
        ],
    )
    client.create_payload_index(  # type: ignore[attr-defined]
        collection_name=COLLECTION,
        field_name="doc_id",
        field_schema="keyword",
    )
    return len(documents)


def search(
    client: object,
    query: str,
    *,
    limit: int,
    embedder: Embedder | None = None,
    min_score: float = MIN_SCORE,
) -> list[str]:
    """Return retrieved doc ids above ``min_score``, closest first.

    Without a relevance floor the vector arm always returns its nearest point,
    so a ``no_evidence`` query could never score as no evidence and the
    keyword/vector comparison would be decided by that artefact.
    """
    vector = (embedder or FastembedEmbedder()).embed([query])[0]
    hits = client.query_points(  # type: ignore[attr-defined]
        collection_name=COLLECTION,
        query=vector,
        limit=limit,
        with_payload=True,
    ).points
    return [str(hit.payload["doc_id"]) for hit in hits if hit.score >= min_score]
