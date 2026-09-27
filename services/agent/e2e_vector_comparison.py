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

    docker run --rm -p 127.0.0.1:6333:6333 qdrant/qdrant@sha256:<digest>
    cd services/agent
    uv sync --locked --group eval
    QDRANT_IMAGE=qdrant/qdrant@sha256:<digest> \\
    QDRANT_URL=http://localhost:6333 QDRANT_ALLOW_RECREATE=1 \\
    ULTICODE_EMBED_MODEL_PATH=<snapshot-dir> \\
    ULTICODE_VECTOR_CONFIRM=1 uv run python e2e_vector_comparison.py
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "src"))

from keyword_evaluation import (
    CONFIRMATION_CASES_PATH,
    _CASES_PATH as DEFAULT_CASES_PATH,
    KeywordCase,
    load_cases,
    retrieval_outcome,
)
from retrieval import keyword_search, load_sample_corpus
from vector_search import (
    COLLECTION,
    EMBED_MODEL,
    MIN_SCORE,
    EMBED_MODEL_PATH,
    artifact_identity,
    FastembedEmbedder,
    build_index,
    qdrant_url,
    search,
)

def _consumption_marker() -> Path:
    """Durable record that the one-shot confirmation set has been used.

    Deliberately outside the checkout: a marker inside the repository is either
    committed or gitignored, and a gitignored one disappears with every fresh
    clone, which would let the confirmation set be evaluated again while still
    claiming to be never seen.

    Scope: the guarantee is **per marker location**, so by default it covers one
    workspace on one machine only. Spreading runs across machines or runners
    needs the operator to point ``ULTICODE_VECTOR_CONFIRM_MARKER`` at a shared
    durable path they control (a mounted volume or an external store). This code
    cannot verify that a configured path is shared or durable, so it reports only
    whether a path was configured, never that cross-runner protection exists.
    """
    override = os.environ.get("ULTICODE_VECTOR_CONFIRM_MARKER")
    if override:
        return Path(override)
    state_home = Path(
        os.environ.get("XDG_STATE_HOME", str(Path.home() / ".local" / "state"))
    )
    return state_home / "ulticode" / "holdout-v2.consumed"


def _is_our_claim_record(marker: Path) -> bool:
    """True only for a record this harness wrote for this confirmation set."""
    try:
        raw_lines = marker.read_text(encoding="utf-8").splitlines()
    except OSError:
        return False
    # This harness writes exactly two assignments. An extra field, a comment or
    # any non-assignment line means the file is not our record.
    if any(not line.strip() or "=" not in line for line in raw_lines):
        return False
    lines = [line.split("=", 1) for line in raw_lines]
    fields: dict[str, str] = {}
    for key, value in lines:
        if key in fields:
            # A record this harness writes never repeats a field; last-write-wins
            # would let an unrelated first value be overwritten into a match.
            return False
        fields[key] = value
    if set(fields) != {"confirmation", "consumed_at"}:
        return False
    if fields.get("confirmation") != CONFIRMATION_CASES_PATH.name:
        return False
    # The harness always writes an ISO timestamp; "garbage" is not a record it
    # could have produced, so it must not disable the confirmation run.
    raw_consumed_at = fields.get("consumed_at", "")
    try:
        consumed_at = datetime.fromisoformat(raw_consumed_at)
    except ValueError:
        return False
    # The writer emits `datetime.now(timezone.utc).isoformat()`, so the text has to
    # equal it exactly. A bare date, a naive timestamp or another offset parses but
    # could not come from this writer — and neither could the ISO week-date or
    # space-separated `Z` spellings `fromisoformat` also accepts, nor a padded
    # value. The comparison deliberately does not trim: accepting whitespace would
    # let a file this writer never produced read as our claim and silently skip the
    # requested one-shot run.
    if consumed_at.isoformat() != raw_consumed_at:
        return False
    return consumed_at.tzinfo is not None and consumed_at.utcoffset() == timedelta(0)


