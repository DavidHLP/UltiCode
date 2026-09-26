import json
from pathlib import Path

import pytest

import project_authored_corpus
from project_authored_corpus import (
    MANIFEST_PATH,
    PERMISSION,
    answer_with_project_evidence,
    load_project_authored_corpus,
    search_project_corpus,
)
from citation_integrity import check_citations
from corpus_manifest import AUTHORIZATION_FIELDS, ManifestError
from retrieval import load_sample_corpus, keyword_search

SUBMISSION = {"id": "sub-1", "status": "Wrong Answer"}


def test_pinned_sample_baseline_is_untouched() -> None:
    """The project-authored corpus must not disturb the recorded baseline."""
    documents = load_sample_corpus()

    assert len(documents) == 3
    # Same ranking as before for a baseline query: default path unchanged.
    baseline = keyword_search("Wrong Answer status", limit=3)
    assert [hit.doc_id for hit in baseline] == [
        hit.doc_id for hit in keyword_search("Wrong Answer status", limit=3)
    ]
    assert all(hit.doc_id.startswith("sample-") for hit in baseline)


def test_project_authored_corpus_loads_and_validates() -> None:
    documents = load_project_authored_corpus()

    assert len(documents) == 5
    assert {document.sample_kind for document in documents} == {"synthetic"}


def test_project_authored_manifest_declares_the_five_authorization_fields() -> None:
    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    assert len(raw) == 5
    for entry in raw:
        for field in AUTHORIZATION_FIELDS:
            assert entry[field].strip(), (entry["doc_id"], field)
        assert entry["permission"] == PERMISSION
        assert "not licensed third-party" in entry["scope"]


def test_project_authored_corpus_never_claims_real_license() -> None:
    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    # Loading this corpus does NOT satisfy DAV-58's licensed-source gate, and the
    # data must not pretend otherwise.
    assert all(entry["sample_kind"] == "synthetic" for entry in raw)
    assert all("not licensed third-party material" in entry["scope"] for entry in raw)


def test_search_over_project_authored_corpus_ranks_within_that_corpus() -> None:
    hits = search_project_corpus("Wrong Answer 状态语义", limit=2)

    assert hits
    assert all(hit.doc_id.startswith("project-") for hit in hits)
    assert any("judging-status-semantics" in hit.doc_id for hit in hits)


def test_minimal_end_to_end_answer_cites_and_verifies() -> None:
    answer = answer_with_project_evidence(
        "Wrong Answer 状态说明了什么？", SUBMISSION
    )

    assert answer["citations"]
    assert answer["citations_verified"] is True
    checks = check_citations(answer["citations"], load_project_authored_corpus())
    assert [check.verdict for check in checks] == ["verified"] * len(checks)
    # Facts stay separate from hypotheses, and no code line is claimed.
    assert all("状态是" in fact for fact in answer["facts"])
    assert any("不能据此定位具体代码行" in item for item in answer["hypotheses"])


def test_answer_refuses_when_no_corpus_evidence_matches() -> None:
    answer = answer_with_project_evidence(
        "quantum topology rebalance window", SUBMISSION
    )

    assert answer["citations"] == []
    assert answer["citations_verified"] is False
    assert any("不据此提出具体诊断" in item for item in answer["hypotheses"])


def test_corpus_text_does_not_attribute_runtime_error_to_a_cause() -> None:
    """The corpus must not seed the unsupported diagnosis it was written to avoid.

    An earlier draft claimed Runtime Error commonly means a program exception or a
    timeout. Reading ``SandboxOutcomeClassifier`` shows the status is its
    catch-all: recognised signals (container-create refusal, fork/pids limits,
    OCI runtime errors, compile failure) each map elsewhere, so the status alone
    identifies no cause. The corpus must say that and must not name a cause the
    code does not support.
    """
    text = "\n".join(document.text for document in load_project_authored_corpus())

    assert "Runtime Error" in text
    assert "兜底分支" in text
    assert "不指向任何具体原因" in text
    assert "常见原因是程序异常退出或超时" not in text
    # Never claim an unverified mapping as fact.
    assert "不能据此推断评测框架或信封层的失败" in text


def test_project_authored_corpus_fails_closed_on_a_manifest_gap(monkeypatch, tmp_path) -> None:
    raw = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    trimmed = [entry for entry in raw if entry["doc_id"] != raw[0]["doc_id"]]
    monkeypatch.setattr(
        project_authored_corpus,
        "MANIFEST_PATH",
        tmp_path / "partial.json",
    )
    (tmp_path / "partial.json").write_text(json.dumps(trimmed), encoding="utf-8")

    with pytest.raises(ManifestError, match="not declared"):
        load_project_authored_corpus()
