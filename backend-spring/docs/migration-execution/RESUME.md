# Migration Resume

## Current Phase

Phase 7 — retire `backend-legacy` through dependency-correct family relocation and contract cutover.

## Active / Next Task

- Active parent `P7-RELOCATE-AUTH-001` was superseded by five atomic cutover tasks.
- Next ready task: `P7-AUTH-ADMIN-PROVIDER-001`.
- Completed: `P7-AUTH-QUERY-PROVIDERS-001` (commit 29bc310).
- Completed: `P7-AUTH-IDEMPOTENCY-SCHEMA-001` (commit 0815da7).
- Completed: `P7-AUTH-PERSISTENCE-001` (commit e3d7fa5).
- Goal: replace the backend-auth in-memory account fallback with authoritative MySQL account/version persistence and bulk permission reads before implementing RPC providers.

## Last Verified Context

- Legacy auth duplicate map: 24/25 have canonical backend-auth counterparts; `JwtUtils` is the WebSocket-specific exception.
- Legacy permission duplicate map: 8/8 have canonical counterparts.
- `backend-auth-api` declares three business services, but backend-auth currently exports only `AuthRpcHealthProvider`.
- `users.authz_version` exists in additive migrations, but `AuthAccountRecord`/`AuthAccountPort` do not expose it.
- `DefaultAuthAccountAdapter` is currently in-memory and cannot back authoritative RPC snapshots or expected-version writes.
- Refresh-token storage remains SHA-256 hash-only under `V20260606130000`.

## Recent Planning Commits

- `78293d5` dependency-correct leaf relocation order
- `1014826` WebSocket assigned to backend-app
- `2ea544c` auth contract cutover required before retirement
- `e634187` canonical auth duplicate map
- `0c4a504` atomic auth cutover execution plan
- `e3d7fa5` feat(auth): implement authoritative account and permission persistence seams
- `0815da7` feat(auth): add durable command receipt migration for RPC idempotency
- `29bc310` feat(auth): implement identity and authorization snapshot query Dubbo providers

## Worktree / Delivery

- Expected planning diff: `TASKS.yaml`, `COVERAGE.md`, `DECISIONS.md`, `RESUME.md` only.
- No GitHub push, merge, release, deploy, or production mutation is authorized.
