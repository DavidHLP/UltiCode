import json
from pathlib import Path

import pytest

import authorized_corpus
from authorized_corpus import (
    MANIFEST_PATH,
    PERMISSION,
    answer_with_authorized_evidence,
    load_authorized_corpus,
    search_authorized,
)
from citation_integrity import check_citations
from corpus_manifest import AUTHORIZATION_FIELDS, ManifestError
from retrieval import load_sample_corpus, keyword_search

SUBMISSION = {"id": "sub-1", "status": "Wrong Answer"}


def test_pinned_sample_baseline_is_untouched() -> None:
    """The authorized corpus must not disturb the recorded deterministic baseline."""
    documents = load_sample_corpus()

    assert len(documents) == 3
    # Same ranking as before for a baseline query: default path unchanged.
    baseline = keyword_search("Wrong Answer status", limit=3)
    assert [hit.doc_id for hit in baseline] == [
        hit.doc_id for hit in keyword_search("Wrong Answer status", limit=3)
    ]
    assert all("authorized-" not in hit.doc_id for hit in baseline)


def test_authorized_corpus_loads_and_validates() -> None:
    documents = load_authorized_corpus()

    assert len(documents) == 5
    assert {document.sample_kind for document in documents} == {"synthetic"}


def test_authorized_manifest_declares_the_five_authorization_fields() -> None:
    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    assert len(raw) == 5
    for entry in raw:
        for field in AUTHORIZATION_FIELDS:
            assert entry[field].strip(), (entry["doc_id"], field)
        assert entry["permission"] == PERMISSION
        assert "not licensed third-party" in entry["scope"]


def test_authorized_corpus_never_claims_real_license() -> None:
    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    # Loading this corpus does NOT satisfy DAV-58's licensed-source gate, and the
    # data must not pretend otherwise.
    assert all(entry["sample_kind"] == "synthetic" for entry in raw)
    assert all("not licensed third-party material" in entry["scope"] for entry in raw)


def test_search_over_authorized_corpus_ranks_within_that_corpus() -> None:
    hits = search_authorized("Wrong Answer 状态语义", limit=2)

    assert hits
    assert all(hit.doc_id.startswith("authorized-") for hit in hits)
    assert any("judging-status-semantics" in hit.doc_id for hit in hits)


def test_minimal_end_to_end_answer_cites_and_verifies() -> None:
    answer = answer_with_authorized_evidence(
        "Wrong Answer 状态说明了什么？", SUBMISSION
    )

    assert answer["citations"]
    assert answer["citations_verified"] is True
    checks = check_citations(answer["citations"], load_authorized_corpus())
    assert [check.verdict for check in checks] == ["verified"] * len(checks)
    # Facts stay separate from hypotheses, and no code line is claimed.
    assert all("状态是" in fact for fact in answer["facts"])
    assert any("不能据此定位具体代码行" in item for item in answer["hypotheses"])


def test_answer_refuses_when_no_authorized_evidence_matches() -> None:
    answer = answer_with_authorized_evidence(
        "quantum topology rebalance window", SUBMISSION
    )

    assert answer["citations"] == []
    assert answer["citations_verified"] is False
    assert any("不据此提出具体诊断" in item for item in answer["hypotheses"])


def test_authorized_corpus_fails_closed_on_a_manifest_gap(monkeypatch, tmp_path) -> None:
    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    trimmed = [entry for entry in raw if entry["doc_id"] != raw[0]["doc_id"]]
    monkeypatch.setattr(
        authorized_corpus,
        "MANIFEST_PATH",
        tmp_path / "partial.json",
    )
    (tmp_path / "partial.json").write_text(json.dumps(trimmed), encoding="utf-8")

    with pytest.raises(ManifestError, match="not declared"):
        load_authorized_corpus()
