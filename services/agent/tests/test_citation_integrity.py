import pytest

from citation_integrity import PROVENANCE_FIELDS, all_verified, check_citations
from retrieval import keyword_search, load_sample_corpus

CORPUS = load_sample_corpus()


def _citation_from(query: str) -> dict[str, object]:
    return keyword_search(query, limit=1)[0].as_model_dict()


def test_verbatim_citation_from_the_corpus_verifies() -> None:
    checks = check_citations([_citation_from("Wrong Answer status")], CORPUS)

    assert [check.verdict for check in checks] == ["verified"]
    assert all_verified(checks)


def test_every_retrievable_hit_verifies_against_its_own_source() -> None:
    for document in CORPUS:
        checks = check_citations(
            [
                {
                    "chunk_id": document.chunk_id,
                    "doc_id": document.doc_id,
                    "version": document.version,
                    "source_path": document.source_path,
                    "source_position": document.source_position,
                    "access_scope": document.access_scope,
                    "sample_kind": document.sample_kind,
                    "text": document.text,
                }
            ],
            CORPUS,
        )
        assert checks[0].verdict == "verified", document.doc_id


def test_unknown_chunk_is_rejected() -> None:
    citation = _citation_from("Wrong Answer status")
    citation["chunk_id"] = "made-up:9:1"

    checks = check_citations([citation], CORPUS)

    assert checks[0].verdict == "unknown_source"
    assert not all_verified(checks)


def test_quote_not_present_in_the_source_is_rejected() -> None:
    citation = _citation_from("Wrong Answer status")
    citation["text"] = "the judge accepted this submission"

    assert check_citations([citation], CORPUS)[0].verdict == "text_not_in_source"


@pytest.mark.parametrize("field", [f for f in PROVENANCE_FIELDS if f != "chunk_id"])
def test_any_provenance_drift_is_rejected(field: str) -> None:
    citation = _citation_from("Wrong Answer status")
    citation[field] = "drifted-value"

    checks = check_citations([citation], CORPUS)

    assert checks[0].verdict == "provenance_mismatch"
    assert field in checks[0].detail


def test_sample_marker_drift_is_caught() -> None:
    # Flipping synthetic to real must never pass as provenance agreement.
    citation = _citation_from("Wrong Answer status")
    citation["sample_kind"] = "real"

    assert check_citations([citation], CORPUS)[0].verdict == "provenance_mismatch"


@pytest.mark.parametrize("field", [*PROVENANCE_FIELDS, "text"])
def test_missing_or_non_text_field_is_malformed(field: str) -> None:
    citation = _citation_from("Wrong Answer status")
    citation[field] = ""

    assert check_citations([citation], CORPUS)[0].verdict == "malformed"

    citation = _citation_from("Wrong Answer status")
    citation[field] = 42
    assert check_citations([citation], CORPUS)[0].verdict == "malformed"


def test_empty_or_non_sequence_citations_never_pass() -> None:
    # "No citation" must not read as "all citations verified".
    assert not all_verified(check_citations([], CORPUS))
    assert not all_verified(check_citations("not-a-list", CORPUS))
    assert check_citations("not-a-list", CORPUS)[0].verdict == "malformed"


def test_one_bad_citation_fails_the_whole_gate() -> None:
    good = _citation_from("Wrong Answer status")
    bad = _citation_from("citation source")
    bad["text"] = "invented sentence"

    checks = check_citations([good, bad], CORPUS)

    assert [check.verdict for check in checks] == ["verified", "text_not_in_source"]
    assert not all_verified(checks)
