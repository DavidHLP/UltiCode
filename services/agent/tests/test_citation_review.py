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
from corpus_manifest import load_manifest
from project_authored_corpus import (
    MANIFEST_PATH,
    answer_with_project_evidence,
    load_project_authored_corpus,
)

SUBMISSION = {"id": "sub-1", "status": "Wrong Answer"}
ANSWER = answer_with_project_evidence("Wrong Answer 状态说明了什么？", SUBMISSION)
CLAIM = ANSWER["hypotheses"][0]
DOCUMENTS = load_project_authored_corpus()
MANIFEST = load_manifest(MANIFEST_PATH)


def _worksheet(citations=None, *, claim=CLAIM):
    return build_worksheet(
        claim=claim,
        citations=ANSWER["citations"] if citations is None else citations,
        documents=DOCUMENTS,
        manifest=MANIFEST,
    )


def _verdict_file(tmp_path, chunk_ids, *, supports=True, complete=True):
    selected = chunk_ids if complete else chunk_ids[:-1]
    path = tmp_path / "verdicts.json"
    path.write_text(
        json.dumps(
            [
                {"chunk_id": chunk_id, "verdicts": {k: supports for k in VERDICT_KEYS}, "note": ""}
                for chunk_id in selected
            ]
        ),
        encoding="utf-8",
    )
    return path


def test_claim_must_be_stated_explicitly() -> None:
    # The data does not record which conclusion a citation supports, so the
    # worksheet refuses to guess by position.
    with pytest.raises(ValueError):
        _worksheet(claim="")


def test_worksheet_uses_manifest_permission_not_access_scope() -> None:
    items = _worksheet()

    assert items
    for item in items:
        assert item.permission == MANIFEST[0].permission or item.permission in {
            entry.permission for entry in MANIFEST
        }
        assert item.permission != item.access_scope
        assert item.permission_scope


def test_worksheet_survives_a_citation_that_lies_about_its_identity() -> None:
    forged = dict(ANSWER["citations"][0])
    forged["doc_id"] = "sample-something-else"
    forged["version"] = "v99"

    items = _worksheet([forged])

    assert items[0].integrity_verdict in {
        "provenance_mismatch",
        "unknown_source",
    }
    # Identity is resolved from the corpus, not from the citation's claim.
    assert items[0].doc_id.startswith("project-")


def test_worksheet_refuses_a_quote_that_is_not_in_the_source() -> None:
    tampered = dict(ANSWER["citations"][0])
    tampered["text"] = "a sentence the corpus never contained"

    items = _worksheet([tampered])

    assert items[0].integrity_verdict == "text_not_in_source"


def test_worksheet_keeps_the_full_quote() -> None:
    items = _worksheet()
    long_citation = dict(ANSWER["citations"][0])
    long_citation["text"] = "x" * 500

    item = _worksheet([long_citation])[0]

    assert len(item.quote) == 500
    assert items[0].quote == ANSWER["citations"][0]["text"]


def test_worksheet_serialises_with_unset_verdicts() -> None:
    payload = json.loads(worksheet_to_json(_worksheet()))

    assert payload
    for entry in payload:
        assert entry["verdicts"] == {key: None for key in VERDICT_KEYS}
        assert entry["integrity_verdict"] == "verified"
        assert entry["permission"] != entry["access_scope"]


def test_verdicts_must_cover_every_reviewed_citation(tmp_path) -> None:
    two = [ANSWER["citations"][0], ANSWER["citations"][0]]
    items = _worksheet(two)
    assert len(items) == 2
    partial = _verdict_file(tmp_path, [item.chunk_id for item in items], complete=False)

    with pytest.raises(VerdictError, match="no verdict"):
        load_verdicts(partial, items)


def test_verdicts_must_be_booleans(tmp_path) -> None:
    items = _worksheet()
    path = tmp_path / "verdicts.json"
    path.write_text(
        json.dumps(
            [{"chunk_id": item.chunk_id, "verdicts": {k: "yes" for k in VERDICT_KEYS}} for item in items]
        ),
        encoding="utf-8",
    )

    with pytest.raises(VerdictError, match="booleans"):
        load_verdicts(path, items)


def test_gate_fails_closed_on_an_unsupported_citation(tmp_path) -> None:
    items = _worksheet()
    path = _verdict_file(tmp_path, [item.chunk_id for item in items], supports=False)

    result = summarize(items, load_verdicts(path, items))

    assert result["gate_passed"] is False
    assert len(result["not_supported"]) == len(items)


def test_gate_passes_only_when_every_citation_is_fully_supported(tmp_path) -> None:
    items = _worksheet()
    path = _verdict_file(tmp_path, [item.chunk_id for item in items])

    result = summarize(items, load_verdicts(path, items))

    assert result["gate_passed"] is True
    assert result["reviewed"] == len(items)
    assert all(result["counts"][key] == len(items) for key in VERDICT_KEYS)
    assert result["not_supported"] == []
    assert result["integrity_unverified"] == []


def test_human_approval_cannot_override_a_failed_integrity_gate(tmp_path) -> None:
    tampered = dict(ANSWER["citations"][0])
    tampered["text"] = "a sentence the corpus never contained"
    items = _worksheet([tampered])
    # A reviewer marks everything as supported; the gate must still refuse.
    path = _verdict_file(tmp_path, [items[0].chunk_id], supports=True)

    result = summarize(items, load_verdicts(path, items))

    assert items[0].integrity_verdict == "text_not_in_source"
    assert result["gate_passed"] is False
    assert result["integrity_unverified"] == [items[0].chunk_id]


def test_partial_verdicts_are_counted_separately(tmp_path) -> None:
    items = _worksheet()
    path = tmp_path / "verdicts.json"
    path.write_text(
        json.dumps(
            [
                {
                    "chunk_id": item.chunk_id,
                    "verdicts": {"exists": True, "supports": False, "derivable": False},
                    "note": "fragment is about provenance, not this status",
                }
                for item in items
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
