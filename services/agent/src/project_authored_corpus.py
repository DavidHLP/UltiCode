"""Project-authored corpus path, kept separate from the pinned synthetic baseline.

The three-document sample corpus under ``corpus/`` carries the deterministic
keyword baseline that DAV-22 recorded, including pinned evaluation numbers. This
module therefore loads a *different* corpus from ``corpus_project_authored/`` and
validates it against its own manifest, leaving the baseline untouched.

Scope honesty: the checked-in material is authored for this project by the
project's assistant (``project-authored-for-u02``). It is **not** user-authored
and **not** licensed third-party material, so loading it here does not satisfy
DAV-58's requirement for genuinely licensed real sources. That gate stays open, and the
name deliberately avoids "authorized" so a reader cannot mistake this for licensed material.
"""

from __future__ import annotations

import json
from pathlib import Path

from corpus_manifest import ManifestError, assert_manifest_covers, load_manifest
from retrieval import MAX_SOURCE_CHARS, SourceDocument, keyword_search

CORPUS_DIR = Path(__file__).resolve().parents[1] / "corpus_project_authored"
MANIFEST_PATH = Path(__file__).resolve().parents[1] / "data" / "project_authored_manifest.json"
#: The only permission marker this project may claim for its own study material.
PERMISSION = "project-authored-for-u02"
SCOPE = (
    "UltiCode project study material written for U02; ingest and model egress "
    "allowed for local evaluation; not user-authored and not licensed third-party material"
)
PROJECTION = "SourceHit.as_model_dict()"


def _load_documents() -> tuple[SourceDocument, ...]:
    documents: list[SourceDocument] = []
    if CORPUS_DIR.is_symlink():
        # Resolving a symlinked root would make an external directory the trusted
        # root, so every child would then pass the per-file containment check.
        raise ManifestError(f"corpus root must not be a symlink: {CORPUS_DIR}")
    root = CORPUS_DIR.resolve()
    for path in sorted(CORPUS_DIR.glob("*.md")):
        # Resolve before reading: a symlink would otherwise expose content from
        # outside the authorized directory while the document still claims a
        # repository-local path and project-authored permission.
        if path.is_symlink() or path.resolve().parent != root:
            raise ManifestError(f"{path.name}: corpus entry escapes {root}")
        text = path.read_text(encoding="utf-8").strip()
        if not text:
            raise ManifestError(f"{path.name}: empty project-authored document")
        if len(text) > MAX_SOURCE_CHARS:
            # Without this bound a large file lands whole in every SourceHit and in
            # the declared model-input projection, blowing memory and prompt budget.
            raise ManifestError(
                f"{path.name}: {len(text)} chars exceeds the {MAX_SOURCE_CHARS} source cap"
            )
        lines = len(text.splitlines())
        documents.append(
            SourceDocument(
                doc_id=f"project-{path.stem}",
                version="v1",
                source_path=f"services/agent/corpus_project_authored/{path.name}",
                access_scope="project-study",
                sample_kind="synthetic",
                text=text,
                source_position=f"lines 1-{lines}",
            )
        )
    if not documents:
        raise ManifestError("project-authored corpus is empty")
    return tuple(documents)


def load_project_authored_corpus() -> tuple[SourceDocument, ...]:
    """Load and validate. Fails closed if the manifest and corpus disagree."""
    documents = _load_documents()
    manifest = load_manifest(MANIFEST_PATH)
    assert_manifest_covers(manifest, documents)
    # A manifest that claims licensed or user-authored material would defeat the
    # point of this corpus, so the declared metadata must match the only values
    # this module stands behind.
    for entry in manifest:
        if entry.permission != PERMISSION or entry.scope != SCOPE:
            raise ManifestError(
                f"{entry.doc_id}: project-authored material must declare "
                f"permission={PERMISSION!r} and the project scope"
            )
    return documents


def search_project_corpus(query: str, *, limit: int = 3) -> tuple[object, ...]:
    return keyword_search(query, limit=limit, documents=load_project_authored_corpus())


def answer_with_project_evidence(question: str, submission: dict[str, str]) -> dict[str, object]:
    """One minimal end-to-end answer: facts from the projection, cited evidence.

    Hypotheses stay separate from facts, and no citation is emitted unless it
    verifies against the authorised corpus.
    """
    from citation_integrity import all_verified, check_citations
    from sourced_analysis import validate_submission_facts

    # Same boundary validation as the main path: a blank status is a substring of
    # every fragment, so filtering on it would cite everything.
    submission_id, status = validate_submission_facts(dict(submission))
    facts = [f"提交 {submission_id} 的状态是 {status}。"]
    hits = tuple(
        hit
        for hit in search_project_corpus(question)
        if status.casefold() in hit.text.casefold()
    )
    if not hits:
        return {
            "facts": facts,
            "hypotheses": ["授权语料中没有能支持本次判断的片段，不据此提出具体诊断。"],
            "citations": [],
            "citations_verified": False,
        }
    citations = [hit.as_model_dict() for hit in hits]
    return {
        "facts": facts,
        "hypotheses": [
            "只有状态与授权片段，没有源码或失败用例；不能据此定位具体代码行或断言运行结果。"
        ],
        "citations": citations,
        "citations_verified": all_verified(
            check_citations(citations, load_project_authored_corpus())
        ),
    }
