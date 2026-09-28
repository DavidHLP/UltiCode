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
#: Digest binding a manifest entry to the exact text it authorised, so replacing a
#: file's content cannot ride in on the old permission and scope.
CONTENT_DIGEST_FIELD = "content_digest"
#: Fields that bind a manifest entry to a document the corpus loader produces.
DOCUMENT_BINDING_FIELDS = (
    "doc_id",
    "chunk_id",
    "source_path",
    "access_scope",
    "sample_kind",
    # Binds the authorization record to the exact text it authorised.
    CONTENT_DIGEST_FIELD,
)
REQUIRED_FIELDS = DOCUMENT_BINDING_FIELDS + AUTHORIZATION_FIELDS
#: Manifest-only provenance: present on a SourceHit, not on a SourceDocument.
MANIFEST_PROVENANCE_FIELD = "source_trust"
#: Retrieval always emits this marker and citation verification enforces it, so a
#: manifest may not claim anything else for the same field.
EXPECTED_SOURCE_TRUST = "untrusted-data"
SYNTHETIC_PERMISSIONS = frozenset({"agent-authored-synthetic", "synthetic"})
#: The projection retrieval actually emits. A manifest may not claim a narrower
#: egress than the code performs, so an unsupported name is rejected.
SUPPORTED_PROJECTIONS = ("SourceHit.as_model_dict()",)


class _DuplicateKey(ValueError):
    """A repeated key in a manifest entry, reported instead of silently kept."""


def content_digest(text: str) -> str:
    """Digest of the authorised text."""
    import hashlib

    return "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()


def _reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            # Last-write-wins could erase a restrictive first declaration, which
            # in the authorization gate is the dangerous direction.
            raise _DuplicateKey(key)
        result[key] = value
    return result


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
    content_digest: str


def _require_text(entry: dict[str, object], field: str, doc_id: str) -> str:
    value = entry.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ManifestError(f"{doc_id}: missing or blank {field}")
    return value.strip()


class ManifestEmpty(ManifestError):
    """The manifest parsed fine and declared nothing at all.

    A caller that can then tell an intentionally empty corpus from a manifest that
    will not parse has a reason for each, instead of one generic failure.
    """


def parse_manifest_text(text: str) -> tuple[ManifestEntry, ...]:
    """Parse manifest **text** into entries, validating every declaration.

    Split from ``load_manifest`` so a caller that already read the file can parse the
    same bytes once instead of reopening it: empty-list classification and declaration
    validation then consume one snapshot.
    """
    try:
        raw = json.loads(text, object_pairs_hook=_reject_duplicate_keys)
    except _DuplicateKey as error:
        raise ManifestError(
            f"corpus manifest has a duplicate key: {error}"
        ) from None
    except ValueError as error:
        raise ManifestError(f"corpus manifest is not valid JSON: {error}") from None
    if not isinstance(raw, list) or not raw:
        if raw == []:
            raise ManifestEmpty("corpus manifest declares no entries")
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
        entries.append(ManifestEntry(**values))
    # Declaration rules live in one place: parsed entries and entries arriving in a
    # snapshot from the acceptance preflight run the same checks, so the two cannot
    # drift apart. Parsing above only assembles and enforces field presence.
    validated = tuple(entries)
    validate_entries(validated)
    return validated


def load_manifest(path: Path | None = None) -> tuple[ManifestEntry, ...]:
    """Read and parse a manifest, rejecting incomplete or self-contradictory entries."""
    manifest_path = path or MANIFEST_PATH
    try:
        text = manifest_path.read_text(encoding="utf-8")
    except OSError as error:
        raise ManifestError(f"corpus manifest is unreadable: {error}") from None
    except UnicodeError as error:
        raise ManifestError(f"corpus manifest is not UTF-8: {error}") from None
    return parse_manifest_text(text)


