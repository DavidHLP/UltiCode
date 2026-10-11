"""Version-bound excerpts from the repository's MIT-licensed core documents."""

import hashlib
import re
from pathlib import Path

from corpus_manifest import ManifestError, assert_manifest_covers, load_manifest
from retrieval import MAX_SOURCE_CHARS, SourceDocument

ROOT = Path(__file__).resolve().parents[3]
MANIFEST = "services/agent/data/repository_corpus_manifest.json"
SOURCES = frozenset(f"docs/{name}.md" for name in
                    ("PRODUCT", "ARCHITECTURE", "DEVELOPMENT", "OPERATIONS", "REFERENCE"))
LICENSE_SHA256 = "2be36e9d56578c1111740b6fe339ea3619cf1f2000af718f1a59e97d2e5d995c"
PERMISSION = f"MIT:LICENSE:sha256:{LICENSE_SHA256}"


def _read(root: Path, name: str) -> bytes:
    path = root / name
    if any(p.is_symlink() for p in (root, path.parent, path)):
        raise ManifestError("repository source must not be a symlink")
    try:
        return path.read_bytes()
    except OSError:
        raise ManifestError("repository source is unreadable") from None


def load_repository_corpus(root: Path = ROOT) -> tuple[SourceDocument, ...]:
    if hashlib.sha256(_read(root, "LICENSE")).hexdigest() != LICENSE_SHA256:
        raise ManifestError("repository license changed")
    entries = load_manifest(root / MANIFEST)
    if len(entries) != len(SOURCES) or {e.source_path for e in entries} != SOURCES:
        raise ManifestError("repository manifest must cover exactly the core source paths")
    documents = []
    for entry in entries:
        if (entry.permission != PERMISSION or entry.sample_kind != "real"
                or entry.access_scope != "repository-public"):
            raise ManifestError("repository source permission or scope mismatch")
        raw = _read(root, entry.source_path)
        if entry.version != f"sha256-{hashlib.sha256(raw).hexdigest()}":
            raise ManifestError("repository source version changed")
        match = re.fullmatch(r"lines ([1-9]\d*)-([1-9]\d*)", entry.source_position)
        try:
            lines = raw.decode("utf-8").splitlines()
        except UnicodeError:
            raise ManifestError("repository source is not UTF-8") from None
        if not match or not 1 <= int(match[1]) <= int(match[2]) <= len(lines):
            raise ManifestError("invalid repository source position")
        text = "\n".join(lines[int(match[1]) - 1:int(match[2])]).strip()
        if not text or len(text) > MAX_SOURCE_CHARS:
            raise ManifestError("invalid repository source excerpt")
        documents.append(SourceDocument(
            doc_id=entry.doc_id, version=entry.version, source_path=entry.source_path,
            access_scope=entry.access_scope, sample_kind=entry.sample_kind,
            text=text, source_position=entry.source_position,
        ))
    corpus = tuple(documents)
    assert_manifest_covers(entries, corpus)
    return corpus
