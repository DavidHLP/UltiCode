# UltiCode 技术债务清偿

## What This Is

系统性修复 UltiCode 在线编程平台代码库中已识别的 28 项技术债务，涵盖安全漏洞、功能缺失、性能瓶颈、代码质量和配置缺陷。基于 `.planning/codebase/CONCERNS.md` 中完整审计结果，按严重程度从 CRITICAL → HIGH → MEDIUM → LOW 分阶段修复，每个修复同步交付测试。

## Core Value

平台安全性和功能完整性——用户能安全使用所有已有功能，不存在已知的 CSRF 绕过、JWT 伪造、功能占位符或数据不准确的问题。

## Requirements

### Validated

<!-- 已有且正常工作的功能 -->

- ✓ 用户注册/登录（JWT + CSRF）— existing
- ✓ 题目浏览、提交代码、查看结果 — existing
- ✓ 比赛系统 — existing
- ✓ 论坛帖子 CRUD — existing
- ✓ 管理后台基础功能 — existing
- ✓ Docker 沙箱代码执行 — existing
- ✓ WebSocket 实时通知 — existing
- ✓ 前端 Console（Vue 3 + Tailwind）— existing
- ✓ 前端 Management（Vue 3 + Tailwind）— existing
- ✓ Spring Boot 后端（MyBatis-Plus + Redis）— existing
- ✓ Flyway 数据库迁移 — existing

### Active

<!-- 本次要修复的 28 项技术债务，按严重程度排列 -->

**CRITICAL（4 项）：**
- [ ] SEC-01: 修复 CSRF 在 Spring Security 框架层被全局禁用的问题，确保自定义拦截器覆盖所有状态变更端点
- [ ] SEC-02: 实现密码重置邮件发送功能（当前只打日志不发送）
- [ ] SEC-03: 实现或移除 UserDetailsServiceImpl 占位符（当前始终抛异常）
- [ ] FUNC-01: 实现 Admin Rejudge 功能（当前为 TODO 占位符）

**HIGH（5 项）：**
- [ ] SEC-04: 加强 Docker 沙箱隔离（添加 seccomp profile、cap-drop ALL）
- [ ] SEC-05: JWT Secret 启动校验（空 secret 时阻止应用启动）
- [ ] SEC-06: 替换 XssFilter 的正则清理为正确的输出编码
- [ ] QUAL-01: 拆分 14 个超过 600 行的 Vue 组件
- [ ] TEST-01: 提升后端关键模块测试覆盖率（auth、submission、CodeExecution）

**MEDIUM（11 项）：**
- [ ] SEC-07: CORS 允许来源外部化为环境变量
- [ ] SEC-08: XssFilter 停止清理请求 Header
- [ ] AUDIT-01: BackupController 使用实际认证用户 ID 替代硬编码 "system"
- [ ] PERF-01: 测试用例批量执行替代逐个 Docker 容器启动
- [ ] PERF-02: Admin Analytics 使用数据库聚合替代全量实体加载
- [ ] DEP-01: 移除 git 跟踪的 management/.env
- [ ] DEP-02: 替换 SNAPSHOT 依赖为稳定版本
- [ ] CONF-01: JWT Cookie Secure 标志在生产环境默认为 true
- [ ] FUNC-02: 实现 5 个 Admin TODO 桩（论坛社区、题目计数、论坛数据、审核详情）
- [ ] QUAL-02: 修复 30+ 处宽泛 catch(Exception e) 为具体异常类型
- [ ] QUAL-03: 拆分 AdminAnalyticsServiceImpl（553 行）

**LOW（8 项）：**
- [ ] CONF-02: 创建 application-prod.yml 生产配置（禁用 Swagger、actuator 端点）
- [ ] CONF-03: docker-compose.yml 移除弱默认密码
- [ ] QUAL-04: 清理生产代码中的 console.log 语句
- [ ] DEP-03: 评估并移除 SockJS 客户端依赖
- [ ] TEST-02: 补充前端 Console 关键路径测试（API 层、stores）
- [ ] TEST-03: 补充前端 Management 关键路径测试
- [ ] TEST-04: 添加后端 Controller 集成测试（@WebMvcTest）
- [ ] FUNC-03: 实现审核平均解决时间计算（当前硬编码 0.0）

### Out of Scope

- 新功能开发（比赛系统增强、推荐系统完善等）— 本轮只清偿技术债务
- UI/UX 重设计 — 除非拆分大组件时必要
- 性能优化基准测试 — 只修已识别的具体问题
- CI/CD 流水线搭建 — 不在本次范围内
- 第三方安全审计 — 自查修复，不引入外部审计

## Context

**代码库现状：**
- 后端 Spring Boot 3.5 + MyBatis-Plus，26+ 模块
- 前端 Console（Vue 3）约 200+ 源文件，Management（Vue 3）约 100+ 源文件
- 代码库映射完成于 2026-04-13，详见 `.planning/codebase/`
- CONCERNS.md 审计发现 28 项问题，其中 4 CRITICAL、5 HIGH、11 MEDIUM、8 LOW

**已知风险：**
- CSRF 框架层禁用 + 自定义拦截器的覆盖范围不明确
- JWT Secret 可能为空导致认证完全可绕过
- 密码重置流程不可用（用户无法恢复账号）
- 测试覆盖率极低（Console 7%, Management 1%, Backend ~15%）

## Constraints

- **安全修复优先**：CRITICAL 和 HIGH 级安全问题必须在其他修复之前完成
- **修复 + 测试同步**：每个修复必须带对应测试，不允许只修不测
- **不引入新依赖**：优先使用已有依赖解决问题（如 DOMPurify 已在前端依赖中）
- **向后兼容**：API 变更需保持现有前端兼容（内部重构不影响外部接口）
- **分阶段交付**：每阶段独立可验证，可独立合入

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 按严重程度递减排序 | 安全风险影响最大，必须最先处理 | — Pending |
| 修复同步测试 | 测试覆盖率低是已知问题，不趁修债务时补测试只会更难 | — Pending |
| 不引入新依赖 | 减少变更面，降低引入新问题的风险 | — Pending |
| 分阶段交付 | 28 个问题一次性修完风险太高，分阶段可控制回滚范围 | — Pending |

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
*Last updated: 2026-04-14 after initialization*