def _claim_confirmation_once() -> tuple[bool, str]:
    """Claim the confirmation set, or refuse.

    The claim is created with exclusive access: an ``exists()`` check followed by
    a write would let two concurrent runs both believe they own the single-use
    set. If the marker cannot be written the run aborts rather than proceeding
    repeatably.
    """
    marker = _consumption_marker()
    record = (
        f"confirmation={CONFIRMATION_CASES_PATH.name}\n"
        f"consumed_at={datetime.now(timezone.utc).isoformat()}\n"
    )
    try:
        # Kept out of the exclusive-create try: mkdir also raises
        # FileExistsError, which must not be read as "already consumed".
        marker.parent.mkdir(parents=True, exist_ok=True)
    except OSError as error:
        raise RuntimeError(f"could not record the confirmation claim: {error}") from None
    try:
        # Exclusive creation: the loser of a race gets FileExistsError.
        with marker.open("x", encoding="utf-8") as handle:
            handle.write(record)
    except FileExistsError:
        # A directory (or any non-record) at the marker path is a bad
        # configuration, not a previous claim; reporting "already consumed" would
        # silently prevent the confirmation run.
        if marker.is_dir() or not (marker.is_file() and not marker.is_symlink()):
            # A symlink, FIFO, socket or device also raises FileExistsError;
            # treating those as a previous claim would skip the run and exit 0.
            raise RuntimeError(
                f"marker path is not a regular claim record: {marker}"
            ) from None
        # A regular file only counts as our claim if it parses as the record this
        # harness writes. A loose substring test would accept any file that
        # happens to mention the confirmation set.
        if not _is_our_claim_record(marker):
            raise RuntimeError(
                f"marker exists but is not this harness's claim record: {marker}"
            ) from None
        return False, str(marker)
    except OSError as error:
        raise RuntimeError(f"could not record the confirmation claim: {error}") from None
    return True, str(marker)


COUNTS = (
    "matched",
    "extra_hits",
    "missed",
    "false_positive",
    "refused_with_evidence",
    "refused_without_evidence",
)
CANDIDATE_LIMITS = (1, 3)
CONTAMINATED_SPLIT = "holdout"
CONFIRMATION_SPLIT = "holdout2"


def _tally(cases: tuple[KeywordCase, ...], retrieve) -> dict[str, int]:
    """Tally outcomes per behaviour class.

    A ``refuse`` case is never scored through the citable-evidence path: fetching
    its declared document is exactly the fabrication risk, and counting it as a
    match would both reward the arm and contradict the per-case evaluation, which
    marks any such retrieval as a risk.
    """
    tally = {name: 0 for name in COUNTS}
    for case in cases:
        expected = set(case.required_evidence)
        actual = set(retrieve(case))
        if case.expected_behavior == "refuse":
            tally["refused_with_evidence" if actual else "refused_without_evidence"] += 1
            continue
        tally[retrieval_outcome(expected, actual)] += 1
    return tally


def _corpus_digest(documents: tuple[object, ...]) -> str:
    """Content digest of the captured corpus, so the evidence names it exactly."""
    import hashlib

    digest = hashlib.sha256()
    for document in documents:
        digest.update(str(getattr(document, "doc_id", "")).encode("utf-8"))
        digest.update(str(getattr(document, "version", "")).encode("utf-8"))
        digest.update(str(getattr(document, "text", "")).encode("utf-8"))
        digest.update(b"\0")
    return f"sha256:{digest.hexdigest()[:16]}"


def _arm_outcome(winners: list[str]) -> str:
    """A tie must not be reported as a unique winner."""
    if len(winners) == 1:
        return winners[0]
    return "tie:" + "+".join(sorted(winners))


def _artifact_unchanged(model_path: str, before: str) -> bool:
    """True only when the snapshot still hashes to the preflight digest."""
    return artifact_identity(model_path) == before


def _cases_digest_from_loaded(cases: tuple[KeywordCase, ...]) -> str:
    """Digest derived from the cases actually scored.

    Reading the file again at output time would report bytes that were never
    evaluated if the file changed during the run.
    """
    import hashlib

    digest = hashlib.sha256()
    for case in sorted(cases, key=lambda item: item.case_id):
        # Every field, with a length prefix so no boundary can be forged by
        # concatenation.
        for value in (
            case.case_id,
            case.split,
            case.query,
            "\x1f".join(case.required_evidence),
            str(case.answerable),
            case.expected_behavior,
            case.allowed_behavior,
            case.forbidden_behavior,
        ):
            encoded = value.encode("utf-8")
            digest.update(str(len(encoded)).encode("ascii"))
            digest.update(b":")
            digest.update(encoded)
        digest.update(b"\x1e")
    return f"sha256:{digest.hexdigest()[:16]}"


