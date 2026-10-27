# Changelog

## Unreleased

No next release has been published from this development branch.

- Clarify release-pinned installation versus existing-environment upgrades.
- Add selected real business/migration acceptance to the CI summary gate.
- Prepare repeatable eight-asset packages only from complete verified image evidence;
  external tag/Release publication still requires separate human authorization.

## [1.0.4](https://github.com/DavidHLP/UltiCode/releases/tag/v1.0.4) — 2026-10-27

Published from `465b9fd031f2c398f39f4696d8b33360f635ed5d`. This release is not a
production-readiness certification.

- Normalize Management problem difficulty at the API boundary, with regression tests.
- Restore database-generated problem IDs with an additive owner migration;
  preserve existing IDs and validated test-case references. Carry complete
  create/update fields across the owner seam and return nested detail readback.
  Persist detail before the first version snapshot and roll back rejected child
  writes. Extend real-service acceptance through persisted create/edit and Console visibility.
- Restore Management's language-menu CI contract and test actual selection-event
  handling and the current-language indicator.
- Refresh Agent repository-document version binding while preserving license,
  content, permission and source-integrity rejection gates.
- Allocate backend service version `1.0.4` in reactor and owner POMs; preserve
  immutable image tags and require the complete nine-service release manifest.
- Document a separate disposable fresh-volume installation procedure. Keep
  canonical migrations and sample data, insert-only backfill, parity, writer
  isolation, backups and legacy upgrade/rollback gates intact.
- Make already-isolated Submission cutover a verified no-op rather than
  restoring legacy grants after an empty REVOKE.
- Redact the transient WebSocket handshake credential and consume it at CONNECT;
  authentication remains cookie-only and fail-closed.

The Agent remains experimental and disabled by default. Tests using scripted
models do not establish real-model quality, budget readiness or production
acceptance. Local Docker/browser evidence is scoped to disposable local services.

The published release retains the exact source, complete nine-service digest
manifest, signed SBOM/provenance/scan evidence and checksums as eight durable
assets. Main CI/publication and independent verification passed. Existing
immutable tags were preserved; no business production deployment was performed.
