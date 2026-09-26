import json
from pathlib import Path

import pytest

from corpus_manifest import (
    MANIFEST_PATH,
    REQUIRED_FIELDS,
    ManifestError,
    covers_corpus,
    load_manifest,
)
from retrieval import load_sample_corpus

CORPUS_IDS = {document.doc_id for document in load_sample_corpus()}


def _entry() -> dict[str, object]:
    return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))[0]


def _write(tmp_path: Path, entries: list[object]) -> Path:
    path = tmp_path / "manifest.json"
    path.write_text(json.dumps(entries), encoding="utf-8")
    return path


def test_checked_in_manifest_declares_every_corpus_document() -> None:
    entries = load_manifest()

    assert covers_corpus(entries, CORPUS_IDS)
    assert {entry.sample_kind for entry in entries} == {"synthetic"}


def test_every_required_field_is_present_and_non_empty() -> None:
    for entry in load_manifest():
        for field in REQUIRED_FIELDS:
            value = getattr(entry, field)
            assert isinstance(value, str) and value.strip(), (entry.doc_id, field)


@pytest.mark.parametrize("field", REQUIRED_FIELDS)
def test_manifest_entry_missing_any_field_is_rejected(tmp_path: Path, field: str) -> None:
    incomplete = _entry()
    del incomplete[field]

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [incomplete]))


@pytest.mark.parametrize("field", REQUIRED_FIELDS)
def test_blank_field_is_rejected_too(tmp_path: Path, field: str) -> None:
    blanked = _entry()
    blanked[field] = "   "

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [blanked]))


def test_real_source_cannot_claim_a_synthetic_permission(tmp_path: Path) -> None:
    # Public API visibility is not a license: this must never pass.
    forged = _entry()
    forged["doc_id"] = "real-1"
    forged["sample_kind"] = "real"
    forged["permission"] = "agent-authored-synthetic"

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [forged]))


def test_real_source_with_explicit_permission_is_accepted(tmp_path: Path) -> None:
    authorized = _entry()
    authorized["doc_id"] = "real-1"
    authorized["sample_kind"] = "real"
    authorized["permission"] = "owner-authorized-2026-09-26"
    authorized["scope"] = "owner-owned study notes; ingest and model egress allowed"

    entries = load_manifest(_write(tmp_path, [authorized]))

    assert entries[0].sample_kind == "real"


def test_unknown_sample_kind_is_rejected(tmp_path: Path) -> None:
    bad = _entry()
    bad["sample_kind"] = "maybe"

    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [bad]))


def test_empty_or_non_list_manifest_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, []))
    with pytest.raises(ManifestError):
        load_manifest(_write(tmp_path, [{"doc_id": "x"}]))


def test_manifest_must_cover_the_retrievable_corpus_exactly() -> None:
    entries = load_manifest()

    assert not covers_corpus(entries, CORPUS_IDS | {"undeclared-doc"})
    assert not covers_corpus(entries, CORPUS_IDS - {"sample-status-only"})