def _report(label: str, counts: dict[str, int], total: int) -> None:
    print(
        f"{label} total={total} matched={counts['matched']} extra={counts['extra_hits']} "
        f"missed={counts['missed']} false_positive={counts['false_positive']} "
        # Refusal outcomes must be visible: every split contains such cases, and a
        # forbidden retrieval is exactly what a confirmation run must be able to show.
        f"refused_with_evidence={counts['refused_with_evidence']} "
        f"refused_without_evidence={counts['refused_without_evidence']}"
    )


def main() -> int:
    image = os.environ.get("QDRANT_IMAGE")
    # A bare "@sha256:" or a non-hex suffix would pass a substring check and let
    # the run record a non-immutable image identity.
    if not image or not re.fullmatch(r"[^\s@]+@sha256:[0-9a-f]{64}", image):
        # `latest` silently changes between runs, which makes the evidence
        # irreproducible.
        print("FAIL reason=unpinned_qdrant_image")
        return 1
    # The confirmation set is single-use. Without an explicit opt-in this run
    # must not touch it, because re-running would contaminate it while still
    # printing a clean-looking confirmation.
    confirm_opt_in = os.environ.get("ULTICODE_VECTOR_CONFIRM") == "1"
    if not confirm_opt_in:
        print("SKIP reason=confirmation_requires_opt_in")
        return 0
    # Loaded from its own versioned file so the routine suite never touches it.
    confirmation = load_cases(CONFIRMATION_CASES_PATH)
    cases = load_cases()
    development = tuple(case for case in cases if case.split == "development")
    contaminated = tuple(case for case in cases if case.split == CONTAMINATED_SPLIT)
    # Captured now: a later edit to the file must not change the reported digest
    # for expectations that were already scored.
    development_digest = _cases_digest_from_loaded(development)
    contaminated_digest = _cases_digest_from_loaded(contaminated)
    confirmation_digest = _cases_digest_from_loaded(confirmation)
    model_path = EMBED_MODEL_PATH.strip()
    if not model_path:
        # Reporting "unpinned" is honest but not reproducible: the same model
        # name can resolve to different weights, so scores and the relevance
        # threshold could not be compared with any later run. Checked first so an
        # unreproducible run does not even install the optional dependencies.
        # The run was requested, so a missing pinned artifact is a failure, not
        # a skip: exiting 0 would report a comparison that never happened.
        print("FAIL reason=embed_model_path_required")
        return 1
    try:
        # The checksum is the artifact identity actually used by this run. Checked
        # in the preflight so a bad path costs nothing, with or without the
        # optional dependencies installed.
        embed_identity = artifact_identity(model_path)
    except ValueError as error:
        # The run was requested, so exiting 0 would report success for a
        # comparison that never happened.
        print(f"FAIL reason=embed_artifact_unusable detail={error}")
        return 1
    # Checked in the preflight: an empty or mislabelled fixture would burn the
    # one-shot set and could still report a comparison with zero cases, and this
    # check must not require the optional dependency to be installed.
    if not confirmation or any(case.split != CONFIRMATION_SPLIT for case in confirmation):
        print(
            f"FAIL reason=confirmation_fixture_invalid loaded={len(confirmation)} "
            f"expected_split={CONFIRMATION_SPLIT}"
        )
        return 1
    # The selection and continuity stages must be non-empty too: an empty or
    # drifted split would otherwise be reported as a completed comparison.
    for name, rows, expected in (
        ("development", development, "development"),
        ("contaminated", contaminated, CONTAMINATED_SPLIT),
    ):
        if not rows or any(case.split != expected for case in rows):
            print(
                f"FAIL reason=split_fixture_invalid split={name} loaded={len(rows)} "
                f"expected_split={expected}"
            )
            return 1

    try:
        from qdrant_client import QdrantClient  # noqa: PLC0415 - evaluation-only
    except ImportError:
        print("FAIL reason=missing_eval_dependency")
        return 1

    # One snapshot for both arms: reloading per query could index the vector arm
    # on text the keyword arm no longer sees.
    corpus = load_sample_corpus()
    client = QdrantClient(url=qdrant_url())
    # The validated value is the one passed to the embedder and printed.
    embedder = FastembedEmbedder(model_path=model_path)
    # Re-verify after loading: the snapshot can be updated between the preflight
    # hash and the model load, which would make the reported digest a lie.
    if not _artifact_unchanged(model_path, embed_identity):
        print(
            "FAIL reason=embed_artifact_changed_during_load "
            f"before={embed_identity} after={artifact_identity(model_path)}"
        )
        return 1
    indexed = build_index(
        client,
        corpus,
        embedder=embedder,
        # Only ever pointed at a disposable instance; see build_index.
        allow_recreate=os.environ.get("QDRANT_ALLOW_RECREATE") == "1",
    )

    def keyword(query: str, limit: int) -> list[str]:
        return [
            hit.doc_id for hit in keyword_search(query, limit=limit, documents=corpus)
        ]

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
    # Each arm keeps its own development optimum. Picking one joint limit would
    # hand the tuned setting to the winner and evaluate the loser off-peak.
    best: dict[str, int] = {}
    for arm, _ in arms:
        best[arm] = max(
            CANDIDATE_LIMITS, key=lambda limit: (scores[(limit, arm)], -limit)
        )
    for arm, _ in arms:
        print(
            f"stage=select chosen_arm={arm} chosen_limit={best[arm]} "
            f"basis=development_only_per_arm"
        )
    top_score = max(scores[(best[arm], arm)] for arm, _ in arms)
    outcome = _arm_outcome(
        [arm for arm, _ in arms if scores[(best[arm], arm)] == top_score]
    )
    print(f"stage=select best_arm={outcome} top_score={top_score}")

    # Step 2: continuity only. This split was already observed once.
    for arm, retrieve in arms:
        counts = _tally(contaminated, lambda case: retrieve(case.query, best[arm]))
        _report(
            f"stage=contaminated split={CONTAMINATED_SPLIT} limit={best[arm]} arm={arm} "
            f"basis=already_observed",
            counts,
            len(contaminated),
        )

    # Step 3: the never-seen confirmation set, claimed only now. A failure
    # during dependency import or index setup must not burn the one-shot set, so
    # the claim is deliberately taken after that preflight has succeeded.
    claimed, marker = _claim_confirmation_once()
    if not claimed:
        print(f"SKIP reason=confirmation_already_consumed marker={marker}")
        return 0
    for arm, retrieve in arms:
        counts = _tally(confirmation, lambda case: retrieve(case.query, best[arm]))
        _report(
            f"stage=confirm split={CONFIRMATION_SPLIT} limit={best[arm]} arm={arm} "
            f"basis=predeclared_never_seen",
            counts,
            len(confirmation),
        )
    # Report only what is observable: whether a path was configured. Whether it is
    # genuinely shared and durable is the operator's claim, not this run's.
    scope = "configured" if os.environ.get("ULTICODE_VECTOR_CONFIRM_MARKER") else "default_workspace"
    print(
        f"stage=confirm note=claim_recorded marker={marker} "
        f"marker_location={scope} shared_durability=unverified_by_this_run"
    )

    print(
        f"OK comparison corpus=agent-authored-synthetic "
        f"corpus_digest={_corpus_digest(corpus)} "
        f"development_cases_digest={development_digest} "
        f"contaminated_cases_digest={contaminated_digest} "
        f"confirmation_cases_digest={confirmation_digest} "
        f"qdrant_image_asserted_by_caller={image} "
        f"qdrant_image_verified_against_server=false "
        f"embed_artifact={embed_identity} min_score={MIN_SCORE} "
        f"collection={COLLECTION} "
        f"development={len(development)} contaminated={len(contaminated)} "
        f"confirmation={len(confirmation)} "
        f"evaluated_total={len(development) + len(contaminated) + len(confirmation)} "
        f"docs={indexed} "
        f"scope=synthetic_slice_not_authorized_corpus"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001 - fixed status label only
        print(f"FAIL error={type(exc).__name__}")
        raise SystemExit(1) from None
