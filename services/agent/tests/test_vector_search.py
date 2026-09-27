import asyncio
import importlib.util
from pathlib import Path

import pytest

import vector_search

_module_spec = importlib.util.spec_from_file_location(
    "e2e_vector_comparison",
    Path(__file__).parents[1] / "e2e_vector_comparison.py",
)
assert _module_spec and _module_spec.loader
e2e_vector_comparison = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(e2e_vector_comparison)
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


def _stub_config() -> object:
    """Stand-in for VectorParams so the wiring tests need no qdrant-client."""
    return type("VectorParams", (), {"size": vector_search.VECTOR_SIZE, "distance": "Cosine"})()


class _FakeClient:
    """Stands in for a single-node Qdrant client; records the calls we make."""

    def __init__(self, existing: tuple[str, ...] = ()) -> None:
        self.collection: dict[str, object] = {}
        self.existing = existing
        self.points: list[dict[str, object]] = []
        self.queries: list[tuple[str, int]] = []

    def recreate_collection(self, *, collection_name: str, vectors_config: object) -> None:
        self.collection["name"] = collection_name
        self.collection["vectors_config"] = vectors_config

    def upsert(self, *, collection_name: str, points: list[dict[str, object]]) -> None:
        assert collection_name == self.collection["name"]
        self.points = points

    def create_collection(self, *, collection_name: str, vectors_config: object) -> None:
        if collection_name in self.existing:
            raise ValueError(f"Collection {collection_name!r} already exists!")
        self.existing = (*self.existing, collection_name)
        self.collection["name"] = collection_name
        self.collection["vectors_config"] = vectors_config

    def get_collections(self) -> object:
        # build_index refuses to touch a collection it does not own.
        return type(
            "Collections",
            (),
            {
                "collections": [
                    type("Collection", (), {"name": name})() for name in self.existing
                ]
            },
        )()

    def create_payload_index(self, **_: object) -> None:
        self.indexed_field = "doc_id"

    def query_points(
        self, *, collection_name: str, query: list[float], limit: int, **_: object
    ) -> object:
        self.queries.append((collection_name, limit))
        # Every point scores above the relevance floor unless a test says otherwise.
        best = min(
            range(len(self.points)),
            key=lambda index: sum(
                a * b for a, b in zip(query, self.points[index]["vector"])
            ),
        )
        return type(
            "Result",
            (),
            {
                "points": [
                    type("Point", (), {"payload": self.points[best]["payload"], "score": 0.99})()
                ]
            },
        )()


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

    indexed = vector_search.build_index(
        client, documents, embedder=_FakeEmbedder(), config_factory=_stub_config
    )

    assert indexed == len(documents)
    assert client.collection["name"] == vector_search.COLLECTION
    # A VectorParams object, not a dict: qdrant-client reads a dict as a
    # named-vector mapping and would never create the collection.
    config = client.collection["vectors_config"]
    assert not isinstance(config, dict)
    assert getattr(config, "size", None) == vector_search.VECTOR_SIZE
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
    vector_search.build_index(
        client, load_sample_corpus(), embedder=embedder, config_factory=_stub_config
    )

    found = vector_search.search(client, "status", limit=2, embedder=embedder)

    assert found[0] in {document.doc_id for document in load_sample_corpus()}
    assert client.queries == [(vector_search.COLLECTION, 2)]


def test_embedding_count_mismatch_is_rejected() -> None:
    class _WrongSize(_FakeEmbedder):
        def embed(self, texts: list[str]) -> list[list[float]]:
            return super().embed(texts)[:-1]

    with pytest.raises(ValueError):
        vector_search.build_index(
            _FakeClient(), load_sample_corpus(), embedder=_WrongSize(), config_factory=_stub_config
        )


def test_every_case_declares_required_evidence_for_the_comparison() -> None:
    cases = load_cases()
    assert len(cases) == 30
    assert all(
        case.required_evidence or case.expected_behavior != "cite" for case in cases
    )


def test_index_refuses_to_delete_a_collection_it_does_not_own() -> None:
    client = _FakeClient(existing=(vector_search.COLLECTION,))

    with pytest.raises(ValueError, match="already exists"):
        vector_search.build_index(client, load_sample_corpus(), embedder=_FakeEmbedder(), config_factory=_stub_config)


def test_index_recreates_only_with_an_explicit_opt_in() -> None:
    client = _FakeClient(existing=(vector_search.COLLECTION,))

    indexed = vector_search.build_index(
        client,
        load_sample_corpus(),
        embedder=_FakeEmbedder(),
        allow_recreate=True,
        config_factory=_stub_config,
    )

    assert indexed == len(load_sample_corpus())


