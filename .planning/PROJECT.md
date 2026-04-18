# UltiCode 技术债务清偿

## Current Milestone: v1.3 Core Features

**Goal:** 补全四大核心功能（判题、竞赛、题目浏览、用户中心）的关键缺失，使平台可完整运行

**Target features:**
- 判题系统：实现 Judge Worker，解决提交永久 Pending 问题
- 竞赛系统：补全后端实体、调度器、Rating 计算、Admin API
- 题目浏览：随机题目、通过率计算、Admin 批量操作
- 用户中心：全局排名、公开主页、前后端 API 一致性修复

**Key context:** 前端 UI 大部分已就绪，主要工作量在后端 API 补全

## Current State

**In Progress:** v1.3 Core Features (started 2026-04-18)
**Status:** 定义需求和路线图阶段。前三个里程碑（v1.0~v1.2）已完成，技术债务清偿和 CI/CD 管道均已就绪。

## What This Is

系统性修复 UltiCode 在线编程平台代码库中已识别的技术债务，并建立自动化 CI/CD 流水线。涵盖安全漏洞修复、功能缺失填补、性能优化、代码质量提升、配置加固、测试覆盖和自动化部署。v1.0~v1.2 共完成 11 个 phase、34 个 plan，全面清偿了平台的技术债务并建立了持续交付能力。

## Core Value

平台安全性、功能完整性和交付自动化——用户能安全使用所有功能，每个 PR 都经过自动化验证，每次合并都自动部署。

## Requirements

### Validated

- ✓ SEC-06: XssFilter 替换为输出编码 (OWASP Encoder) — v1.0 Phase 1
- ✓ SEC-01: CSRF 迁移至 Spring Security CsrfValidationFilter — v1.0 Phase 1
- ✓ SEC-05: JWT Secret @PostConstruct 启动校验 — v1.0 Phase 1
- ✓ SEC-03: 移除 UserDetailsServiceImpl 占位符 — v1.0 Phase 1
- ✓ SEC-02: 密码重置邮件实际发送 (BCrypt token + DB storage) — v1.0 Phase 2
- ✓ FUNC-01: Admin Rejudge 批量操作 + 限流 — v1.0 Phase 2
- ✓ SEC-04: Docker 沙箱 seccomp + cap-drop ALL — v1.0 Phase 2
- ✓ TEST-01: 71 个测试 (48 单元 + 18 模块 + 5 集成) 覆盖关键模块 — v1.0 Phase 3
- ✓ QUAL-01: 14 个超大 Vue 组件拆分为 59 个子组件 + 14 composables — v1.0 Phase 4
- ✓ SEC-07: CORS 允许来源外部化为环境变量 — v1.1 Phase 5
- ✓ SEC-08: XssFilter 停止清理请求 Header — v1.1 Phase 5
- ✓ CONF-01: JWT Cookie Secure 标志在生产环境默认为 true — v1.1 Phase 5
- ✓ CONF-02: 创建 application-prod.yml 生产配置 — v1.1 Phase 5
- ✓ CONF-03: docker-compose.yml 移除弱默认密码 — v1.1 Phase 5
- ✓ AUDIT-01: BackupController 使用实际认证用户 ID — v1.1 Phase 6
- ✓ FUNC-02: 实现 5 个 Admin TODO 桩 — v1.1 Phase 6
- ✓ FUNC-03: 实现审核平均解决时间计算 — v1.1 Phase 6
- ✓ PERF-01: 测试用例批量执行 — v1.1 Phase 6
- ✓ PERF-02: Admin Analytics 数据库聚合 — v1.1 Phase 6
- ✓ QUAL-02: 修复 26 处宽泛 catch 块 — v1.1 Phase 7
- ✓ QUAL-03: 拆分 AdminAnalyticsServiceImpl 为 3 个服务 — v1.1 Phase 7
- ✓ QUAL-04: 清理前端 console.log — v1.1 Phase 7
- ✓ DEP-01: 移除 git 跟踪的 management/.env — v1.1 Phase 7
- ✓ DEP-02: 替换 SNAPSHOT 依赖为 1.0.0 — v1.1 Phase 7
- ✓ DEP-03: 评估 SockJS 依赖 — v1.1 Phase 7
- ✓ TEST-02: Console 前端关键路径测试 (35 tests) — v1.1 Phase 8
- ✓ TEST-03: Management 前端关键路径测试 (23 tests) — v1.1 Phase 8
- ✓ TEST-04: 后端 Controller @WebMvcTest 集成测试 (12 tests) — v1.1 Phase 8
- ✓ FOUND-01~06: Dockerfile 修复、.dockerignore、CSP、CI profile、secrets mapping — v1.2 Phase 9
- ✓ CI-01~06: 统一 ci.yml with paths-filter、parallel jobs、build caching — v1.2 Phase 9
- ✓ CD-01~05: GHCR push、image tagging、docker-compose.prod.yml、SSH deploy、ordered restart — v1.2 Phase 10
- ✓ HARD-01: Dependabot 配置 (Actions + npm + Maven) — v1.2 Phase 11
- ✓ HARD-02: Rollback workflow (workflow_dispatch) — v1.2 Phase 11

### Active

(No active requirements — next milestone TBD)

### Out of Scope