def validate_entries(entries: tuple[ManifestEntry, ...]) -> None:
    """Re-apply the declaration rules to already-parsed entries.

    ``load_manifest`` applies them while parsing; a caller that is handed a snapshot
    instead of a path must not be able to skip them, so the rules live in one place
    and both callers run them. Every check mirrors what parsing enforces: fields
    present and non-blank, declared once, an expected source trust, a supported
    projection, a known material class, and no real source hiding behind a synthetic
    permission marker.
    """
    seen: set[str] = set()
    for entry in entries:
        doc_id = entry.doc_id
        if not str(doc_id or "").strip():
            raise ManifestError("manifest entry has a blank doc_id")
        if doc_id in seen:
            raise ManifestError(f"{doc_id}: declared twice")
        seen.add(doc_id)
        for field in (*REQUIRED_FIELDS, MANIFEST_PROVENANCE_FIELD):
            value = getattr(entry, field, None)
            if not isinstance(value, str) or not value.strip():
                raise ManifestError(f"{doc_id}: missing or blank {field}")
        if entry.source_trust != EXPECTED_SOURCE_TRUST:
            raise ManifestError(
                f"{doc_id}: source_trust must be {EXPECTED_SOURCE_TRUST!r}, "
                "which is what retrieval emits"
            )
        if entry.model_input_projection not in SUPPORTED_PROJECTIONS:
            raise ManifestError(
                f"{doc_id}: model_input_projection must be one of "
                f"{SUPPORTED_PROJECTIONS}, so the record cannot understate what "
                "retrieval sends to the model"
            )
        if entry.sample_kind not in {"synthetic", "real"}:
            raise ManifestError(f"{doc_id}: sample_kind must be synthetic or real")
        if entry.sample_kind == "real" and entry.permission in SYNTHETIC_PERMISSIONS:
            raise ManifestError(
                f"{doc_id}: a real source cannot carry a synthetic permission marker"
            )


def assert_manifest_covers(
    entries: tuple[ManifestEntry, ...], documents: tuple[object, ...]
) -> None:
    """Fail closed when the corpus and the manifest disagree.

    ``documents`` is a sequence of ``retrieval.SourceDocument``; only ``doc_id``,
    ``chunk_id``, ``source_path``, ``access_scope``, ``sample_kind`` and
    ``source_position`` are compared.
    """
    by_doc = {entry.doc_id: entry for entry in entries}
    # Identity first: a repeated doc_id/version is the more specific fault, and it
    # must not be masked by the content binding below.
    seen: dict[tuple[str, str], str] = {}
    chunk_ids: dict[str, tuple[str, str]] = {}
    for document in documents:
        identity = (getattr(document, "doc_id", ""), getattr(document, "version", ""))
        text = getattr(document, "text", "")
        if identity in seen:
            # Even a byte-identical duplicate is refused: keyword_search would emit
            # it twice and the second copy could consume a result slot, hiding
            # another required document.
            raise ManifestError(f"{identity[0]}: duplicate document identity")
        seen[identity] = text
        chunk_id = str(getattr(document, "chunk_id", ""))
        # Two *distinct* identities can still reach one chunk key when a separator
        # appears inside doc_id or version (`a` + `b:c` and `a:b` + `c` both make
        # `a:b:c:1`). Chunk-keyed lookups keep one, so the corpus would silently
        # lose a document.
        if chunk_id in chunk_ids:
            raise ManifestError(f"{chunk_id}: two documents share one chunk id")
        chunk_ids[chunk_id] = identity

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
        # A SourceDocument exposes only `text`, so the digest must be recomputed
        # from it; reading a non-existent attribute would skip the comparison and
        # let changed content ride in on the old permission and scope.
        actual_digest = content_digest(getattr(document, "text", ""))
        if entry.content_digest != actual_digest:
            raise ManifestError(
                f"{entry.doc_id}: manifest content_digest does not match the corpus text"
            )
    undeclared = set(by_doc) - {getattr(document, "doc_id", "") for document in documents}
    if undeclared:
        raise ManifestError(f"manifest declares documents that are not retrievable: {sorted(undeclared)}")