def test_vector_search_drops_hits_below_the_relevance_floor() -> None:
    client = _FakeClient()
    embedder = _FakeEmbedder()
    vector_search.build_index(
        client, load_sample_corpus(), embedder=embedder, config_factory=_stub_config
    )

    original = vector_search.search

    class _LowScoreClient(_FakeClient):
        def query_points(self, **kwargs: object) -> object:
            result = _FakeClient.query_points(self, **kwargs)  # type: ignore[arg-type]
            result.points[0].score = 0.01
            return result

    low = _LowScoreClient()
    vector_search.build_index(
        low, load_sample_corpus(), embedder=embedder, config_factory=_stub_config
    )

    assert original(low, "status", limit=2, embedder=embedder) == []


def test_confirmation_set_can_only_be_claimed_once(tmp_path, monkeypatch) -> None:
    """An env opt-in alone does not stop a second run; the marker does."""
    smoke = e2e_vector_comparison

    marker = tmp_path / "holdout-v2.consumed"
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(marker))

    claimed, path = smoke._claim_confirmation_once()
    assert claimed is True
    assert marker.exists()
    assert "holdout-v2" in path

    claimed_again, _ = smoke._claim_confirmation_once()
    assert claimed_again is False


def test_a_concurrent_claim_cannot_overwrite_the_record(tmp_path, monkeypatch) -> None:
    """Exclusive creation, not exists()-then-write: no lost update."""
    smoke = e2e_vector_comparison

    marker = tmp_path / "holdout-v2.consumed"
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(marker))
    # Another process already claimed it.
    marker.write_text("confirmation=holdout-v2.json\nconsumed_at=earlier\n", encoding="utf-8")

    claimed, _ = smoke._claim_confirmation_once()

    assert claimed is False
    # The existing record must be untouched, not rewritten by the loser.
    assert "earlier" in marker.read_text(encoding="utf-8")


def test_claim_fails_closed_when_the_marker_cannot_be_written(tmp_path, monkeypatch) -> None:
    smoke = e2e_vector_comparison
    # The parent is a regular file, so creating the marker directory must fail.
    blocker = tmp_path / "not-a-dir"
    blocker.write_text("x", encoding="utf-8")

    unwritable = blocker / "holdout-v2.consumed"
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(unwritable))

    with pytest.raises(RuntimeError, match="could not record"):
        smoke._claim_confirmation_once()


def test_tied_arms_are_reported_as_a_tie() -> None:
    smoke = e2e_vector_comparison

    assert smoke._arm_outcome(["keyword"]) == "keyword"
    assert smoke._arm_outcome(["vector", "keyword"]) == "tie:keyword+vector"


def test_a_missing_embedding_path_stops_the_run(monkeypatch, capsys, tmp_path) -> None:
    """Without a pinned local snapshot the run cannot be reproducible."""
    smoke = e2e_vector_comparison
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM", "1")
    monkeypatch.setenv("QDRANT_IMAGE", "qdrant/qdrant@sha256:" + "0" * 64)
    # The Qdrant endpoint is validated before the artifact, so point it anywhere
    # loopback-like: the run must stop at the artifact check, not here.
    monkeypatch.setenv("QDRANT_URL", "http://127.0.0.1:6333")
    monkeypatch.setattr(smoke, "EMBED_MODEL_PATH", "")

    # Requested but unrun must not look successful.
    assert smoke.main() == 1
    assert "embed_model_path_required" in capsys.readouterr().out


def test_an_unusable_embedding_path_stops_the_run(monkeypatch, capsys, tmp_path) -> None:
    smoke = e2e_vector_comparison
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM", "1")
    monkeypatch.setenv("QDRANT_IMAGE", "qdrant/qdrant@sha256:" + "0" * 64)
    monkeypatch.setenv("QDRANT_URL", "http://127.0.0.1:6333")
    monkeypatch.setattr(smoke, "EMBED_MODEL_PATH", str(tmp_path / "missing"))

    # A requested run that cannot use a pinned artifact fails closed.
    assert smoke.main() == 1
    assert "embed_artifact_unusable" in capsys.readouterr().out


def test_artifact_identity_tracks_the_local_snapshot(tmp_path) -> None:
    """Two runs can be shown to have used the same weights."""
    import vector_search

    snapshot = tmp_path / "snapshot"
    snapshot.mkdir()
    (snapshot / "model.onnx").write_bytes(b"weights")
    (snapshot / "config.json").write_text("{}")

    first = vector_search.artifact_identity(str(snapshot))
    assert first.startswith("sha256:")
    assert vector_search.artifact_identity(str(snapshot)) == first

    (snapshot / "extra.bin").write_bytes(b"more")
    assert vector_search.artifact_identity(str(snapshot)) != first

    # Same size, different bytes: a name+size digest would collide here.
    before = vector_search.artifact_identity(str(snapshot))
    model_file = snapshot / "model.onnx"
    model_file.write_bytes(b"Weights")  # same length as b"weights"
    assert model_file.stat().st_size == 7
    assert vector_search.artifact_identity(str(snapshot)) != before

    with pytest.raises(ValueError, match="not a directory"):
        vector_search.artifact_identity(str(tmp_path / "nope"))


