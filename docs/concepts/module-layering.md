---
title: Module Layering
type: concept
tags: [backend, architecture]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/
  - .claude/rules/springboot-rules.md
  - AGENTS.md
aliases: [模块分层]
---

# Module Layering

## The problem
26 modules × several classes each needs a uniform internal shape, or layers leak
(business logic in controllers, entities returned to the frontend, raw SQL in XML).

## The decision
Every module is `controller → service → mapper (MyBatis-Plus) → entity`:

```
modules/<m>/
├── controller/   REST, @PreAuthorize, param binding — NO business logic
├── service/      interface + impl/ — ALL business logic
├── mapper/       BaseMapper<T> CRUD; custom via @Select/@Update — NO XML
├── entity/       DO, @TableName, UUID PK, is_deleted
└── dto/          DTO/VO (MapStruct-mapped)
```

- Controllers call services; services call mappers.
- **No cross-layer calls**: controller ↛ mapper; service ↛ entity-to-controller
  (return DTO/VO).
- No XML mappers — custom SQL is `@Select`/`@Update` annotations.
- Paging: `Page<T>` + `selectPage`.
- Custom security annotations: `@RequireRole`, `@CheckBan`, `@RateLimit`,
  `@Audited`, `@CurrentUser`.

## Where it lives
- Every `modules/*/`; enforced by `.claude/rules/springboot-rules.md` + `AGENTS.md`.

## Trade-offs
- Boilerplate per module — accepted for navigability and AI-friendliness.
- DTO/VO boundary is loose in places (some modules conflate); new code should
  separate input DTO from output VO.

## Related
[[concepts/result-envelope-and-case-mapping]] ·
[[overview/backend-modules-overview]] · [[overview/architecture-overview]]
