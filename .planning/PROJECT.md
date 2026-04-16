# UltiCode 技术债务清偿

## Current Milestone: v1.1 Technical Debt Remediation II

**Goal:** 清偿 v1.0 延后的全部 19 项 MEDIUM/LOW 级别技术债务，使平台达到生产就绪状态

**Target features (19 items, 6 categories):**
- 安全配置加固: SEC-07, SEC-08, CONF-01, CONF-02, CONF-03
- Admin 功能补全: AUDIT-01, FUNC-02, FUNC-03
- 性能优化: PERF-01, PERF-02
- 代码质量: QUAL-02, QUAL-03, QUAL-04
- 依赖清理: DEP-01, DEP-02, DEP-03
- 测试补充: TEST-02, TEST-03, TEST-04

## Current State

**Shipped:** v1.0 Technical Debt Remediation (2026-04-16)
**Status:** v1.1 in progress — 19 MEDIUM/LOW technical debt items

## What This Is

系统性修复 UltiCode 在线编程平台代码库中已识别的 28 项技术债务，涵盖安全漏洞、功能缺失、性能瓶颈、代码质量和配置缺陷。v1.0 已完成全部 9 项 CRITICAL 和 HIGH 级别修复，包括安全过滤链（CSRF/XSS/JWT）、核心功能（密码重置/Rejudge/Docker 沙箱加固）、测试覆盖率和前端组件拆分。剩余 19 项 MEDIUM/LOW 级别债务延后至未来里程碑。

## Core Value

平台安全性和功能完整性——用户能安全使用所有已有功能，不存在已知的 CSRF 绕过、JWT 伪造、功能占位符或数据不准确的问题。

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

### Active

**安全配置加固:**
- SEC-07: CORS 允许来源外部化为环境变量
- SEC-08: XssFilter 停止清理请求 Header
- CONF-01: JWT Cookie Secure 标志在生产环境默认为 true
- CONF-02: 创建 application-prod.yml 生产配置（禁用 Swagger、actuator 端点）
- CONF-03: docker-compose.yml 移除弱默认密码

**Admin 功能补全:**
- AUDIT-01: BackupController 使用实际认证用户 ID 替代硬编码 "system"
- FUNC-02: 实现 5 个 Admin TODO 桩（论坛社区、题目计数、论坛数据、审核详情）
- FUNC-03: 实现审核平均解决时间计算（当前硬编码 0.0）

**性能优化:**
- PERF-01: 测试用例批量执行替代逐个 Docker 容器启动
- PERF-02: Admin Analytics 使用数据库聚合替代全量实体加载

**代码质量:**
- QUAL-02: 修复 30+ 处宽泛 catch(Exception e) 为具体异常类型
- QUAL-03: 拆分 AdminAnalyticsServiceImpl（553 行）
- QUAL-04: 清理生产代码中的 console.log 语句

**依赖清理:**
- DEP-01: 移除 git 跟踪的 management/.env
- DEP-02: 替换 SNAPSHOT 依赖为稳定版本
- DEP-03: 评估并移除 SockJS 客户端依赖

**测试补充:**
- TEST-02: 补充前端 Console 关键路径测试（API 层、stores）
- TEST-03: 补充前端 Management 关键路径测试
- TEST-04: 添加后端 Controller 集成测试（@WebMvcTest）

### Out of Scope

- 新功能开发（比赛系统增强、推荐系统完善等）— v1.x 系列只清偿技术债务
- CI/CD 流水线搭建 — 独立里程碑处理
- 第三方安全审计 — 自查修复，不引入外部审计
- UI/UX 重设计 — 拆分组件时仅做结构优化

## Context

**代码库现状 (post-v1.0, pre-v1.1)：**
- 后端 Spring Boot 3.5 + MyBatis-Plus，26+ 模块
- 前端 Console (Vue 3) ~200+ 源文件，Management (Vue 3) ~100+ 源文件
- v1.0 变更: 378 files changed, +31,958 / -18,490 LOC
- 安全基线: CSRF → Spring Security, XSS → OWASP Encoder, JWT → fail-fast validation
- 测试: Testcontainers BOM 1.21.3, 71 新测试覆盖 auth/submission/code-execution
- 前端: 所有 Vue 组件 < 500 行, co-located composables 模式建立

**v1.1 待解决 (19 items from v1.0 deferred)：**
- 安全配置: CORS 外部化, Header 清理, 生产配置 (5 items)
- Admin: TODO 桩实现, 审核时间计算, 备份用户 ID (3 items)
- 性能: 批量测试执行, 数据库聚合 (2 items)
- 代码质量: 异常处理, 大文件拆分, console.log (3 items)
- 依赖: .env 清理, SNAPSHOT 替换, SockJS 评估 (3 items)
- 测试: Console/Management 前端测试 + Controller 集成测试 (3 items)

## Constraints

- **安全修复优先**：CRITICAL 和 HIGH 级安全问题必须在其他修复之前完成 ✓ (v1.0 done)
- **修复 + 测试同步**：每个修复必须带对应测试 ✓ (v1.0 done)
- **不引入新依赖**：仅新增 OWASP Java Encoder ✓
- **向后兼容**：API 变更保持前端兼容 ✓
- **分阶段交付**：4 个阶段独立可验证 ✓

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 按严重程度递减排序 | 安全风险影响最大，必须最先处理 | ✓ Good — 安全漏洞全部修复 |
| 修复同步测试 | 测试覆盖率低是已知问题 | ✓ Good — 71 新测试，Phase 3 专项 |
| 不引入新依赖 | 减少变更面 | ✓ Good — 仅 OWASP Encoder |
| 分阶段交付 | 28 个问题一次修完风险太高 | ✓ Good — 4 phase, 11 plan |
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
*Last updated: 2026-04-16 after Phase 06 (Admin Functionality & Performance)*
