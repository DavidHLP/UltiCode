# Roadmap: UltiCode

## Milestones

- [x] **v1.0 Technical Debt** — Phases 1-4 (shipped 2026-04-16)
- [x] **v1.1 Technical Debt II** — Phases 5-8 (shipped 2026-04-17)
- [x] **v1.2 CI/CD Pipeline** — Phases 9-11 (shipped 2026-04-18)
- [x] **v1.3 Core Features** — Phases 12-15 (shipped 2026-04-19)
- [x] **v1.4 Seed Data** — Phases 16-18 (shipped 2026-04-19)
- [x] **v1.5 Coverage** — Phases 19-25 (shipped 2026-04-20)
- [x] **v1.6 User & Social** — Phases 26-29 (shipped 2026-04-21)
- [x] **v1.7 Notifications** — Phases 30-33 (shipped 2026-04-21)
- [x] **v1.8 Technical Debt III** — Phases 34-37 (shipped 2026-04-22)
- [x] **v1.9 Performance & Quality** — Phases 38-40 (shipped 2026-04-22)
- [x] **v2.0 Dependencies & Quality** — Phases 41-44 (shipped 2026-04-22)
- [ ] **v3.0 Platform Quality & UX** — Phases 45-47

## Phases

- [ ] **Phase 45: API Documentation** — SpringDoc 2.6.0 + @ApiResponse annotations + Swagger UI
- [ ] **Phase 46: Sandbox Hardening** — bubblewrap bug fixes + per-language limits + namespace isolation tests
- [ ] **Phase 47: Frontend i18n** — vue-i18n upgrade + useLocale composable + language switcher

---

## Phase Details

### Phase 45: API Documentation

**Goal**: SpringDoc annotations complete with accessible Swagger UI

**Depends on**: Nothing (standalone backend work)

**Requirements**: API-01, API-02, API-03

**Success Criteria** (what must be TRUE):
1. pom.xml springdoc.version is 2.6.0 (springdoc 2.8.17 requires Spring Boot 3.5+; 2.6.0 is last compatible with SB 3.2.5)
2. Critical endpoints (auth, user, problem, submission, contest) have @Operation and @ApiResponse annotations
3. Swagger UI loads and displays API docs at /swagger-ui.html
4. OpenAPI 3.0.1 spec is valid and includes all annotated endpoints

**Plans**: 1 plan

---

### Phase 46: Sandbox Hardening

**Goal**: Code execution sandbox is hardened with correct bubblewrap invocation and resource limits

**Depends on**: Phase 45

**Requirements**: SAND-01, SAND-02, SAND-03, SAND-04, SAND-05

**Success Criteria** (what must be TRUE):
1. bubblewrap command uses --read-only after --tmpfs flag (not before)
2. seccomp profile path is correctly volume-mounted into container
3. Each language (java, python, cpp, go, rust) has distinct timeout and memory limits
4. /tmp is mounted as tmpfs with size=64m enforcement
5. Integration test validates namespace isolation (user/pid/network namespaces separate)

**Plans**: 1 plan

---

### Phase 47: Frontend i18n

**Goal**: Consistent internationalization across Console and Management with lazy-loaded translations and language switcher

**Depends on**: Phase 46

**Requirements**: I18N-01, I18N-02, I18N-03, I18N-04, I18N-05

**Success Criteria** (what must be TRUE):
1. Management frontend uses vue-i18n 11.3.2 (matching Console)
2. useLocale composable persists selected locale to localStorage and syncs with backend
3. Non-active locale translation files load on-demand (dynamic import)
4. Console header displays language switcher toggling between zh-CN and en-US
5. Missing translation keys produce console warnings in development (missingWarn enabled)

**Plans**: 3 plans

---

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
| 34-37 | v1.8 | 4 | Complete | 2026-04-22 |
| 38-40 | v1.9 | 4 | Complete | 2026-04-22 |
| 41-44 | v2.0 | 4 | Complete | 2026-04-22 |
| 45 | v3.0 | 1 | Complete | 2026-04-22 |
| 46 | v3.0 | 0 | Not started | - |
| 47 | v3.0 | 3 | Not started | - |

---

## Completed Milestones

<details>
<summary>✅ v2.0 Dependencies & Quality (Phases 41-44) — SHIPPED 2026-04-22</summary>

- [x] Phase 41: Dependency Upgrades (1/1 plan) — completed 2026-04-22
- [x] Phase 42: Rate Limiting E2E (1/1 plan) — completed 2026-04-22
- [x] Phase 43: JaCoCo Threshold Raise (1/1 plan) — completed 2026-04-22
- [x] Phase 44: Testcontainers Upgrade (1/1 plan) — completed 2026-04-22

</details>

<details>
<summary>✅ v1.9 Performance & Quality (Phases 38-40) — SHIPPED 2026-04-22</summary>

- [x] Phase 38: Achievement N+1 Query Optimization (1/1 plan) — completed 2026-04-22
- [x] Phase 39: Follow System Optimization (1/1 plan) — completed 2026-04-22
- [x] Phase 40: JaCoCo Coverage Enforcement (2/2 plans) — completed 2026-04-22

</details>

<details>
<summary>✅ v1.8 Technical Debt III (Phases 34-37) — SHIPPED 2026-04-22</summary>

- [x] Phase 34: Swagger UI 修复 (1/1 plan) — completed 2026-04-21
- [x] Phase 35: Flyway URL 修复 (1/1 plan) — completed 2026-04-22
- [x] Phase 36: Achievement 异步化 (1/1 plan) — completed 2026-04-22
- [x] Phase 37: Forum Stats 真实数据 (1/1 plan) — completed 2026-04-22

</details>

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

## Next Milestone

_Archived milestones: `.planning/milestones/`_

---

_Generated: 2026-04-22_
