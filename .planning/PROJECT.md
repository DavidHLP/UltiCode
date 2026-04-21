# UltiCode — Online Programming Platform

## What This Is

UltiCode is an online programming platform (similar to LeetCode) built with Spring Boot + Vue 3, featuring problem solving, contests, forum discussions, and code execution.

## Current State

**Last shipped:** v1.6 User & Social (2026-04-21)
**Total phases completed:** 29 (across v1.0–v1.6)

## Core Value

平台安全性、功能完整性和交付自动化

## Platform Stack

- **Backend**: Spring Boot 3.5 (Java 17), MyBatis-Plus, MySQL, Redis
- **Frontend Console**: Vue 3 + Vite + Tailwind CSS (user-facing)
- **Frontend Management**: Vue 3 + Vite + Tailwind CSS (admin)
- **Database**: MySQL with Flyway migrations

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

## v1.6 Accomplishments

- Follow system: idempotent follow/unfollow, paginated follower/following lists, @Async achievement triggers
- Profile backend: ProfileVO with 17 fields, avatar upload, profile edit endpoint
- Achievement backend: async WebSocket notifications, progress tracking, FIRST_PROBLEM/LANGUAGE_SOLVED triggers
- Social frontend: ProfileView at /profile/{username}, FollowButton with optimistic toggle, 6 StatsCards

- Rate limiting via Redisson AOP on all public endpoints
- JaCoCo coverage enforcement at 50% line / 40% branch
- Redis caching layer with Spring Cache + Redisson backend
- N+1 query elimination via JOIN FETCH
- PM2 dotenv + Maven build order documented
- ForumServiceImpl, CodeExecutionService, ContestServiceImpl decomposed

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

- Pre-existing test failures in JudgeWorkerProcessorTest and MonitoringServiceTest (unrelated to v1.5 refactoring)

## Next Milestone

Planning begins with `/gsd-new-milestone`.

---

*Last updated: 2026-04-21 after v1.6 milestone*
