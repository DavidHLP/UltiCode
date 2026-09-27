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
from pathlib import Path
from typing import Callable, Protocol

from retrieval import SourceDocument

COLLECTION = "u02-eval"
EMBED_MODEL = "BAAI/bge-small-en-v1.5"
#: FastEmbed 0.8.1 accepts **kwargs but its download_model call forwards only
#: ``local_files_only`` and ``specific_model_path``, so a ``revision=`` argument is
#: silently ignored. The supported way to pin the artifact is therefore a
#: pre-downloaded snapshot directory, which this module identifies by checksum.
EMBED_MODEL_PATH = os.environ.get("ULTICODE_EMBED_MODEL_PATH", "")


def artifact_identity(model_path: str) -> str:
    """Content checksum of a local snapshot, so two runs can be shown identical.

    Every regular file's bytes are hashed in sorted relative-path order with a
    path delimiter, so a same-size edit to the weights changes the digest. Names
    and sizes alone would collide by construction, which is exactly the case this
    has to catch.
    """
    import hashlib

    root = Path(model_path).resolve()
    if not root.is_dir():
        raise ValueError(f"embedding artifact is not a directory: {root}")
    digest = hashlib.sha256()
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        # Lengths, not delimiters: snapshot bytes are arbitrary, so a NUL inside a
        # file could otherwise make two different layouts hash the same.
        name = str(path.relative_to(root)).encode("utf-8")
        digest.update(len(name).to_bytes(8, "big"))
        digest.update(name)
        size = 0
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1 << 20), b""):
                size += len(chunk)
                digest.update(chunk)
        digest.update(size.to_bytes(8, "big"))
    return f"sha256:{digest.hexdigest()}"


VECTOR_SIZE = 384
#: Cosine relevance floor: below this the nearest point is not evidence.
MIN_SCORE = 0.35


class Embedder(Protocol):
    def embed(self, texts: list[str]) -> list[list[float]]: ...


class FastembedEmbedder:
    """One small ONNX embedding model. Loaded once per comparison run."""

    def __init__(
        self, model_name: str = EMBED_MODEL, model_path: str | None = None
    ) -> None:
        from fastembed import TextEmbedding  # noqa: PLC0415 - evaluation-only

        # `specific_model_path` is the one pin FastEmbed 0.8.1 actually forwards.
        kwargs = {"specific_model_path": model_path} if model_path else {}
        self._model = TextEmbedding(model_name=model_name, **kwargs)

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [vector.tolist() for vector in self._model.embed(texts)]


LOOPBACK_HOSTS = frozenset({"127.0.0.1", "localhost", "::1", "[::1]"})
#: Set only when the endpoint really is a disposable Qdrant you own.
REMOTE_QDRANT_OPT_IN = "ULTICODE_QDRANT_ALLOW_REMOTE"


def qdrant_url() -> str:
    url = os.environ.get("QDRANT_URL")
    if not url:
        raise ValueError("QDRANT_URL must point at the single-node Qdrant service")
    from urllib.parse import urlparse

    host = (urlparse(url).hostname or "").lower()
    # Creating a collection, upserting points and a payload index are writes; a
    # confirmation opt-in does not authorise mutating an arbitrary endpoint.
    if host not in LOOPBACK_HOSTS and os.environ.get(REMOTE_QDRANT_OPT_IN) != "1":
        raise ValueError(
            f"Qdrant target {host!r} is not loopback; set {REMOTE_QDRANT_OPT_IN}=1 "
            "only for a disposable instance you own"
        )
    return url


def _collection_config() -> object:
    """Build a real VectorParams.

    With qdrant-client 1.19.1 a plain dict is read as a *named vector* mapping,
    so ``size``/``distance`` are treated as vector names and the call fails on a
    real client even though a permissive fake accepts it.
    """
    from qdrant_client.models import Distance, VectorParams  # noqa: PLC0415

    return VectorParams(size=VECTOR_SIZE, distance=Distance.COSINE)


def build_index(
    client: object,
    documents: tuple[SourceDocument, ...],
    *,
    embedder: Embedder | None = None,
    allow_recreate: bool = False,
    config_factory: Callable[[], object] | None = None,
) -> int:
    """Upsert one point per source document and return how many were indexed.

    ``recreate_collection`` drops an existing collection, so it only runs on a
    collection this harness owns *and* the caller opted into. A pre-existing
    collection on a shared instance is never silently deleted.
    """
    vectors = (embedder or FastembedEmbedder()).embed([doc.text for doc in documents])
    if len(vectors) != len(documents):
        raise ValueError("embedding count did not match the corpus")
    # Injected so the wiring can be tested without the optional qdrant-client,
    # while a real run always uses the real VectorParams builder.
    config = (config_factory or _collection_config)()
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