def test_tied_arms_are_reported_as_a_tie() -> None:
    smoke = e2e_vector_comparison

    assert smoke._arm_outcome(["keyword"]) == "keyword"
    assert smoke._arm_outcome(["vector", "keyword"]) == "tie:keyword+vector"


def test_default_collection_config_is_real_qdrant_vector_params() -> None:
    """qdrant-client 1.19.1 reads a plain dict as a named-vector mapping.

    Skipped in the default runtime, which deliberately has no qdrant-client; the
    wiring tests above inject a non-dict stub so the default suite still covers
    the call shape without the optional dependency.
    """
    models = pytest.importorskip("qdrant_client.models")
    import vector_search

    config = vector_search._collection_config()

    assert isinstance(config, models.VectorParams)
    assert not isinstance(config, dict)
    assert config.size == vector_search.VECTOR_SIZE
    assert config.distance == models.Distance.COSINE


def test_artifact_is_validated_before_the_optional_dependencies(monkeypatch, capsys) -> None:
    """A bad artifact must stop the run with or without qdrant installed."""
    smoke = e2e_vector_comparison
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM", "1")
    monkeypatch.setenv("QDRANT_IMAGE", "qdrant/qdrant@sha256:" + "0" * 64)
    monkeypatch.setattr(smoke, "EMBED_MODEL_PATH", "/nonexistent/snapshot")

    assert smoke.main() == 1
    assert "embed_artifact_unusable" in capsys.readouterr().out


@pytest.mark.parametrize(
    "image",
    [
        "qdrant/qdrant@sha256:",
        "qdrant/qdrant@sha256:abc",
        "qdrant/qdrant@sha256:" + "z" * 64,
        "qdrant/qdrant@sha256:" + "a" * 63,
        "qdrant/qdrant:latest",
        "qdrant/qdrant",
    ],
)
def test_a_malformed_image_identity_cannot_pass_the_guard(monkeypatch, capsys, image) -> None:
    """A substring check let an empty or non-hex digest through."""
    smoke = e2e_vector_comparison
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM", "1")
    monkeypatch.setenv("QDRANT_IMAGE", image)

    assert smoke.main() == 1
    assert "unpinned_qdrant_image" in capsys.readouterr().out


def test_a_well_formed_digest_gets_past_the_image_guard(monkeypatch, capsys) -> None:
    smoke = e2e_vector_comparison
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM", "1")
    monkeypatch.setenv("QDRANT_IMAGE", "qdrant/qdrant@sha256:" + "a" * 64)
    monkeypatch.setattr(smoke, "EMBED_MODEL_PATH", "")

    # Past the image guard: it stops at the next precondition instead.
    assert smoke.main() == 1
    assert "embed_model_path_required" in capsys.readouterr().out


def test_an_empty_confirmation_fixture_is_rejected_before_claiming(
    monkeypatch, capsys, tmp_path
) -> None:
    """An empty fixture must not consume the one-shot set or report OK."""
    import pathlib as _pathlib

    smoke = e2e_vector_comparison
    # tmp_path, never the repository's data directory: a test that writes there
    # leaves the checkout dirty and risks the fixture being committed.
    work = _pathlib.Path(smoke.CONFIRMATION_CASES_PATH).parent
    marker = _pathlib.Path(tmp_path) / "holdout-empty-marker"
    for stale in (marker,):
        if stale.exists():
            stale.unlink()
    empty_cases = _pathlib.Path(tmp_path) / "holdout-empty.json"
    empty_cases.write_text("[]", encoding="utf-8")

    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM", "1")
    monkeypatch.setenv("QDRANT_IMAGE", "qdrant/qdrant@sha256:" + "a" * 64)
    monkeypatch.setenv("ULTICODE_EMBED_MODEL_PATH", str(_pathlib.Path(tmp_path)))
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(marker))
    monkeypatch.setattr(smoke, "CONFIRMATION_CASES_PATH", empty_cases)
    monkeypatch.setattr(smoke, "EMBED_MODEL_PATH", str(_pathlib.Path(tmp_path)))

    assert smoke.main() == 1
    assert "confirmation_fixture_invalid" in capsys.readouterr().out
    # The one-shot marker must not have been consumed by a rejected fixture.
    assert not marker.exists()


def test_a_directory_at_the_marker_path_is_a_configuration_error(tmp_path, monkeypatch) -> None:
    smoke = e2e_vector_comparison
    as_directory = tmp_path / "marker-dir"
    as_directory.mkdir()
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(as_directory))

    with pytest.raises(RuntimeError, match="not a regular claim record"):
        smoke._claim_confirmation_once()


