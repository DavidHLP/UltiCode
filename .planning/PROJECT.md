# UltiCode — Online Programming Platform

## What This Is

UltiCode is an online programming platform (similar to LeetCode) built with Spring Boot + Vue 3, featuring problem solving, contests, forum discussions, and code execution.

## Current State

**Last shipped:** v1.7 Notifications (2026-04-21)
**Total phases completed:** 33 (across v1.0–v1.7)

## Core Value

平台安全性、功能完整性和交付自动化

## Platform Stack

- **Backend**: Spring Boot 3.5 (Java 17), MyBatis-Plus, MySQL, Redis
- **Frontend Console**: Vue 3 + Vite + Tailwind CSS (user-facing)
- **Frontend Management**: Vue 3 + Vite + Tailwind CSS (admin)
- **Database**: MySQL with Flyway migrations

## Current Milestone: v1.8 技术债修复 II

**Goal:** 修复 CONCERNS.md 中剩余的高优先级技术债

**Target features:**
- B-01/DEPS-01: 修复 Swagger disabled（springdoc 版本兼容问题）
- CI-01: 修复 Flyway URL obsolete（CI workflow 下载 URL 返回 404）
- PITFALL-01: 修复 Achievement 同步阻塞问题（改为异步事件处理）
- B-02: 修复 Admin Forum Stats 返回硬编码零值

## Recent Milestones

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
| v1.8 Technical Debt III | 2026-04-21 | Swagger, Flyway URL, Achievement async, Forum stats |

## v1.7 Accomplishments

- **WebSocket Push Wiring** (Phase 30): NotificationServiceImpl and AchievementNotificationListener wired to RealtimeService for real-time push
- **Follow Notification Trigger** (Phase 31): First-follow creates FOLLOW notification, idempotent insert, fire-and-forget pattern
- **Contest Reminder Trigger** (Phase 32): T-24h and T-1h dual windows, metadata persistence for dedup
- **Submission Result Trigger** (Phase 33): AC/WA/TLE/etc. results push via WebSocket with metadata

## v1.6 Accomplishments

- Follow system: idempotent follow/unfollow, paginated follower/following lists, @Async achievement triggers
- Profile backend: ProfileVO with 17 fields, avatar upload, profile edit endpoint
- Achievement backend: async WebSocket notifications, progress tracking, FIRST_PROBLEM/LANGUAGE_SOLVED triggers
- Social frontend: ProfileView at /profile/{username}, FollowButton with optimistic toggle, 6 StatsCards

## Technical Debt Status

Major technical debt清偿 from v1.0–v1.5:
- ✅ Security hardening (CSRF, JWT, XSS, CORS)
- ✅ Rate limiting infrastructure
- ✅ JaCoCo coverage baseline
- ✅ Redis caching layer
- ✅ N+1 query optimization
- ✅ Large file service decomposition
- ✅ PM2 build infrastructure
- ✅ CI/CD with Flyway migrations

## Known Issues

- Pre-existing test failures in JudgeWorkerProcessorTest and MonitoringServiceTest (unrelated to current milestone)

## Next Milestone

Next milestone not yet planned. Use `/gsd-new-milestone` to start.

---

*Last updated: 2026-04-21 after v1.8 Technical Debt III milestone started*
