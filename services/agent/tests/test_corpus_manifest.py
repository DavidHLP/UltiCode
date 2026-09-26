import json
from pathlib import Path

import pytest

from corpus_manifest import (
    AUTHORIZATION_FIELDS,
    DOCUMENT_BINDING_FIELDS,
    MANIFEST_PATH,
    MANIFEST_PROVENANCE_FIELD,
    REQUIRED_FIELDS,
    ManifestError,
    assert_manifest_covers,
    load_manifest,
)
from retrieval import load_sample_corpus

CORPUS = load_sample_corpus()
CORPUS_IDS = {document.doc_id for document in CORPUS}


def _entry() -> dict[str, object]:
    return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))[0]


def _write(tmp_path: Path, entries: object, *, name: str = "manifest.json") -> Path:
    path = tmp_path / name
    path.write_text(
        entries if isinstance(entries, str) else json.dumps(entries), encoding="utf-8"
    )
    return path


def test_checked_in_manifest_is_complete_and_binds_the_corpus() -> None:
    entries = load_manifest()

    assert_manifest_covers(entries, CORPUS)
    assert {entry.doc_id for entry in entries} == CORPUS_IDS
    assert {entry.sample_kind for entry in entries} == {"synthetic"}


def test_authorization_fields_are_exactly_davs_five() -> None:
    # Guards against the schema silently growing beyond the exit criterion.
    assert AUTHORIZATION_FIELDS == (
        "permission",
        "scope",
        "version",
        "source_position",
        "model_input_projection",
    )
    assert set(REQUIRED_FIELDS) == set(AUTHORIZATION_FIELDS) | set(
        DOCUMENT_BINDING_FIELDS
    )
    assert MANIFEST_PROVENANCE_FIELD == "source_trust"


@pytest.mark.parametrize("field", AUTHORIZATION_FIELDS)
def test_each_authorization_field_is_mandatory(tmp_path: Path, field: str) -> None:
    incomplete = _entry()
    del incomplete[field]

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [incomplete]))


@pytest.mark.parametrize("field", REQUIRED_FIELDS + (MANIFEST_PROVENANCE_FIELD,))
def test_blank_field_is_rejected(tmp_path: Path, field: str) -> None:
    blanked = _entry()
    blanked[field] = "   "

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [blanked]))


@pytest.mark.parametrize("field", DOCUMENT_BINDING_FIELDS)
def test_binding_fields_are_mandatory_too(tmp_path: Path, field: str) -> None:
    incomplete = _entry()
    del incomplete[field]

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [incomplete]))


def test_real_entry_marked_synthetic_permission_is_rejected(tmp_path: Path) -> None:
    forged = _entry()
    forged["doc_id"] = "real-1"
    forged["sample_kind"] = "real"
    forged["permission"] = "agent-authored-synthetic"

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [forged]))


def test_real_entry_with_any_claim_is_accepted_but_is_not_evidence(tmp_path: Path) -> None:
    # The gate checks completeness and consistency, not authorization. A claim is
    # still a claim; the test name records that so nobody mistakes this for proof.
    claimed = _entry()
    claimed["doc_id"] = "real-1"
    claimed["sample_kind"] = "real"
    claimed["permission"] = "claimed-by-author"

    entries = load_manifest(_write(tmp_path, [claimed]))

    assert entries[0].sample_kind == "real"


def test_unknown_sample_kind_is_rejected(tmp_path: Path) -> None:
    bad = _entry()
    bad["sample_kind"] = "maybe"

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [bad]))


def test_invalid_json_is_rejected_with_a_clear_error(tmp_path: Path) -> None:
    with pytest.raises(ManifestError, match="not valid JSON"):
        load_manifest(_write(tmp_path, "{not json"))


def test_empty_or_non_list_manifest_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, []))
    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, {"doc_id": "x"}))


def test_duplicate_declaration_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ManifestError, match="declared twice"):
        load_manifest(_write(tmp_path, [_entry(), _entry()]))


def test_retrievable_but_undeclared_document_fails_closed() -> None:
    partial = tuple(entry for entry in load_manifest() if entry.doc_id != "sample-status-only")

    with pytest.raises(ManifestError, match="not declared"):
        assert_manifest_covers(partial, CORPUS)


def test_manifest_declaring_a_non_retrievable_document_fails() -> None:
    entries = load_manifest()
    extra = entries[0].__class__(
        **{
            **{field: getattr(entries[0], field) for field in entries[0].__dataclass_fields__},
            "doc_id": "ghost-doc",
        }
    )

    with pytest.raises(ManifestError, match="not retrievable"):
        assert_manifest_covers((*entries, extra), CORPUS)


def test_manifest_metadata_must_match_the_document_it_describes(tmp_path: Path) -> None:
    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    raw[0]["source_position"] = "lines 1-999"
    drifted = load_manifest(_write(tmp_path, raw))

    with pytest.raises(ManifestError, match="source_position"):
        assert_manifest_covers(drifted, CORPUS)


def test_version_drift_is_detected(tmp_path: Path) -> None:
    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    raw[0]["version"] = "v99"
    drifted = load_manifest(_write(tmp_path, raw))

    with pytest.raises(ManifestError, match="version"):
        assert_manifest_covers(drifted, CORPUS)


def test_corpus_loader_fails_closed_when_the_manifest_is_incomplete(
    monkeypatch, tmp_path
) -> None:
    """Regression on the wiring, not just the helper.

    Testing ``assert_manifest_covers`` alone would stay green if someone removed
    the call from the corpus load path, leaving undeclared material retrievable.
    """
    import corpus_manifest
    from retrieval import load_sample_corpus

    partial = [
        entry
        for entry in json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        if entry["doc_id"] != "sample-status-only"
    ]
    monkeypatch.setattr(
        corpus_manifest, "MANIFEST_PATH", _write(tmp_path, partial, name="partial.json")
    )

    with pytest.raises(ManifestError, match="not declared"):
        load_sample_corpus()


def test_corpus_loader_fails_closed_when_a_field_is_blank(monkeypatch, tmp_path) -> None:
    import corpus_manifest
    from retrieval import load_sample_corpus

    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    raw[0]["scope"] = ""
    monkeypatch.setattr(
        corpus_manifest, "MANIFEST_PATH", _write(tmp_path, raw, name="blank.json")
    )

    with pytest.raises(ManifestError, match="scope"):
        load_sample_corpus()
