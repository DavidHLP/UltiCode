# Migration Resume

## Current Phase

Phase 7 — retire `backend-legacy` through dependency-correct family relocation and contract cutover.

## Active / Next Task

- Active parent `P7-RELOCATE-AUTH-001` was superseded by five atomic cutover tasks.
- Completed: `P7-WEB-SECURITY-INFRA-001` (commit f2af7e1).
- Completed: `P7-AUTH-WEB-ERRORS-001` (commit dc61f0e).
- Completed: `P7-AUTH-RATE-LIMIT-001` (commit 86fa560).
- `P7-AUTH-RETIRE-001` split into three sub-tasks.
- Completed: `P7-RETIRE-AUTH-001` (commit 5987732) — 32 auth files deleted.
- Completed: `P7-RETIRE-PERMISSION-001` (commit 1ad1b4f) — legacy permission module retired via Dubbo RPC cutover.
- Completed: `P7-RETIRE-REFRESHTOKEN-001` (commit 5efdb5c) — legacy refresh token module retired.
- Completed: `P7-AUTH-RETIRE-001` (commits 5987732, 1ad1b4f, 5efdb5c) — legacy auth, permission, refreshtoken twins retired.
- Active: `P7-RELOCATE-ADMIN-001` — Context Packet verified. Admin 213 src + backup 18 src, 14 external files across 8 modules.
- Strategy boundary (ADR-P7-ADMIN-RPC-BOUNDARY): backend-admin implements Dubbo providers AND in-process port/adapter implementations for existing com.ulticode.modules.admin.port.* interfaces. Consumers keep local bindings until they migrate to backend-app, then switch to @DubboReference. P7-LEGACY-REMOVAL deletes legacy port interfaces only after inbound bindings reach zero.
- Completed: `P7-AUTH-CONSUMER-CUTOVER-001` (commit 4dd2792).
- Completed: `P7-AUTH-ADMIN-PROVIDER-001` (commit 93a99b7).
- Completed: `P7-AUTH-QUERY-PROVIDERS-001` (commit 29bc310).
- Completed: `P7-AUTH-IDEMPOTENCY-SCHEMA-001` (commit 0815da7).
- Completed: `P7-AUTH-PERSISTENCE-001` (commit e3d7fa5).
- Goal: replace the backend-auth in-memory account fallback with authoritative MySQL account/version persistence and bulk permission reads before implementing RPC providers.

## Last Verified Context

- `backend-web-security` is the canonical rate-limit module; module tests (20), Redis IT (5), OwnerBoundaryArchTest (10), and affected legacy/auth reactor pass.
- backend-auth now maps auth/common business exceptions to Result; HTTP 429/code 42900/validated Retry-After is covered.
- backend-auth AuthController now enforces login 10/60, register 5/60, and refresh 20/60 through the canonical RateLimit interface.

- Legacy auth duplicate map: 24/25 have canonical backend-auth counterparts; `JwtUtils` is the WebSocket-specific exception.
- Retained auth seams (by design, do NOT delete): `AuthAccountPort`, `AuthCutoverService`, `DefaultAuthAccountAdapter`, `JwtUtils` (WebSocket shim, removed by P7-RELOCATE-WEBSOCKET-001).
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
- `93a99b7` feat(auth): implement account administration Dubbo provider with transactional engine and durable idempotency
- `7c6b98c` feat(auth): complete request-scoped idempotency key derivation and caller wiring in AuthCutoverService
- `686e391` test(auth): add target permission preservation and request-scoped idempotency key stability tests for AuthCutoverService caller
- `d6a9be4` feat(auth): complete feature-flagged consumer routing adapter AuthCutoverService and wire callers
- `4dd2792` feat(auth): implement feature-flagged consumer routing adapter AuthCutoverService

## Worktree / Delivery

- Expected planning diff: `TASKS.yaml`, `COVERAGE.md`, `DECISIONS.md`, `RESUME.md` only.
- No GitHub push, merge, release, deploy, or production mutation is authorized.
