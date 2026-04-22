# UltiCode — Online Programming Platform

## What This Is

UltiCode is an online programming platform (similar to LeetCode) built with Spring Boot + Vue 3, featuring problem solving, contests, forum discussions, and code execution.

## Current State

**Last shipped:** v1.8 Technical Debt III (2026-04-22)
**Total phases completed:** 37 (across v1.0–v1.8)

## Core Value

平台安全性、功能完整性和交付自动化

## Platform Stack

- **Backend**: Spring Boot 3.5 (Java 17), MyBatis-Plus, MySQL, Redis
- **Frontend Console**: Vue 3 + Vite + Tailwind CSS (user-facing)
- **Frontend Management**: Vue 3 + Vite + Tailwind CSS (admin)
- **Database**: MySQL with Flyway migrations

## v1.8 Accomplishments

- **Swagger UI (Phase 34)**: 启用 SwaggerConfig.java，springdoc 降级到 2.6.0 修复与 Spring Boot 3.2.5 兼容性
- **Flyway URL (Phase 35)**: Flyway 11.3.4 → 10.17.0 修复 CI 下载 404
- **Achievement Async (Phase 36)**: Achievement 检查通过 @Async + @TransactionalEventListener(AFTER_COMMIT) 异步化
- **Forum Stats (Phase 37)**: AdminForumServiceImpl 硬编码零值替换为真实 DB count 查询

## Milestone History

| Milestone | Date | Summary |
|-----------|------|---------|
| v1.0 Technical Debt | 2026-04-16 | Security fixes, auth, tests, component split |
| v1.1 Technical Debt II | 2026-04-17 | CORS, prod config, analytics, quality fixes |
| v1.2 CI/CD Pipeline | 2026-04-18 | Docker, CI workflows, Flyway, secrets |
| v1.3 Core Features | 2026-04-19 | User stats, contest system, submission features |
| v1.4 Seed Data | 2026-04-19 | Problem/tag/contest seed data expansion |
| v1.5 Coverage | 2026-04-20 | Rate limiting, JaCoCo, Redis caching, N+1, large file refactoring |
| v1.6 User & Social | 2026-04-21 | Follow system, user profiles, achievements backend, social frontend |
| v1.7 Notifications | 2026-04-21 | WebSocket push, follow/contest/submission notification triggers |
| v1.8 Technical Debt III | 2026-04-22 | Swagger, Flyway URL, Achievement async, Forum stats |

## Deferred Items

Items acknowledged and carried forward from v1.8:

| Category | Item | Status |
|----------|------|--------|
| Dependencies | DEPS-03: springdoc 3.x 升级 | Pending |
| Performance | PERF-01: Achievement N+1 查询优化 | Pending |
| Performance | PERF-02: Follow System 索引优化 | Pending |
| Missing | MISS-01: 测试覆盖率强制执行 | Pending |
| Missing | MISS-02: Rate Limiting 端到端测试 | Pending |

## Next Milestone

Next milestone not yet planned. Use `/gsd-new-milestone` to start.

---

*Last updated: 2026-04-22 after v1.8 Technical Debt III milestone shipped*
