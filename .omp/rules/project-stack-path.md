---
description: Database, frontend, shared-package, operations, documentation, and cross-stack
  rules for UltiCode.
globs:
- init-db/**/*.sql
- init-db/**/*.conf
- scripts/dev/**/*
- docker-compose*.yml
- docker/**/*
- apps/**/*
- packages/**/*
- AGENTS.md
- '**/AGENTS.md'
- CLAUDE.md
- PROJECT_DOCUMENTATION.md
priority: 100
---

# UltiCode stack rules

- MUST read the nearest `AGENTS.md` before editing the affected surface.
- Database: `init-db/migrations/` is the only migration source; never edit an applied migration; use `V{timestamp}__Description.sql` and preserve rollback compatibility.
- Database: migration and mapper changes MUST use parameterized access, explicit columns, deterministic pagination, bounded batches, intentional affected-row checks, and preserve schema ownership.
- Database: use `utf8mb4` and the established collation; business-key uniqueness belongs in a database constraint; seed and fixture SQL MUST NOT create usable credentials or plaintext tokens.
- Frontend: Vue components MUST use the established Composition API and typed public boundaries; do not add `any`, `@ts-ignore`, unsafe assertions, or unsanitized HTML/URL sinks.
- Frontend: HTTP calls MUST use the application request helper or the established shared package; component-local clients and direct Console-to-Management imports are forbidden.
- Frontend: preserve Vue 3/TypeScript contracts, existing routing/store seams, i18n coverage, keyboard accessibility, cancellation/stale-result handling, and the `Result` envelope.
- Frontend: stable behavior shared between Console and Management belongs in a focused package under `packages/`; do not force app-specific behavior into shared code.
- Frontend: add focused Vitest regressions for changed behavior, loading/empty/success/failure states, and malicious-input paths where rendering or URLs are involved.
- Operations: development-only infrastructure exposure belongs in `docker-compose.dev.yml` and binds to loopback; base and production Compose files MUST NOT publish backend or infrastructure ports.
- Operations: use existing startup, migration, PM2, and verification entry points; do not invent parallel operational paths.
- Shell: new Bash scripts MUST use `#!/usr/bin/env bash`, default to `set -euo pipefail`, quote expansions, avoid `eval`, use `mktemp` plus cleanup traps, bound retries/polling, and fail closed for security-sensitive validation.
- Shell: changed scripts MUST pass `bash -n`; never echo secrets or enable `set -x` around credentials.
- Documentation: update `PROJECT_DOCUMENTATION.md` in the same change when behavior, commands, paths, contracts, or architecture boundaries change; keep implementation and executable configuration authoritative.
- Cross-stack: inspect both backend and frontend consumers before changing DTOs, VOs, API routes, events, or shared types; record the producer-consumer mapping and verify wire compatibility.
