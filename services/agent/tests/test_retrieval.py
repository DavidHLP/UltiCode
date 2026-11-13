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


def test_context_fragments_are_bounded_positioned_and_run_local():
    from retrieval import context_documents, MAX_SOURCE_CHARS
    content = "题" * 8000
    selection = {"problem": {"reference": "7", "content": content, "truncated": True},
                 "comments": [{"kind": "forum_comment", "reference": "comment-1", "content": "comment"}]}
    documents = context_documents(selection, "run-1")
    problem = documents[:-1]
    assert "".join(document.text for document in problem) == content
    assert all(0 < len(document.text) <= MAX_SOURCE_CHARS for document in documents)
    assert len({document.chunk_id for document in documents}) == len(documents)
    assert problem[0].source_position == "chars 1-1200 (selected text truncated)"
    assert problem[-1].source_position == "chars 7201-8000 (selected text truncated)"
    assert all(document.access_scope == "session-authorized-current-run" for document in documents)
    assert documents[-1].sample_kind == "forum-comment"
    assert context_documents(selection, "run-2")[0].chunk_id != documents[0].chunk_id
    assert keyword_search("missing", documents=documents) == ()
    assert keyword_search("synthetic", documents=context_documents({}, "run-1")) == ()


def test_catalog_id_retrieves_unseen_content_without_expanding_scope():
    from retrieval import context_documents
    documents = context_documents({"comments": [{"kind": "forum_comment", "reference": "comment-1",
                                                "content": "Use two pointers from opposite ends."}]}, "run-1")
    hit, = keyword_search(documents[0].doc_id, documents=documents)
    assert hit.text == documents[0].text
    assert hit.doc_id == documents[0].doc_id
    assert keyword_search("forum-comment:other:0", documents=()) == ()
