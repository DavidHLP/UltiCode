"""Corpus manifest gate for retrieval material (DAV-58 exit criterion).

What this checks
----------------
* **Metadata completeness** — every declared document carries the five fields
  DAV-58 names: ``permission``, ``scope``, ``version``, source position, and the
  exact model-input projection. Any missing or blank one fails.
* **Binding to a real document** — the manifest must name real, retrievable
  documents: ``doc_id``, ``chunk_id``, ``source_path``, ``access_scope`` and
  ``sample_kind`` must agree with what the corpus loader produces.

What this does NOT check
------------------------
It does **not** prove that a source is authorized. A non-blank ``permission``
string is a claim someone wrote down, not evidence of a license. Real
authorization stays with the person who supplies the material; until then a
``real`` entry is only as trustworthy as that claim.

Field provenance is kept explicit: ``source_trust`` and the five authorization
fields are manifest metadata, not fields of ``retrieval.SourceDocument``.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

MANIFEST_PATH = Path(__file__).resolve().parents[1] / "data" / "corpus_manifest.json"

#: The five fields DAV-58 requires. This list is the exit criterion, not an
#: invented schema.
AUTHORIZATION_FIELDS = (
    "permission",
    "scope",
    "version",
    "source_position",
    "model_input_projection",
)
#: Fields that bind a manifest entry to a document the corpus loader produces.
DOCUMENT_BINDING_FIELDS = (
    "doc_id",
    "chunk_id",
    "source_path",
    "access_scope",
    "sample_kind",
)
REQUIRED_FIELDS = DOCUMENT_BINDING_FIELDS + AUTHORIZATION_FIELDS
#: Manifest-only provenance: present on a SourceHit, not on a SourceDocument.
MANIFEST_PROVENANCE_FIELD = "source_trust"
SYNTHETIC_PERMISSIONS = frozenset({"agent-authored-synthetic", "synthetic"})


class ManifestError(ValueError):
    """The manifest is incomplete or inconsistent with the corpus."""


@dataclass(frozen=True)
class ManifestEntry:
    doc_id: str
    chunk_id: str
    source_path: str
    access_scope: str
    sample_kind: str
    permission: str
    scope: str
    version: str
    source_position: str
    model_input_projection: str
    source_trust: str


def _require_text(entry: dict[str, object], field: str, doc_id: str) -> str:
    value = entry.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ManifestError(f"{doc_id}: missing or blank {field}")
    return value.strip()


def load_manifest(path: Path | None = None) -> tuple[ManifestEntry, ...]:
    """Parse a manifest, rejecting incomplete or self-contradictory entries."""
    manifest_path = path or MANIFEST_PATH
    try:
        raw = json.loads(manifest_path.read_text(encoding="utf-8"))
    except ValueError as error:
        raise ManifestError(f"corpus manifest is not valid JSON: {error}") from None
    if not isinstance(raw, list) or not raw:
        raise ManifestError("corpus manifest must be a non-empty list")
    entries: list[ManifestEntry] = []
    seen: set[str] = set()
    for item in raw:
        if not isinstance(item, dict):
            raise ManifestError("corpus manifest entries must be objects")
        doc_id = _require_text(item, "doc_id", "<unknown>")
        if doc_id in seen:
            raise ManifestError(f"{doc_id}: declared twice")
        seen.add(doc_id)
        values = {
            field: _require_text(item, field, doc_id)
            for field in (*REQUIRED_FIELDS, MANIFEST_PROVENANCE_FIELD)
        }
        if values["sample_kind"] not in {"synthetic", "real"}:
            raise ManifestError(f"{doc_id}: sample_kind must be synthetic or real")
        if values["sample_kind"] == "real" and values["permission"] in SYNTHETIC_PERMISSIONS:
            raise ManifestError(
                f"{doc_id}: a real source cannot carry a synthetic permission marker"
            )
        entries.append(ManifestEntry(**values))
    return tuple(entries)


def assert_manifest_covers(
    entries: tuple[ManifestEntry, ...], documents: tuple[object, ...]
) -> None:
    """Fail closed when the corpus and the manifest disagree.

    ``documents`` is a sequence of ``retrieval.SourceDocument``; only ``doc_id``,
    ``chunk_id``, ``source_path``, ``access_scope``, ``sample_kind`` and
    ``source_position`` are compared.
    """
    by_doc = {entry.doc_id: entry for entry in entries}
    for document in documents:
        entry = by_doc.get(getattr(document, "doc_id", ""))
        if entry is None:
            raise ManifestError(
                f"{getattr(document, 'doc_id', '?')}: retrievable but not declared"
            )
        for field in ("chunk_id", "source_path", "access_scope", "sample_kind", "source_position"):
            declared = getattr(entry, field)
            actual = getattr(document, field, None)
            if actual is not None and declared != actual:
                raise ManifestError(
                    f"{entry.doc_id}: manifest {field} does not match the corpus"
                )
        declared_version = entry.version
        actual_version = getattr(document, "version", None)
        if actual_version is not None and declared_version != actual_version:
            raise ManifestError(f"{entry.doc_id}: manifest version does not match the corpus")
    undeclared = set(by_doc) - {getattr(document, "doc_id", "") for document in documents}
    if undeclared:
        raise ManifestError(f"manifest declares documents that are not retrievable: {sorted(undeclared)}")
