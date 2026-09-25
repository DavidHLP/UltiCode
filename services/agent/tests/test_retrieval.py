import pytest

from retrieval import MAX_QUERY_CHARS, keyword_search, load_sample_corpus


def test_keyword_search_returns_source_bound_hit() -> None:
    hits = keyword_search("Wrong Answer")

    assert hits
    hit = hits[0]
    assert hit.access_scope == "agent-authored-synthetic"
    assert hit.sample_kind == "synthetic"
    assert hit.source_trust == "untrusted-data"
    assert "Wrong Answer" in hit.text


def test_keyword_search_returns_empty_for_unmatched_query() -> None:
    assert keyword_search("quantum topology") == ()


def test_corpus_documents_are_bounded_and_marked_synthetic() -> None:
    documents = load_sample_corpus()

    assert len(documents) == 3
    assert all(document.sample_kind == "synthetic" for document in documents)
    assert all(document.access_scope == "agent-authored-synthetic" for document in documents)
    assert all(
        "Provenance: agent-authored synthetic example; not a real UltiCode submission, DTO, or user-authorized material."
        in document.text
        for document in documents
    )
    assert all(document.source_position.startswith("lines ") for document in documents)


@pytest.mark.parametrize("query", ["", "   ", "x" * (MAX_QUERY_CHARS + 1), 7, None])
def test_invalid_queries_are_rejected(query: object) -> None:
    with pytest.raises(ValueError, match="invalid search query"):
        keyword_search(query)
