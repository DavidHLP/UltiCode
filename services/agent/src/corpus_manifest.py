"""Corpus manifest gate for authorized retrieval material (DAV-58 exit test).

DAV-58 requires that every corpus entry records its permission, scope, version,
source position, and the exact model-input projection, and that a manifest
missing any of them fails. This module is that gate.

It also refuses the failure mode the plan warns about: a ``real`` document that
carries a synthetic/self-authored permission marker. Public API visibility is not
a license, so nothing may quietly present a real source as self-authored or the
reverse.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

MANIFEST_PATH = Path(__file__).resolve().parents[1] / "data" / "corpus_manifest.json"

#: Fields DAV-58 names explicitly. A manifest entry without any of them fails.
REQUIRED_FIELDS = (
    "doc_id",
    "version",
    "chunk_id",
    "source_path",
    "source_position",
    "access_scope",
    "sample_kind",
    "source_trust",
    "permission",
    "scope",
    "model_input_projection",
)
SYNTHETIC_PERMISSIONS = frozenset({"agent-authored-synthetic", "synthetic"})


class ManifestError(ValueError):
    """The manifest is not acceptable as retrieval material."""


@dataclass(frozen=True)
class ManifestEntry:
    doc_id: str
    version: str
    chunk_id: str
    source_path: str
    source_position: str
    access_scope: str
    sample_kind: str
    source_trust: str
    permission: str
    scope: str
    model_input_projection: str


def _require_text(entry: dict[str, object], field: str, doc_id: str) -> str:
    value = entry.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ManifestError(f"{doc_id}: missing or blank {field}")
    return value.strip()


def load_manifest(path: Path | None = None) -> tuple[ManifestEntry, ...]:
    manifest_path = path or MANIFEST_PATH
    raw = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(raw, list) or not raw:
        raise ManifestError("corpus manifest must be a non-empty list")
    entries: list[ManifestEntry] = []
    for item in raw:
        if not isinstance(item, dict):
            raise ManifestError("corpus manifest entries must be objects")
        doc_id = _require_text(item, "doc_id", "<unknown>")
        values = {field: _require_text(item, field, doc_id) for field in REQUIRED_FIELDS}
        if values["sample_kind"] not in {"synthetic", "real"}:
            raise ManifestError(f"{doc_id}: sample_kind must be synthetic or real")
        if (
            values["sample_kind"] == "real"
            and values["permission"] in SYNTHETIC_PERMISSIONS
        ):
            raise ManifestError(
                f"{doc_id}: a real source cannot carry a synthetic permission marker"
            )
        entries.append(ManifestEntry(**values))
    return tuple(entries)


def covers_corpus(entries: tuple[ManifestEntry, ...], corpus_doc_ids: set[str]) -> bool:
    """Every retrievable document must be declared, and nothing undeclared."""
    return {entry.doc_id for entry in entries} == corpus_doc_ids
