"""U02.c keyword-vs-vector comparison over the same corpus and question set.

Opt-in and evaluation-only: it needs a single-node Qdrant service, the optional
``eval`` dependency group, and a pinned container image. It never touches the
UltiCode stack, user data, or a real model. Output is fixed labels and counts
only.

Protocol, because the holdout set must stay isolated:

1. Both arms are compared on the **development** split across the candidate
   retrieval limits, and the limit is chosen there.
2. The **holdout** split is then evaluated exactly once, at the chosen limit.
3. Nothing is re-tuned after seeing holdout output.

Scope: the corpus is the 3-document agent-authored synthetic sample, not the
authorized 5-10 document corpus, so a "no gain" result is evidence about this
slice only.

Run with a disposable single-node Qdrant, for example:

    docker run --rm -p 6333:6333 qdrant/qdrant@sha256:<digest>
    cd services/agent
    uv sync --locked --group eval
    QDRANT_IMAGE=qdrant/qdrant@sha256:<digest> \\
    QDRANT_URL=http://localhost:6333 uv run python e2e_vector_comparison.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from keyword_evaluation import KeywordCase, load_cases, retrieval_outcome
from retrieval import keyword_search, load_sample_corpus
from vector_search import (
    COLLECTION,
    EMBED_MODEL,
    FastembedEmbedder,
    build_index,
    qdrant_url,
    search,
)

COUNTS = ("matched", "extra_hits", "missed", "false_positive")
CANDIDATE_LIMITS = (1, 3)


def _tally(cases: tuple[KeywordCase, ...], retrieve) -> dict[str, int]:
    tally = {name: 0 for name in COUNTS}
    for case in cases:
        expected = set(case.required_evidence)
        tally[retrieval_outcome(expected, set(retrieve(case)))] += 1
    return tally


def _report(label: str, counts: dict[str, int]) -> None:
    print(
        f"{label} matched={counts['matched']} extra={counts['extra_hits']} "
        f"missed={counts['missed']} false_positive={counts['false_positive']}"
    )


def main() -> int:
    image = os.environ.get("QDRANT_IMAGE")
    if not image or "@sha256:" not in image:
        # `latest` silently changes between runs, which makes the evidence
        # irreproducible.
        print("FAIL reason=unpinned_qdrant_image")
        return 1
    try:
        from qdrant_client import QdrantClient  # noqa: PLC0415 - evaluation-only
    except ImportError:
        print("FAIL reason=missing_eval_dependency")
        return 1

    cases = load_cases()
    development = tuple(case for case in cases if case.split == "development")
    holdout = tuple(case for case in cases if case.split == "holdout")
    client = QdrantClient(url=qdrant_url())
    embedder = FastembedEmbedder()
    indexed = build_index(client, load_sample_corpus(), embedder=embedder)

    def keyword(query: str, limit: int) -> list[str]:
        return [hit.doc_id for hit in keyword_search(query, limit=limit)]

    def vector(query: str, limit: int) -> list[str]:
        return search(client, query, limit=limit, embedder=embedder)

    # Step 1: choose the retrieval limit on the development split only.
    development_scores: dict[int, int] = {}
    for limit in CANDIDATE_LIMITS:
        for arm, retrieve in (("keyword", keyword), ("vector", vector)):
            counts = _tally(development, lambda case: retrieve(case.query, limit))
            development_scores[(limit, arm)] = counts["matched"]
            _report(
                f"stage=select split=development limit={limit} arm={arm} total={len(development)}",
                counts,
            )
    best = max(
        CANDIDATE_LIMITS,
        key=lambda limit: (
            max(development_scores[(limit, arm)] for arm in ("keyword", "vector")),
            -limit,
        ),
    )
    winning_arm = max(
        ("keyword", "vector"), key=lambda arm: development_scores[(best, arm)]
    )
    print(
        f"stage=select chosen_limit={best} chosen_arm={winning_arm} "
        f"basis=development_only"
    )

    # Step 2: the holdout split runs once, at the limit chosen above.
    for arm, retrieve in (("keyword", keyword), ("vector", vector)):
        counts = _tally(holdout, lambda case: retrieve(case.query, best))
        _report(
            f"stage=confirm split=holdout limit={best} arm={arm} total={len(holdout)}",
            counts,
        )
    print("stage=confirm note=holdout_evaluated_once_no_retuning")

    print(
        f"OK comparison corpus=agent-authored-synthetic docs={indexed} cases={len(cases)} "
        f"embed_model={EMBED_MODEL} store=qdrant image={image} collection={COLLECTION} "
        f"scope=synthetic_slice_not_authorized_corpus"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001 - fixed status label only
        print(f"FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
