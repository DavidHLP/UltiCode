"""U02.c keyword-vs-vector comparison over the same corpus and question set.

Opt-in and evaluation-only: it needs a single-node Qdrant service and the
optional ``eval`` dependency group, and it never touches the UltiCode stack,
user data, or a real model. Output is fixed labels and counts only.

Run with a disposable single-node Qdrant, for example:

    docker run --rm -p 6333:6333 qdrant/qdrant:latest
    cd services/agent
    uv sync --locked --group eval
    QDRANT_URL=http://localhost:6333 uv run python e2e_vector_comparison.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from keyword_evaluation import load_cases, retrieval_outcome
from retrieval import keyword_search, load_sample_corpus
from vector_search import COLLECTION, EMBED_MODEL, FastembedEmbedder, build_index, qdrant_url, search

COUNTS = ("matched", "extra_hits", "missed", "false_positive")


def _tally(expected: set[str], actual: set[str]) -> dict[str, int]:
    tally = {name: 0 for name in COUNTS}
    tally[retrieval_outcome(expected, actual)] += 1
    return tally


def _arm(cases: tuple, limit: int, retrieve) -> dict[str, dict[str, int]]:
    result = {
        split: {name: 0 for name in COUNTS}
        for split in ("development", "holdout")
    }
    for case in cases:
        bucket = result[case.split]
        for name, value in _tally(set(case.required_evidence), set(retrieve(case.query))).items():
            bucket[name] += value
    return result


def main() -> int:
    try:
        from qdrant_client import QdrantClient  # noqa: PLC0415 - evaluation-only
    except ImportError:
        print("FAIL reason=missing_eval_dependency")
        return 1

    cases = load_cases()
    documents = load_sample_corpus()
    client = QdrantClient(url=qdrant_url())
    embedder = FastembedEmbedder()
    indexed = build_index(client, documents, embedder=embedder)

    def report(limit: int, arm: str, result: dict[str, dict[str, int]]) -> None:
        for split in ("development", "holdout"):
            counts = result[split]
            print(
                f"limit={limit} arm={arm} split={split} matched={counts['matched']} "
                f"extra={counts['extra_hits']} missed={counts['missed']} "
                f"false_positive={counts['false_positive']}"
            )

    for limit in (1, 3):
        keyword = _arm(
            cases, limit, lambda q: [hit.doc_id for hit in keyword_search(q, limit=limit)]
        )
        vector = _arm(
            cases, limit, lambda q: search(client, q, limit=limit, embedder=embedder)
        )
        report(limit, "keyword", keyword)
        report(limit, "vector", vector)
        delta = sum(
            vector[split]["matched"] - keyword[split]["matched"] for split in keyword
        )
        print(
            f"limit={limit} single_variable=retrieval_limit "
            f"delta_matched_vector_minus_keyword={delta:+d}"
        )

    print(
        f"OK comparison corpus=agent-authored-synthetic docs={indexed} "
        f"model={EMBED_MODEL} store=qdrant collection={COLLECTION} cases={len(cases)}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001 - fixed status label only
        print(f"FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
