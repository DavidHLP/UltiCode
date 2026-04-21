# Roadmap: UltiCode

## Milestones

- ✅ **v1.0 Technical Debt** — Phases 1-4 (shipped 2026-04-16)
- ✅ **v1.1 Technical Debt II** — Phases 5-8 (shipped 2026-04-17)
- ✅ **v1.2 CI/CD Pipeline** — Phases 9-11 (shipped 2026-04-18)
- ✅ **v1.3 Core Features** — Phases 12-15 (shipped 2026-04-19)
- ✅ **v1.4 Seed Data** — Phases 16-18 (shipped 2026-04-19)
- ✅ **v1.5 Coverage** — Phases 19-25 (shipped 2026-04-20)
- ✅ **v1.6 User & Social** — Phases 26-29 (shipped 2026-04-21)
- ✅ **v1.7 Notifications** — Phases 30-33 (shipped 2026-04-21)
- 🚧 **v1.8 Technical Debt III** — Phases 34-37 (in progress)

## Phase Progress

| Phase | Milestone | Plans | Status | Completed |
|-------|-----------|-------|--------|-----------|
| 1-4 | v1.0 | - | Complete | 2026-04-16 |
| 5-8 | v1.1 | - | Complete | 2026-04-17 |
| 9-11 | v1.2 | - | Complete | 2026-04-18 |
| 12-15 | v1.3 | - | Complete | 2026-04-19 |
| 16-18 | v1.4 | - | Complete | 2026-04-19 |
| 19-25 | v1.5 | - | Complete | 2026-04-20 |
| 26-29 | v1.6 | 5 | Complete | 2026-04-21 |
| 30-33 | v1.7 | 4 | Complete | 2026-04-21 |
| 34-37 | v1.8 | 0 | Not started | - |

---

## Completed Milestones

<details>
<summary>✅ v1.7 Notifications (Phases 30-33) — SHIPPED 2026-04-21</summary>

- [x] Phase 30: WebSocket Push Wiring (1/1 plan) — completed 2026-04-21
- [x] Phase 31: Follow Notification Trigger (1/1 plan) — completed 2026-04-21
- [x] Phase 32: Contest Reminder Trigger (2/2 plans) — completed 2026-04-21
- [x] Phase 33: Submission Result Trigger (1/1 plan) — completed 2026-04-21

</details>

<details>
<summary>✅ v1.6 User & Social (Phases 26-29) — SHIPPED 2026-04-21</summary>

- [x] Phase 26: Follow System (1/1 plan) — completed 2026-04-21
- [x] Phase 27: Profile Backend (1/1 plan) — completed 2026-04-21
- [x] Phase 28: Achievement Backend (1/1 plan) — completed 2026-04-21
- [x] Phase 29: Social Frontend (2/2 plans) — completed 2026-04-21

</details>

<details>
<summary>✅ v1.5 Coverage (Phases 19-25) — SHIPPED 2026-04-20</summary>

- [x] Phase 19: Rate Limiting Infrastructure (1/1 plan)
- [x] Phase 20: JaCoCo Coverage Baseline (1/1 plan)
- [x] Phase 21: Security Hardening (1/1 plan)
- [x] Phase 22: Redis Caching Layer (1/1 plan)
- [x] Phase 23: N+1 Query Optimization (1/1 plan)
- [x] Phase 24: PM2 Build Infrastructure (1/1 plan)
- [x] Phase 25: Large File Refactoring (1/1 plan)

</details>

<details>
<summary>✅ v1.4 Seed Data (Phases 16-18) — SHIPPED 2026-04-19</summary>

- [x] Phase 16: Seed Data Infrastructure (1/1 plan)
- [x] Phase 17: Problem/Tag/Contest Seeds (1/1 plan)
- [x] Phase 18: Expanded Seed Data (1/1 plan)

</details>

<details>
<summary>✅ v1.3 Core Features (Phases 12-15) — SHIPPED 2026-04-19</summary>

- [x] Phase 12: Judge Worker (2/2 plans)
- [x] Phase 13: Contest Data Layer (2/2 plans)
- [x] Phase 14: Contest Engine (2/2 plans)
- [x] Phase 15: Problem & User Features (2/2 plans)

</details>

<details>
<summary>✅ v1.2 CI/CD Pipeline (Phases 9-11) — SHIPPED 2026-04-18</summary>

- [x] Phase 9: Docker & Compose (3/3 plans)
- [x] Phase 10: CI Workflow (3/3 plans)
- [x] Phase 11: Flyway & Migrations (2/2 plans)

</details>

<details>
<summary>✅ v1.1 Technical Debt II (Phases 5-8) — SHIPPED 2026-04-17</summary>

- [x] Phase 5: CORS & Production Config (4/4 plans)
- [x] Phase 6: Admin Analytics (4/4 plans)
- [x] Phase 7: Code Quality Fixes (4/4 plans)
- [x] Phase 8: Management Frontend Tests (3/3 plans)

</details>

<details>
<summary>✅ v1.0 Technical Debt (Phases 1-4) — SHIPPED 2026-04-16</summary>

- [x] Phase 1: Security Foundations (3/3 plans)
- [x] Phase 2: Auth & Tests (3/3 plans)
- [x] Phase 3: Docker & Testing (3/3 plans)
- [x] Phase 4: Component Split (2/2 plans)

</details>

---

## v1.8 Technical Debt III (In Progress)

### Phase 34: Swagger UI 修复
**Goal**: Swagger UI 可通过 /swagger-ui.html 正常访问
**Depends on**: Nothing
**Requirements**: DEPS-01
**Success Criteria** (what must be TRUE):
  1. Swagger UI 页面可正常加载（HTTP 200）
  2. API 文档显示所有 REST endpoints
  3. Try-out 功能可正常发送请求
**Plans**: TBD

### Phase 35: Flyway URL 修复
**Goal**: CI workflow 中 Flyway 下载不再返回 404
**Depends on**: Nothing
**Requirements**: DEPS-02
**Success Criteria** (what must be TRUE):
  1. CI workflow run 显示 Flyway 下载成功
  2. Flyway migration job 不再因 URL 404 失败
  3. 数据库迁移在 CI 环境正常执行
**Plans**: TBD

### Phase 36: Achievement 异步化
**Goal**: Achievement 检查不再阻塞主线程
**Depends on**: Nothing
**Requirements**: PITFALL-01
**Success Criteria** (what must be TRUE):
  1. Achievement 检查在 @Async 线程执行
  2. @EventListener 在 AFTER_COMMIT 阶段触发
  3. 主线程不等待 Achievement 检查完成
**Plans**: TBD

### Phase 37: Forum Stats 真实数据
**Goal**: Admin Forum Stats 返回真实统计而非硬编码零值
**Depends on**: Nothing
**Requirements**: BUG-01, PITFALL-02
**Success Criteria** (what must be TRUE):
  1. Admin dashboard Forum Stats 显示 forum_comments 表真实 count
  2. Admin dashboard Forum Stats 显示 forum_votes 表真实 count
  3. Stats 数据随实际数据变化而更新（非硬编码）
**Plans**: TBD

---

## Next Milestone

Next milestone not yet planned. Use `/gsd-new-milestone` to start.

---
_Archived milestones: `.planning/milestones/`_