- 新功能开发（比赛系统增强、推荐系统完善等）— v1.x 系列只清偿技术债务
- 第三方安全审计 — 自查修复，不引入外部审计
- UI/UX 重设计 — 拆分组件时仅做结构优化
- Kubernetes 部署 — Docker Compose 先满足需求
- Branch protection rules — 可手动在 GitHub 配置
- Recommendation service CI/CD — Optional service, not in docker-compose.prod.yml scope
- Blue-green / canary deployment — Overkill for single VPS deployment
- Multi-environment (staging + prod) — Single production environment sufficient for now

## Context

**代码库现状 (post-v1.2)：**
- 后端 Spring Boot 3.5 + MyBatis-Plus，26+ 模块
- 前端 Console (Vue 3) ~200+ 源文件，Management (Vue 3) ~100+ 源文件
- v1.0 变更: 378 files changed, +31,958 / -18,490 LOC
- v1.1 变更: Phases 5-8, 15 plans, 141 total tests (71 v1.0 + 70 v1.1)
- v1.2 变更: Phases 9-11, 8 plans, 259 files changed, +13,074 / -26,140 LOC
- 安全基线: CSRF/XSS/JWT 全链路加固, 生产配置 profile 就绪
- 测试: Testcontainers BOM 1.21.3, 141 tests (71 + 35 console + 23 management + 12 backend)
- 前端: 所有 Vue 组件 < 500 行, console.log 清理完毕, SNAPSHOT deps → 1.0.0
- CI/CD: ci.yml (path-filtered parallel jobs), docker-publish.yml (GHCR), deploy.yml (SSH + ordered restart), rollback.yml (manual), Dependabot v2
- 所有 28+19 项技术债务及 CI/CD 需求已清偿

## Constraints

- **安全修复优先**：CRITICAL 和 HIGH 级安全问题必须在其他修复之前完成 ✓ (v1.0 done)
- **修复 + 测试同步**：每个修复必须带对应测试 ✓ (v1.0 done)
- **不引入新依赖**：仅新增 OWASP Java Encoder ✓
- **向后兼容**：API 变更保持前端兼容 ✓
- **分阶段交付**：11 个阶段独立可验证 ✓
- **Docker Compose 先于 Kubernetes** — 当前规模足够 ✓ (v1.2 done)
- **GitHub-hosted runners** — 项目规模适合 ✓ (v1.2 done)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 按严重程度递减排序 | 安全风险影响最大，必须最先处理 | ✓ Good — 安全漏洞全部修复 |
| 修复同步测试 | 测试覆盖率低是已知问题 | ✓ Good — 71 新测试，Phase 3 专项 |
| 不引入新依赖 | 减少变更面 | ✓ Good — 仅 OWASP Encoder |
| 分阶段交付 | 28 个问题一次修完风险太高 | ✓ Good — 11 phases, 34 plans |
| SEC-06 先于 SEC-01 | XssFilter header 损坏阻塞 CSRF token | ✓ Good — 正确依赖顺序 |
| TEST-01 独立阶段 | 全面验证 Phase 1-2 安全修复 | ✓ Good — Testcontainers 集成测试 |
| QUAL-01 最后执行 | 文件数最多，零安全影响，避免合并冲突 | ✓ Good — 无冲突完成 |
| D-17: Redis session revocation | 密码修改后立即撤销所有会话 | ✓ Good |
| D-18: 新 token 覆盖旧 token | 防止多次 forgot-password 积累 | ✓ Good |
| D-19: 延迟优先队列 | FIFO + 限流足够保护 | ⚠ Revisit — 规模增长后可能需优先级 |
| D-20: 仅 5 种语言 | Go 不在支持范围 | ✓ Good |
| Manual MyBatis + Testcontainers | 避免 @SpringBootTest 加载 Nacos/Dubbo | ✓ Good — 隔离性好 |
| Co-located composables 模式 | views/{feature}/composables/ + components/ | ✓ Good — 前端标准模式 |
| Dialog state stays in parent | D-04 保持父组件控制 | ✓ Good |
| CORS 外部化到 CorsProperties | 环境变量驱动，非硬编码 | ✓ Good — v1.1 Phase 5 |
| AdminAnalytics 拆分为 facade + 3 服务 | 495→3 focused services | ✓ Good — v1.1 Phase 7 |
| Vitest 独立配置 per frontend | Console/Management 各自测试配置 | ✓ Good — v1.1 Phase 8 |
| Batch Docker test execution | 单容器多测试用例 | ✓ Good — v1.1 Phase 6 |
| 统一 ci.yml 替代分散工作流 | dorny/paths-filter monorepo 路径检测 | ✓ Good — v1.2 Phase 9 |
| GHCR + SHA+latest 双标签 | 可追溯 + 可回滚 | ✓ Good — v1.2 Phase 10 |
| Backend-first ordered restart | 后端健康检查通过再启动前端 | ✓ Good — v1.2 Phase 10 |
| application-ci.yml profile | 避免 Testcontainers Docker-in-Docker | ✓ Good — v1.2 Phase 9 |
| Dependabot v2 grouped updates | 减少 PR 噪音，weekly 分组 | ✓ Good — v1.2 Phase 11 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-18 after v1.2 milestone*
