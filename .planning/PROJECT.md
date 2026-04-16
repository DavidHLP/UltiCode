# UltiCode 技术债务清偿

## Current State

**Shipped:** v1.0 Technical Debt Remediation (2026-04-16)
**Status:** All 9 CRITICAL/HIGH technical debt items resolved across 4 phases (11 plans, 20 tasks)

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

_(待 v1.1 里程碑定义)_

### Out of Scope

- 新功能开发（比赛系统增强、推荐系统完善等）— 本轮只清偿技术债务
- CI/CD 流水线搭建 — 不在本次范围内
- 第三方安全审计 — 自查修复，不引入外部审计
- UI/UX 重设计 — 拆分组件时仅做结构优化

## Context

**代码库现状 (post-v1.0)：**
- 后端 Spring Boot 3.5 + MyBatis-Plus，26+ 模块
- 前端 Console (Vue 3) ~200+ 源文件，Management (Vue 3) ~100+ 源文件
- v1.0 变更: 378 files changed, +31,958 / -18,490 LOC
- 安全基线: CSRF → Spring Security, XSS → OWASP Encoder, JWT → fail-fast validation
- 测试: Testcontainers BOM 1.21.3, 71 新测试覆盖 auth/submission/code-execution
- 前端: 所有 Vue 组件 < 500 行, co-located composables 模式建立

**已知风险 (deferred to v2)：**
- CORS 允许来源硬编码 (SEC-07)
- 部分宽泛 catch(Exception e) 未修复 (QUAL-02)
- 前端测试覆盖率仍低 (TEST-02/03)
- Admin 功能仍有 5 个 TODO 桩 (FUNC-02)

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
*Last updated: 2026-04-16 after v1.0 milestone*
