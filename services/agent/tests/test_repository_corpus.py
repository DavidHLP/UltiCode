import json
import shutil

import pytest

from corpus_manifest import ManifestError
from keyword_evaluation import evaluate_case_records, load_cases
from repository_corpus import MANIFEST, ROOT, SOURCES, load_repository_corpus
from retrieval import keyword_search


@pytest.fixture
def corpus_root(tmp_path):
    for name in (*SOURCES, "LICENSE", MANIFEST):
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / name, path)
    return tmp_path


def test_licensed_sources_and_keyword_hit_miss(corpus_root):
    documents = load_repository_corpus(corpus_root)
    assert len(documents) == 5
    assert all(d.sample_kind == "real" and d.access_scope == "repository-public" for d in documents)
    hits = keyword_search("HttpOnly", documents=documents)
    assert hits and any(hit.source_path == "docs/REFERENCE.md" for hit in hits)
    assert all(hit.source_position.startswith("lines ") for hit in hits)
    assert not keyword_search("zzzzmissingrepositoryterm", documents=documents)


@pytest.mark.parametrize("source", ["LICENSE", "docs/REFERENCE.md"])
def test_changed_license_or_source_fails_closed(corpus_root, source):
    path = corpus_root / source
    path.write_text(path.read_text() + "\nChanged.")
    with pytest.raises(ManifestError):
        load_repository_corpus(corpus_root)


def test_case_records_use_the_supplied_repository_corpus(corpus_root):
    documents = load_repository_corpus(corpus_root)
    cases = load_cases(text=json.dumps([{
        "case_id": "repository-dev-auth", "split": "development",
        "query": "HttpOnly", "required_evidence": ["repository-reference"],
        "answerable": True, "expected_behavior": "cite",
        "allowed_behavior": "Explain the documented cookie flow with source evidence.",
        "forbidden_behavior": "Claim that a live authentication request was verified.",
    }]), documents=documents)
    record, = evaluate_case_records(cases, limit=3, documents=documents)
    assert record.retrieval_hit and record.citation_traceable
    assert record.citation_support == record.answer_completion == "deferred"
    assert record.observed_behavior == "not_measured"


def test_repository_development_cases_are_predeclared_and_answer_level_deferred(corpus_root):
    documents = load_repository_corpus(corpus_root)
    path = ROOT / "services/agent/data/repository_development_cases.json"
    annotations = json.loads(path.read_text())
    assert len(annotations) == 20
    assert {row["category"] for row in annotations} == {
        "normal_call", "no_tool", "clarification", "tool_failure", "no_evidence",
    }
    assert all(row["precondition"] and row["user_scope"] == "repository-public"
               for row in annotations)
    cases = load_cases(path, documents=documents)
    assert all(case.split == "development" for case in cases)
    records = evaluate_case_records(cases, limit=3, documents=documents)
    assert len(records) == 20
    assert all(record.observed_behavior == "not_measured"
               and record.citation_support == record.answer_completion == "deferred"
               for record in records)


@pytest.mark.parametrize("field,value", [
    ("source_path", "../../outside.md"),
    ("source_position", "lines 1-999999"),
    ("sample_kind", "synthetic"),
    ("permission", "project-authored-for-u02"),
    ("content_digest", "sha256:" + "0" * 64),
])
def test_invalid_manifest_fails_closed(corpus_root, field, value):
    path = corpus_root / MANIFEST
    entries = json.loads(path.read_text())
    entries[0][field] = value
    path.write_text(json.dumps(entries))
    with pytest.raises(ManifestError):
        load_repository_corpus(corpus_root)


def test_symlinked_source_directory_is_rejected(corpus_root):
    docs = corpus_root / "docs"
    moved = corpus_root / "moved"
    docs.rename(moved)
    docs.symlink_to(moved, target_is_directory=True)
    with pytest.raises(ManifestError):
        load_repository_corpus(corpus_root)
