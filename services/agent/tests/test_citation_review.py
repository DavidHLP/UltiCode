import json

import pytest

from citation_review import (
    VERDICT_KEYS,
    VerdictError,
    build_worksheet,
    load_verdicts,
    summarize,
    worksheet_to_json,
)
from project_authored_corpus import answer_with_project_evidence, load_project_authored_corpus

SUBMISSION = {"id": "sub-1", "status": "Wrong Answer"}
ANSWER = answer_with_project_evidence("Wrong Answer 状态说明了什么？", SUBMISSION)


def test_worksheet_pairs_each_citation_with_fragment_and_claim() -> None:
    items = build_worksheet(ANSWER, load_project_authored_corpus())

    assert items
    assert len(items) == len(ANSWER["citations"])
    for item in items:
        assert item.chunk_id and item.doc_id
        assert item.quote
        assert item.claim
        assert item.source_position.startswith("lines")
        # Verdicts start unset: the module never judges for the reviewer.
        assert set(item.verdicts) == set(VERDICT_KEYS)
        assert all(value is None for value in item.verdicts.values())


def test_worksheet_serialises_with_unset_verdicts() -> None:
    payload = json.loads(worksheet_to_json(build_worksheet(ANSWER, load_project_authored_corpus())))

    assert payload
    for entry in payload:
        assert entry["verdicts"] == {key: None for key in VERDICT_KEYS}


def _verdict_file(tmp_path, chunk_ids, *, supports=True, complete=True):
    selected = chunk_ids if complete else chunk_ids[:-1]
    path = tmp_path / "verdicts.json"
    path.write_text(
        json.dumps(
            [
                {
                    "chunk_id": chunk_id,
                    "verdicts": {key: supports for key in VERDICT_KEYS},
                    "note": "",
                }
                for chunk_id in selected
            ]
        ),
        encoding="utf-8",
    )
    return path


def test_verdicts_must_cover_every_reviewed_citation(tmp_path) -> None:
    items = build_worksheet(ANSWER, load_project_authored_corpus())
    partial = _verdict_file(tmp_path, [item.chunk_id for item in items], complete=False)

    with pytest.raises(VerdictError, match="no verdict"):
        load_verdicts(partial, items)


def test_verdicts_must_be_booleans(tmp_path) -> None:
    items = build_worksheet(ANSWER, load_project_authored_corpus())
    path = tmp_path / "verdicts.json"
    path.write_text(
        json.dumps(
            [
                {"chunk_id": item.chunk_id, "verdicts": {key: "yes" for key in VERDICT_KEYS}}
                for item in items
            ]
        ),
        encoding="utf-8",
    )

    with pytest.raises(VerdictError, match="booleans"):
        load_verdicts(path, items)


def test_gate_fails_closed_on_an_unsupported_citation(tmp_path) -> None:
    items = build_worksheet(ANSWER, load_project_authored_corpus())
    path = _verdict_file(tmp_path, [item.chunk_id for item in items], supports=False)

    verdicts = load_verdicts(path, items)
    result = summarize(items, verdicts)

    assert result["gate_passed"] is False
    assert len(result["not_supported"]) == len(items)


def test_gate_passes_only_when_every_citation_is_fully_supported(tmp_path) -> None:
    items = build_worksheet(ANSWER, load_project_authored_corpus())
    path = _verdict_file(tmp_path, [item.chunk_id for item in items])

    result = summarize(items, load_verdicts(path, items))

    assert result["gate_passed"] is True
    assert result["reviewed"] == len(items)
    assert all(result["counts"][key] == len(items) for key in VERDICT_KEYS)
    assert result["not_supported"] == []


def test_partial_verdicts_are_counted_separately(tmp_path) -> None:
    """exists but not supports must not be rounded up to a pass."""
    items = build_worksheet(ANSWER, load_project_authored_corpus())
    chunk_ids = [item.chunk_id for item in items]
    path = tmp_path / "verdicts.json"
    path.write_text(
        json.dumps(
            [
                # The citation exists, but the fragment does not support the claim.
                {
                    "chunk_id": chunk_id,
                    "verdicts": {"exists": True, "supports": False, "derivable": False},
                    "note": "fragment is about provenance, not about this status",
                }
                for chunk_id in chunk_ids
            ]
        ),
        encoding="utf-8",
    )

    result = summarize(items, load_verdicts(path, items))

    assert result["counts"]["exists"] == len(items)
    assert result["counts"]["supports"] == 0
    assert result["gate_passed"] is False


def test_empty_worksheet_never_passes() -> None:
    assert summarize((), {})["gate_passed"] is False
