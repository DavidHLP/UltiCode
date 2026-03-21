# NestJS 到 Spring Boot 迁移 - 总计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 NestJS 后端完整迁移到 Spring Boot，保持与前端 Vue 3 的 API 完全兼容。

**Architecture:** Spring Boot 3.5 + MyBatis-Plus + Spring Security + Redis + Redisson

**Tech Stack:** Spring Boot 3.5.12, MyBatis-Plus 3.5.5, jjwt 0.12.5, Redisson 3.27.0, SpringDoc 2.3.0

---

## 迁移阶段概览

```
Phase 1: 基础设施层 (8 天) ─────────────────────────────────►
    │  ├── 项目结构、依赖配置
    │  ├── 统一响应格式、错误码
    │  ├── Spring Security + JWT
    │  ├── MyBatis-Plus 配置
    │  ├── Redis/Redisson 配置
    │  └── SpringDoc API 文档
    │
Phase 2: 核心业务模块 (12 天) ─────────────────────────────►
    │  ├── User 模块 (2天)
    │  ├── Auth 模块 (2天)
    │  ├── Problem 模块 (3天)
    │  ├── Submission 模块 (3天)
    │  └── Solution 模块 (2天)
    │
Phase 3: 高级功能 (10 天) ─────────────────────────────────►
    │  ├── Contest 模块 (3天)
    │  ├── Forum 模块 (3天)
    │  ├── WebSocket 实时通信 (2天)
    │  └── 任务队列 (2天)
    │
Phase 4: 管理功能 (7 天) ──────────────────────────────────►
    │  ├── Admin 模块 (2天)
    │  ├── Moderation 模块 (2天)
    │  ├── Notification 模块 (1天)
    │  └── 其他模块 (2天)
    │
Phase 5: 辅助模块 (5 天) ──────────────────────────────────►
    │  ├── Recommendation 模块 (2天)
    │  ├── Search 模块 (1天)
    │  ├── EdgeOperations 模块 (1天)
    │  └── 其他 (1天)
    │
总计: 42 天 (约 8-10 周，含缓冲)
```

---

## 详细计划文档

| 阶段 | 计划文档 | 状态 |
|------|----------|------|
| Phase 1 | [2026-03-21-phase1-infrastructure.md](./2026-03-21-phase1-infrastructure.md) | ✅ 已完成 |
| Phase 2 | [2026-03-21-phase2-core-modules.md](./2026-03-21-phase2-core-modules.md) | ✅ 已完成 |
| Phase 3 | [2026-03-21-phase3-advanced-features.md](./2026-03-21-phase3-advanced-features.md) | ✅ 已完成 |
| Phase 4 | [2026-03-21-phase4-admin-features.md](./2026-03-21-phase4-admin-features.md) | ✅ 已完成 |
| Phase 5 | [2026-03-21-phase5-auxiliary-modules.md](./2026-03-21-phase5-auxiliary-modules.md) | ✅ 已完成 |
| **Phase 6** | [2026-03-22-phase6-remaining-modules.md](./2026-03-22-phase6-remaining-modules.md) | ⏳ 待执行 |

---

## 模块迁移对照表

| NestJS 模块 | Spring Boot 模块 | 阶段 | 优先级 |
|-------------|-----------------|------|--------|
| - | common (基础设施) | Phase 1 | P0 |
| - | security (认证) | Phase 1 | P0 |
| user | user | Phase 2 | P0 |
| auth | auth | Phase 2 | P0 |
| problem | problem | Phase 2 | P0 |
| submission | submission | Phase 2 | P0 |
| solution | solution | Phase 2 | P0 |
| contest | contest | Phase 3 | P1 |
| forum | forum | Phase 3 | P1 |
| notification | notification | Phase 4 | P1 |
| admin | admin | Phase 4 | P1 |
| moderation | moderation | Phase 4 | P1 |
| bookmark | bookmark | Phase 4 | P2 |
| problem-list | problem-list | Phase 4 | P2 |
| recommendation | recommendation | Phase 5 | P2 |
| search | search | Phase 5 | P2 |
| edge-operations | edge-operations | Phase 5 | P2 |
| achievement | achievement | Phase 5 | P3 |
| subscription | subscription | Phase 5 | P3 |
| backup | backup | Phase 5 | P3 |
| email | email | Phase 5 | P3 |
| i18n | i18n | Phase 5 | P3 |
| monitoring | monitoring | Phase 5 | P3 |
| test-case | test-case (并入 problem) | Phase 2 | P0 |
| view | view (并入各模块) | - | - |
| vote | vote (并入 edge-operations) | Phase 5 | P2 |
| cache | cache (并入 common) | Phase 1 | P0 |
| config | config (并入 common) | Phase 1 | P0 |

---

## 执行顺序

### 1. 执行 Phase 1

```bash
# 参考 Phase 1 计划文档执行
# 完成后验收: 项目可启动，Swagger UI 可访问
```

### 2. 执行 Phase 2

```bash
# 参考 Phase 2 计划文档执行
# 完成后验收: 核心 API 可正常使用
```

### 3. 执行 Phase 3-5

根据 Phase 1-2 的完成情况，创建后续阶段的详细计划。

---

## 验收标准

### Phase 1 验收

- [ ] 项目可通过 `./mvnw spring-boot:run` 启动
- [ ] 访问 `/swagger-ui.html` 显示 API 文档
- [ ] 访问 `/api-docs` 返回 OpenAPI JSON
- [ ] JWT 认证流程完整
- [ ] Redis 连接正常

### Phase 2 验收

- [ ] 用户注册/登录正常
- [ ] 题目 CRUD 正常
- [ ] 代码提交正常
- [ ] 题解 CRUD 正常

### Phase 3 验收

- [ ] 竞赛功能正常
- [ ] 论坛功能正常
- [ ] WebSocket 连接正常
- [ ] 任务队列正常

### Phase 4 验收

- [ ] 管理后台功能正常
- [ ] 内容审核流程正常
- [ ] 通知推送正常

### Phase 5 验收

- [ ] 推荐系统正常
- [ ] 搜索功能正常
- [ ] 所有辅助功能正常

---

## 风险管理

| 风险 | 缓解措施 | 负责人 |
|------|----------|--------|
| API 不兼容 | 严格参照 NestJS 响应格式，使用相同错误码 | 开发者 |
| WebSocket 协议变更 | 提供前端迁移指南，使用 STOMP 协议 | 开发者 |
| 时间延期 | 每阶段预留 1-2 天缓冲时间 | 项目负责人 |
| 数据库迁移 | 共享现有数据库，不做 schema 变更 | 开发者 |

---

## 开始执行

执行以下命令开始 Phase 1:

```bash
# 1. 查看 Phase 1 计划
cat docs/superpowers/plans/2026-03-21-phase1-infrastructure.md

# 2. 创建项目目录
cd backend-spring
mkdir -p src/main/java/com/ulticode/{common,security,infrastructure,modules}
mkdir -p src/main/resources
mkdir -p src/test/java/com/ulticode

# 3. 按照 Phase 1 计划逐步执行
```
