import pytest

import vector_search
from keyword_evaluation import load_cases, retrieval_outcome
from retrieval import load_sample_corpus


class _FakeEmbedder:
    """Deterministic stand-in for the small ONNX embedding model."""

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [
            [float(len(text) % 7), *([1.0] * (vector_search.VECTOR_SIZE - 1))]
            for text in texts
        ]


class _FakePoint:
    def __init__(self, payload: dict[str, object]) -> None:
        self.payload = payload


class _FakeClient:
    """Stands in for a single-node Qdrant client; records the calls we make."""

    def __init__(self) -> None:
        self.collection: dict[str, object] = {}
        self.points: list[dict[str, object]] = []
        self.queries: list[tuple[str, int]] = []

    def recreate_collection(self, *, collection_name: str, vectors_config: object) -> None:
        self.collection["name"] = collection_name
        self.collection["vectors_config"] = vectors_config

    def upsert(self, *, collection_name: str, points: list[dict[str, object]]) -> None:
        assert collection_name == self.collection["name"]
        self.points = points

    def create_payload_index(self, **_: object) -> None:
        self.indexed_field = "doc_id"

    def query_points(
        self, *, collection_name: str, query: list[float], limit: int, **_: object
    ) -> object:
        self.queries.append((collection_name, limit))
        best = min(
            range(len(self.points)),
            key=lambda index: sum(
                a * b for a, b in zip(query, self.points[index]["vector"])
            ),
        )
        return type("Result", (), {"points": [_FakePoint(self.points[best]["payload"])]})()


def test_both_arms_are_judged_by_one_classifier() -> None:
    # Otherwise the comparison measures the scoring code, not the retriever.
    assert retrieval_outcome(set(), set()) == "matched"
    assert retrieval_outcome(set(), {"a"}) == "false_positive"
    assert retrieval_outcome({"a"}, {"a"}) == "matched"
    assert retrieval_outcome({"a"}, {"a", "b"}) == "extra_hits"
    assert retrieval_outcome({"a"}, {"b"}) == "missed"


def test_qdrant_url_must_be_configured(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("QDRANT_URL", raising=False)
    with pytest.raises(ValueError):
        vector_search.qdrant_url()

    monkeypatch.setenv("QDRANT_URL", "http://localhost:6333")
    assert vector_search.qdrant_url() == "http://localhost:6333"


def test_index_round_trip_keeps_provenance_payload() -> None:
    client = _FakeClient()
    documents = load_sample_corpus()

    indexed = vector_search.build_index(client, documents, embedder=_FakeEmbedder())

    assert indexed == len(documents)
    assert client.collection["name"] == vector_search.COLLECTION
    assert client.collection["vectors_config"] == {
        "size": vector_search.VECTOR_SIZE,
        "distance": "Cosine",
    }
    assert set(client.points[0]["payload"]) == {
        "doc_id",
        "version",
        "source_position",
        "access_scope",
    }
    assert [point["payload"]["doc_id"] for point in client.points] == [
        document.doc_id for document in documents
    ]


def test_search_returns_payload_doc_ids_and_passes_the_limit() -> None:
    client = _FakeClient()
    embedder = _FakeEmbedder()
    vector_search.build_index(client, load_sample_corpus(), embedder=embedder)

    found = vector_search.search(client, "status", limit=2, embedder=embedder)

    assert found[0] in {document.doc_id for document in load_sample_corpus()}
    assert client.queries == [(vector_search.COLLECTION, 2)]


def test_embedding_count_mismatch_is_rejected() -> None:
    class _WrongSize(_FakeEmbedder):
        def embed(self, texts: list[str]) -> list[list[float]]:
            return super().embed(texts)[:-1]

    with pytest.raises(ValueError):
        vector_search.build_index(_FakeClient(), load_sample_corpus(), embedder=_WrongSize())


def test_every_case_declares_required_evidence_for_the_comparison() -> None:
    cases = load_cases()
    assert len(cases) == 40
    assert all(
        case.required_evidence or case.expected_behavior != "cite" for case in cases
    )
