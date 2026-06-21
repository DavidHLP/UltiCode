---
title: Result Envelope & Case Mapping
type: concept
tags: [api, backend, frontend]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/common/response/
  - backend-spring/src/main/java/com/ulticode/common/dto/
  - shared/auth-core/
  - .claude/rules/springboot-rules.md
aliases: [Result 封装, 命名映射]
---

# Result Envelope & Case Mapping

## The problem
Every API needs one predictable shape (so the frontend has one parse path), and
the DB (`snake_case`) must map cleanly to Java entities (`camelCase`) and onward
to the frontend — without per-field hand-mapping everywhere.

## The decision
- **Envelope**: all responses wrapped in `Result<T>`:
  `{ code, message, data, traceId }`. `code = 0` = success; non-zero = error.
  `traceId` for request tracing. Paging via `PageResult.of(list, total, page, limit)`.
- **Errors**: business logic throws `BusinessException(ErrorCode.XXX)`; the global
  handler converts to `Result.error(...)`. Controllers don't try-catch business
  exceptions.
- **Case mapping**: MyBatis-Plus `mapUnderscoreToCamelCase = true` maps `snake_case`
  columns ↔ camelCase entities automatically. The frontend (`request.ts`) maps the
  wire `snake_case` ↔ `camelCase` in its interceptors.

## Known gap
Backend DTO **enum fields are still raw `String`** (e.g.
`PerformModerationActionDTO.action`); the frontend uses proper TS enums. Aligning
is an audited cross-stack procedure (`cross-stack-dto-granularity-alignment`
skill) — prefer backend enum adoption over widening the gap.

## Where it lives
- `common/response/Result`, `common/dto/PageResult`, `common/exception/`.
- MyBatis config (`application.yml`).
- `console|management/src/utils/request.ts`, `shared/auth-core`.

## Related
[[concepts/module-layering]] · [[overview/backend-modules-overview]] ·
[[overview/frontend-apps-overview]]
