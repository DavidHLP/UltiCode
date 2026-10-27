# Changelog

## Unreleased

This is release-candidate preparation, not an official stable release or a
production-readiness declaration.

- Normalize Management problem difficulty at the API boundary, with regression tests.
- Add extended disposable business acceptance; problem creation remains a release
  blocker because owner ID generation and the complete create-command contract
  are not aligned. Do not claim problem CRUD acceptance.
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

Before an official release, retain the tested source commit, successful CI and
real-service acceptance evidence, full signed nine-service digest manifest,
SBOM/provenance/scan evidence and release notes as durable assets. The source-only
bundle is not deployment input. Production deployment, release publication and
branch-protection changes require separate approval.