def test_an_empty_development_split_is_rejected(monkeypatch, capsys, tmp_path) -> None:
    """An empty selection stage must not be reported as a completed comparison."""
    import keyword_evaluation

    smoke = e2e_vector_comparison
    real_load = keyword_evaluation.load_cases

    def fake_load(path=None):
        # No path: the default dataset. Empty, so `development` has no rows.
        if path is None:
            return ()
        return real_load(smoke.CONFIRMATION_CASES_PATH)

    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM", "1")
    monkeypatch.setenv("QDRANT_IMAGE", "qdrant/qdrant@sha256:" + "a" * 64)
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(tmp_path / "marker"))
    monkeypatch.setattr(smoke, "EMBED_MODEL_PATH", str(tmp_path))
    monkeypatch.setattr(smoke, "load_cases", fake_load)

    assert smoke.main() == 1
    assert "split_fixture_invalid" in capsys.readouterr().out


def test_an_unrelated_regular_file_at_the_marker_is_a_configuration_error(
    tmp_path, monkeypatch
) -> None:
    """Only our own claim record may count as 'already consumed'."""
    smoke = e2e_vector_comparison
    unrelated = tmp_path / "unrelated.txt"
    unrelated.write_text("some other file\n", encoding="utf-8")
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(unrelated))

    with pytest.raises(RuntimeError, match="not this harness's claim record"):
        smoke._claim_confirmation_once()


def test_our_own_claim_record_still_reports_already_consumed(tmp_path, monkeypatch) -> None:
    smoke = e2e_vector_comparison
    record = tmp_path / "holdout-v2.consumed"
    record.write_text(
        f"confirmation={smoke.CONFIRMATION_CASES_PATH.name}\nconsumed_at=earlier\n",
        encoding="utf-8",
    )
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(record))

    claimed, _ = smoke._claim_confirmation_once()

    assert claimed is False


def test_a_file_merely_mentioning_the_confirmation_name_is_not_a_claim(
    tmp_path, monkeypatch
) -> None:
    """A loose substring match would accept an unrelated file."""
    smoke = e2e_vector_comparison
    lookalike = tmp_path / "lookalike.txt"
    lookalike.write_text(
        f"notes: {smoke.CONFIRMATION_CASES_PATH.name} was evaluated at some point\n",
        encoding="utf-8",
    )
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(lookalike))

    with pytest.raises(RuntimeError, match="not this harness's claim record"):
        smoke._claim_confirmation_once()


def test_a_claim_record_without_a_timestamp_is_rejected(tmp_path, monkeypatch) -> None:
    smoke = e2e_vector_comparison
    partial = tmp_path / "partial"
    partial.write_text(
        f"confirmation={smoke.CONFIRMATION_CASES_PATH.name}\n", encoding="utf-8"
    )
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(partial))

    with pytest.raises(RuntimeError, match="not this harness's claim record"):
        smoke._claim_confirmation_once()


def test_a_claim_record_with_a_duplicated_field_is_rejected(tmp_path, monkeypatch) -> None:
    """Last-write-wins would turn an unrelated first value into a match."""
    smoke = e2e_vector_comparison
    record = tmp_path / "duplicated"
    record.write_text(
        "confirmation=unrelated\n"
        f"confirmation={smoke.CONFIRMATION_CASES_PATH.name}\n"
        "consumed_at=earlier\n",
        encoding="utf-8",
    )
    monkeypatch.setenv("ULTICODE_VECTOR_CONFIRM_MARKER", str(record))

    with pytest.raises(RuntimeError, match="not this harness's claim record"):
        smoke._claim_confirmation_once()


def test_a_non_loopback_qdrant_needs_its_own_opt_in(monkeypatch) -> None:
    """Creating a collection and upserting are writes the confirmation opt-in cannot authorise."""
    smoke = e2e_vector_comparison
    monkeypatch.setenv("QDRANT_URL", "http://qdrant.internal.example:6333")
    monkeypatch.delenv(smoke.REMOTE_QDRANT_OPT_IN, raising=False)

    with pytest.raises(ValueError, match="not loopback"):
        smoke.qdrant_url()

    monkeypatch.setenv(smoke.REMOTE_QDRANT_OPT_IN, "1")
    assert smoke.qdrant_url() == "http://qdrant.internal.example:6333"


def test_loopback_qdrant_needs_no_opt_in(monkeypatch) -> None:
    smoke = e2e_vector_comparison
    monkeypatch.setenv("QDRANT_URL", "http://127.0.0.1:6333")
    monkeypatch.delenv(smoke.REMOTE_QDRANT_OPT_IN, raising=False)

    assert smoke.qdrant_url() == "http://127.0.0.1:6333"
