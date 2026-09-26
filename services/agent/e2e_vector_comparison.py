"""U02.c keyword-vs-vector comparison over the same corpus and question set.

Opt-in and evaluation-only: it needs a single-node Qdrant service, the optional
``eval`` dependency group, and a pinned container image. It never touches the
UltiCode stack, user data, or a real model. Output is fixed labels and counts
only.

Protocol:

1. Both arms are compared on the **development** split across the candidate
   retrieval limits, and the limit is chosen there.
2. The original **holdout** split is reported as *contaminated*: it was already
   observed during an exploratory run, so it is continuity evidence only and can
   never be a clean confirmation.
3. **holdout2** is the never-seen confirmation set. Its expectations were written
   from the corpus text and committed before any retrieval ran on it, and it is
   evaluated once, at the limit chosen in step 1.

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
CONTAMINATED_SPLIT = "holdout"
CONFIRMATION_SPLIT = "holdout2"


def _tally(cases: tuple[KeywordCase, ...], retrieve) -> dict[str, int]:
    tally = {name: 0 for name in COUNTS}
    for case in cases:
        expected = set(case.required_evidence)
        tally[retrieval_outcome(expected, set(retrieve(case)))] += 1
    return tally


def _report(label: str, counts: dict[str, int], total: int) -> None:
    print(
        f"{label} total={total} matched={counts['matched']} extra={counts['extra_hits']} "
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
    contaminated = tuple(case for case in cases if case.split == CONTAMINATED_SPLIT)
    confirmation = tuple(case for case in cases if case.split == CONFIRMATION_SPLIT)

    client = QdrantClient(url=qdrant_url())
    embedder = FastembedEmbedder()
    indexed = build_index(client, load_sample_corpus(), embedder=embedder)

    def keyword(query: str, limit: int) -> list[str]:
        return [hit.doc_id for hit in keyword_search(query, limit=limit)]

    def vector(query: str, limit: int) -> list[str]:
        return search(client, query, limit=limit, embedder=embedder)

    arms = (("keyword", keyword), ("vector", vector))

    # Step 1: choose the retrieval limit on the development split only.
    scores: dict[tuple[int, str], int] = {}
    for limit in CANDIDATE_LIMITS:
        for arm, retrieve in arms:
            counts = _tally(development, lambda case: retrieve(case.query, limit))
            scores[(limit, arm)] = counts["matched"]
            _report(
                f"stage=select split=development limit={limit} arm={arm}",
                counts,
                len(development),
            )
    best = max(
        CANDIDATE_LIMITS,
        key=lambda limit: (
            max(scores[(limit, arm)] for arm, _ in arms),
            -limit,
        ),
    )
    winning_arm = max((arm for arm, _ in arms), key=lambda arm: scores[(best, arm)])
    print(
        f"stage=select chosen_limit={best} chosen_arm={winning_arm} basis=development_only"
    )

    # Step 2: continuity only. This split was already observed once.
    for arm, retrieve in arms:
        counts = _tally(contaminated, lambda case: retrieve(case.query, best))
        _report(
            f"stage=contaminated split={CONTAMINATED_SPLIT} limit={best} arm={arm} "
            f"basis=already_observed",
            counts,
            len(contaminated),
        )

    # Step 3: the never-seen confirmation set, run once at the chosen limit.
    for arm, retrieve in arms:
        counts = _tally(confirmation, lambda case: retrieve(case.query, best))
        _report(
            f"stage=confirm split={CONFIRMATION_SPLIT} limit={best} arm={arm} "
            f"basis=predeclared_never_seen",
            counts,
            len(confirmation),
        )
    print("stage=confirm note=holdout2_evaluated_once_no_retuning")

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
