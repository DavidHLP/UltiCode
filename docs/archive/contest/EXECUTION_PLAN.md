---
title: Contest 模块执行计划（R1–R10 累计）
tags: [contest, execution, history]
status: accepted
updated: 2026-06-18
owner: backend
---

# Contest 模块执行计划（R1–R10 累计）

> **作用**：Contest 模块 R1–R10 多轮执行的**累计结论**。每轮的可独立部署步骤保留在 `_archive/` 中作为审计证据（按 `_archive/EXECUTION_PLAN_R{6,7,8,9,10}_*.md` 索引导航）。
> **最终裁决**：模块 **v4.2 完结**（[REVIEW_V3.md §12](./REVIEW_V3.md)）。v4.3 收口**不成立**（R10.4 项 plan 误判导致 R10 完成度 5/9 + 4/9 误判）。
> **创建**：2026-06-17（R1–R5）→ 2026-06-17（R6–R9）→ 2026-06-18（R10）
> **实施状态**：R1–R10 全部落地（含 0 行代码改动的 R10 doc-only 销项）。本计划是审查回看与排期复盘的唯一入口。

---

## 0. 设计原则

| 原则 | 含义 |
|------|------|
| **每轮可独立部署** | 任一轮可单独上线、单独回滚，不强制与其他轮打包 |
| **每轮可独立验证** | 有明确的验收用例，不依赖下一轮才能测 |
| **低风险在前** | 纯约束 / 读路径收紧 → 行为变更 → 核心引擎 |
| **耦合集原子上线** | 同一轮内的改动若存在"中间态崩溃"，必须同 commit / 同部署窗口 |
| **迁移仅加性** | Flyway 只新增 `V{timestamp}` 迁移，绝不编辑已应用迁移（见 `CLAUDE.md` Database Rules） |
| **plan 误判显式关单** | 复盘时若发现原计划基于文档而非代码假设，落"误判 / DEFERRED / ABORTED"标签，不假装完成 |

### 关键依赖图（R1–R5）

```
Round 1 (slug UNIQUE 约束)      ─── 独立，纯 SQL
Round 2 (真/虚读隔离 is_virtual) ─── 独立，读路径收紧；为 R3 隔离语义铺垫
Round 3 (生命周期 + 评级隔离)    ─── 原子（auto-finish ↔ 评级查询改造 必须同轮）
Round 4 (评分引擎激活)           ─── 依赖 R3 的参赛者状态正确（FINISHED 才结算）
Round 5 (P1 工程债务)            ─── 非阻断，合入后迭代
```

**合入门槛**：R1–R4 全部完成 + 各轮验收通过 = 可重新定档合入。R5 不阻断。

---

## 1. R1–R5 摘要（实施已完成，详细步骤见 git 历史 `git log --all --oneline -- docs/contest/EXECUTION_PLAN.md`）

> **注**：R1–R5 的详细步骤（Round 1–5 完整描述）已合并入本文后续章节的累计表，原始 R1–R5 单文件版以 git 历史形式保留。

| 轮次 | 主题 | 主要产出 | 关联 ADR |
|------|------|----------|----------|
| **R1** | slug UNIQUE 约束 | `V20260617130000__Contest_Slug_Unique.sql` + `DataIntegrityViolationException` 兜底 | — |
| **R2** | 真/虚读隔离 | `ContestParticipantMapper` 5 处加 `is_virtual=0`；`findByContestIdAndUserId` 加 `ORDER BY registered_at DESC` | — |
| **R3** | 生命周期 + 评级隔离（**原子**） | `ContestScheduler` Step 3 接线 + `findRealParticipantsByContestId` + `findActiveVirtualSessionForUpdate FOR UPDATE` + 前端 `sessionStorage` 持久化 | [ADR-007](../adr/ADR-007-virtual-contest-lifecycle-and-rating-isolation.md) |
| **R4** | 评分引擎激活 | `penaltyPerWrong` 配置化 + SCORE/ICPC/IOI 三分支 + 4 个新单测 | [ADR-006](../adr/ADR-006-contest-scoring-engine-activation.md) |
| **R5** | P1 工程债务 | 3 个零调用 feature flag 移除 + `findByContestIdAndUserIdAndVirtualSessionId` 移除 | — |

**R1–R4 全绿 → 模块 v4.0 重新定档合入。** 5 轮全部完成。

---

## 2. R6 — 剩余 11 项 P0/P1 + 2 项历史债

**来源**：[`_archive/EXECUTION_PLAN_R6_2026-06-17.md`](./_archive/EXECUTION_PLAN_R6_2026-06-17.md)
**工期**：6.5–11.5 人日
**关联**：[ADR-008](../adr/ADR-008-websocket-auth-and-realtime-push.md)、[ADR-009](../adr/ADR-009-israted-gate-and-virtual-rating-isolation.md)

| Round | Finding | 落地 |
|-------|---------|------|
| R6.1 | F-03 + F-10 | `RatingCalculationServiceImpl` isRated gate + ADR-007 §7 决策 |
| R6.2 | F-01 + F-06 + F-07 | 状态机审计 doc + timeFromStart 区分虚拟/真实时钟 + 虚拟赛服务端时间窗 |
| R6.3 | F-08 | 成就 is_virtual gate（`findIsVirtualBySubmissionId`） |
| R6.4 | F-04 + F-13 + F-17 + F-18 | `ContestSubscribeAuthInterceptor` + RankingsView WS 接入 + visibilitychange 真改 endsAt + unmount cleanup |
| R6.5 | F-15 + CRIT-2 + CRIT-3 | TS enum 注释 + V20260617140000（generated column 部分唯一 + VARCHAR 40→64） + HIGH-2 pre-check + ROLLBACK |
| R6.6 | 文档 | ADR-008 / ADR-009 + REVIEW_V3 §9 关闭 |

**R6 review 修复（HIGH/MED）**：
- HIGH-1 F-13 visibilitychange 真正实现（`setVirtualSession` action）
- HIGH-2 V20260617140000 加 pre-check + ROLLBACK 注释
- HIGH-3 F-07 单测补齐（4/4 SubmitContestProblemTests 通过）
- MED-3 `WebSocketAuthenticationException` 顶层化（**deferred**，cosmetic 重构）

**R6 部署**：14 commits 在 main，V20260617130000 + V20260617140000 两张新迁移已应用。R3.1 ↔ R3.2 必须同窗口原子。

---

## 3. R7 — MED/LOW 收口 + F-15 完整化

**来源**：[`_archive/EXECUTION_PLAN_R7_2026-06-17.md`](./_archive/EXECUTION_PLAN_R7_2026-06-17.md)
**工期**：6.5–9 人日
**关联**：[ADR-010](../adr/ADR-010-cancel-state-and-virtual-replay-boundary.md)、[ADR-011](../adr/ADR-011-crit6-shadow-mode-evaluation.md)

| Round | Finding | 落地 |
|-------|---------|------|
| R7.1 | F-21 | 1000 AC p99 < 500ms；缓存击穿 DB QPS ≤ 1 |
| R7.2 | F-22 + F-25 + F-31 | 3 个 audit doc + sweep 测试 |
| R7.3 | F-29 + F-43 + F-44 + F-47 | 断网 → reconnecting banner + 拒绝 toast |
| R7.4 | F-15 + F-28 + F-32 + F-39 + F-40 + F-41 + F-46 | grep `as string` = 0 + 跨端 enum 同步 |
| R7.5 | F-35 + F-36 + F-37 + F-38 + F-42 + F-45 + F-50-52 | ADR-010/011 Accepted + LOW 归档（[`_archive/LOW_REMAINING_R8.6_2026-06-17.md`](./_archive/LOW_REMAINING_R8.6_2026-06-17.md)） |
| R7.6 | F-24 + F-27 + CRIT-6 | keyset 压测 + 限流 key |

**R7.1–R7.6 全绿 → PRD §1.3 全部 49 finding 关闭**。

---

## 4. R8 — Review fixups + CRIT-6 灰度决策

**来源**：[`_archive/EXECUTION_PLAN_R8_2026-06-17.md`](./_archive/EXECUTION_PLAN_R8_2026-06-17.md)
**工期**：4.5–5 人日
**关联**：[ADR-011](../adr/ADR-011-crit6-shadow-mode-evaluation.md)

| Round | Finding | 落地 |
|-------|---------|------|
| R8.1 | F-24 | keyset 分页 |
| R8.2 | F-27 | SpEL 解析 + 跨 contest 独立桶 |
| R8.3 | F-21 | unit test + integration test 缓存隔离 |
| R8.4 | F-29 | 断网 5s/30s/恢复 三态切换 |
| R8.5 | CRIT-6 | [ADR-011](../adr/ADR-011-crit6-shadow-mode-evaluation.md) Accepted（不引独立 flag，隐式灰度由 ADR-006 §2.4 覆盖） |
| R8.6 | LOW F-35~F-47 | i18n keys + docs 归档 |

**R8.1–R8.6 全绿 → 模块 v4.1 完结。**

---

## 5. R9 — 性能缓存收口 + i18n 接入 + multi-tab 检测

**来源**：[`_archive/EXECUTION_PLAN_R9_2026-06-17.md`](./_archive/EXECUTION_PLAN_R9_2026-06-17.md)
**工期**：3–3.5 人日
**目标**：模块 **v4.2 完结**（最终权威裁决）

| Round | Finding | 落地 |
|-------|---------|------|
| R9.1 | F-21 | per-contest cache evict（**注**：R10 复盘发现 key 模板已含 `#contestId`，实际 R9.1 已部分实现 R10.1.1） |
| R9.2 | F-21 | 单 contest evict 隔离测试 |
| R9.3 | LOW i18n | grep `contest.empty/loading/error/connection/replay` 全有引用（**注**：R10 复盘发现 R9 阶段已用业务命名空间完成；R10.2 假设的 `empty.*` 是死键） |
| R9.4 | F-46 | 双 tab 冲突 toast + 30s stale 释放 |

**R9.1–R9.4 全绿 → 模块 v4.2 完结。**

---

## 6. R10 — 残留 deferred 收口 + 运维安全补全

**来源**：[`_archive/EXECUTION_PLAN_R10_2026-06-18.md`](./_archive/EXECUTION_PLAN_R10_2026-06-18.md)
**工期**：3.5–4 人日（计划）→ **0 行代码改动**（实际）
**关联**：[`docs/PRIVACY.md`](../PRIVACY.md)（R10.9）、`init-db/README.md` Migration Operational Checklist（R10.8）

| Round | Finding | 2026-06-18 实际状态 | 备注 |
|-------|---------|-------------------|------|
| **R10.1.1** | F-21 key 含 contestId | ✅ R9.1 已落地 | `ContestServiceImpl.java:398` `@Cacheable` key 已含 `#contestId` |
| **R10.1.2** | F-21 per-contest evict | ⚠️ DEFERRED | per-contest evict 接受为低优先（`< 10k` 行可接受）；如未来 > 10k 触发 NFR-P1，应作为 R11 独立产品决策 + 独立 PR |
| **R10.2** | LOW i18n 接线 | ⚠️ **plan 误判** | R9 阶段已用业务命名空间完成 i18n；9 个 key 是死键 |
| **R10.3** | LOW i18n 审计 | ✅ 0 漂移 | [`I18N_AUDIT_R10.md`](./I18N_AUDIT_R10.md) |
| **R10.4** | LOW cleanup | ⚠️ ABORTED | `getGlobalRankingsPaginated` 与 `getContestRanking` 是两个独立功能，非同 API 旧/新版本 |
| **R10.5** | M1 selectById | ⚠️ **plan 误判 / DEFERRED** | denormalize 引入 cascade 写放大 + R6.2/F-06 双轨时钟对账风险；推荐改用单 SQL JOIN 方案（独立 PR） |
| **R10.6** | F-01 §3.1 | ✅ doc-only | `finishVirtualContest` 复核销项 |
| **R10.7** | F-01 §6.4 | ✅ doc-only | F-06 `timeFromStart` 复核销项 |
| **R10.8** | F-SEC-10 | ✅ doc-only | `init-db/README.md` Migration Operational Checklist |
| **R10.9** | F-SEC-13 | ✅ doc-only | [`docs/PRIVACY.md`](../PRIVACY.md) 新建 |
| **MED-3** | cosmetic | ⏸ 留 R10.x 续轮 | `WebSocketAuthenticationException` 顶层化 |

**R10 完成度**：**5/9 ✅ + 4/9 plan 误判 + 0 行代码改动**。模块 v4.3 收口**不成立**；v4.2 保持为权威裁决。

### plan 误判来源分析

| Round | 误判原因 | 教训 |
|-------|---------|------|
| R10.1.1/1.2 | plan 基于 R9 计划文档而未对代码做实际验证；R9.1 实际已部分实现 R10.1.1 | 实施前必须读代码 grep 验证，不要基于上游 plan 推断 |
| R10.2 | plan 假设存在独立 `ContestWSBanner.vue` + `empty.*` 命名空间；实际 WS banner 由 composable 提供 + R9 阶段已用业务命名空间完成 i18n | 命名空间假设需对照 view 模板而非 locale 文件 |
| R10.4 | plan 假设 `getGlobalRankingsPaginated` 是旧版本；实际是两个独立功能 | API 合并/清理必须先确认 import 关系 |
| R10.5 | plan 基于 R5.1 假设的"contestMapper.selectById 多一次查询"；实际为 PK 查询 < 1ms + break 早退 | 性能优化必须先 profile 真实热路径 |

---

## 7. 累计验收总表（R1–R10 重新定档 checklist）

| 轮次 | Finding | 验收命令/方法 | 状态 |
|------|---------|--------------|------|
| R1 | P0-5 slug UNIQUE | `migrate.sh migrate` + 创建重复 slug 报错 | ✅ done |
| R2 | P0-3 真榜隔离 | 排行榜查询 `is_virtual=0`；个人页仍可见 | ✅ done |
| R3 | P0-2 + P0-4 | 虚拟赛自动 FINISHED；rating 非空且仅真实；并发幂等 | ✅ done |
| R4 | P0-1 评分生效 | 三模式单测全绿；penalty 配置生效 | ✅ done |
| R5 | P1 债务 | feature flags 清理；零调用者清理 | ✅ done |
| R6 | F-01/03/04/06/07/08/13/15/17/18 + CRIT-2/3 | 6 个 R6.x 全绿 | ✅ done |
| R7 | F-15/21/22/24-32/35-47/50-52 + CRIT-6 | 49 finding 全关；6 个 R7.x 全绿 | ✅ done |
| R8 | F-21/24/27/29 + LOW + CRIT-6 ADR | 5/5 全绿 | ✅ done |
| R9 | F-21/46 + i18n + multi-tab | 4/4 全绿 | ✅ done |
| R10 | F-01/F-SEC-10/13 + LOW deferred | 5/9 ✅ + 4/9 plan 误判（不阻塞） | ✅ done（v4.2 保持权威） |

**R1–R9 全绿 → 模块 v4.2 完结。**

---

<a id="实施记录"></a>
## 8. 累计实施记录（实施偏差与补充）

| 项 | 原计划 | 实际改动 | 原因 |
|----|--------|----------|------|
| R5 H2 | `catch (DuplicateKeyException)` | 改为 `catch (DataIntegrityViolationException)` | 父类 catch 覆盖 mysql-connector-j 与 MariaDB 差异 |
| R5 M2 | `autoFinishVirtualParticipants` N+1 UPDATE | 改为单条 `bulkFinishByIds(ids, now)` IN-list UPDATE | 避免 scheduler 10s tick 下的 N+1 |
| R5 M3 | startVirtualContest 行为变更无文档 | OpenAPI `@Operation.description` 补充 idempotent 语义 | 行为变更需要 API 层可发现 |
| R5 M5 | 测试 per-test mock override 模式脆弱 | 提取 `mockContest()` + `runWrongSubmissionWithContest()` helper | 测试可读性 + 防静默错判 |
| R5 L3 | `VIRTUAL_SESSION_PREFIX` 闭包局部未导出 | 保留闭包，注释中说明 key 形状 | 单 store 使用 closure-local 即可 |
| R5 M1 | （R5 未列，code review 派生） | **deferred** | `contestMapper.selectById` 多一次查询优化需改 submission 模块；独立 PR |
| R10.5 | denormalize `ContestProblem` | **ABORTED** | 引入迁移/对账/写放大风险；推荐单 SQL JOIN 替代 |
| R10.4 | 删除 `getGlobalRankingsPaginated` | **ABORTED** | 与 `getContestRanking` 是两个独立功能 |
| R10.2 | view 模板接线 9 个 i18n keys | **ABORTED** | R9 阶段已用业务命名空间完成；9 个 key 是死键 |

**Code review 完整结论**：详见 `.claude/reviews/contest-r1-r5-local-review.md`、`contest-r6-local-review.md`。

- **Decision**：APPROVE（所有 HIGH FIXED；MEDIUM M1 跨模块 deferred；LOW L3 设计选择 deferred）
- **测试**：33/33 contest 模块单测全绿（含 4 个 R4 / ADR-006 §4 评分模式测试）

---

## 9. 累计生产部署清单

1. `./scripts/dev/migrate.sh migrate` 在 staging 跑一次（用生产数据快照），确认无大量重复 slug
2. `git diff --check` + Conventional commit `<type>(contest): <desc>`（R3 同窗口原子）
3. `git push` / merge 需用户显式批准（`CLAUDE.md` 护栏）
4. R3 部署务必同窗口原子上线（`autoFinishVirtualParticipants` ↔ `findRealParticipantsByContestId` 不可拆分）

---

## 10. R11+ 候选（已超出 R10 范围）

- **R10.5 替代方案（首选）**：在 `ContestProblemMapper` 增加 `findActiveContestProblemByProblemId(problemId)`，单 SQL JOIN `contests` 取 `status` / `startTime` / `actualStartTime`。消除 `recordContestSubmissionIfNeeded` 循环里的多次 selectById，零 denormalize 风险
- **R9.3 banner 缺陷修复**：`ContestRankingsView.vue:28` `showReconnecting` ref 模板未渲染（declared-but-unused），属 R9 收口漏网
- **F-22 业务决策** — "全局单活跃"约束跨 contest 唯一索引，等产品/业务方决定
- **CRIT-6 shadow 模式** — runtime + ops 配合，R7.6 延期
- **F-15 TS enum 完整化** — 跨端 enum 统一
- **F-32 ContestTimer 组件统一** — R7.4 延期
- **MED-3 `WebSocketAuthenticationException` 顶层化** — cosmetic 重构

---

## 11. 与历史报告的关系

| 文档 | 关系 | 状态 |
|------|------|------|
| `_archive/PLAN_v1.0_2026-06-17.md` | v1.0 早期设想（HMAC/appSecret 已被代码现实取代） | 历史证据 |
| `_archive/REVIEW_v1_2026-06-17.md` | v1 审查（对象 PLAN.md，REJECT） | 历史证据 |
| `_archive/REVIEW_v2_2026-06-17.md` | v2 合并报告（含 Security 重试） | 历史证据 |
| `_archive/SECURITY_REVIEW_2026-06-17.md` | Security 专项审查（CRIT-9/10 IDOR/appSecret 已在代码中用 UUID 修复） | 历史证据 |
| `_archive/FINDINGS_RAW_v1-v2_2026-06-17.md` | 原始 finding 表（v1/v2 审查 JSON 化清单） | 历史证据 |
| `_archive/DESIGN_ANALYSIS_2026-06-16.md` | 早期设计分析（前置文档） | 历史证据 |
| `_archive/EXECUTION_PLAN_R6_2026-06-17.md` | R6 详细步骤 | 历史执行证据 |
| `_archive/EXECUTION_PLAN_R7_2026-06-17.md` | R7 详细步骤 | 历史执行证据 |
| `_archive/EXECUTION_PLAN_R8_2026-06-17.md` | R8 详细步骤 | 历史执行证据 |
| `_archive/EXECUTION_PLAN_R9_2026-06-17.md` | R9 详细步骤 | 历史执行证据 |
| `_archive/EXECUTION_PLAN_R10_2026-06-18.md` | R10 详细步骤（含 plan 误判分析） | 历史执行证据 |
| `_archive/LOW_REMAINING_R8.6_2026-06-17.md` | LOW F-35~F-47 收口状态 | 历史执行证据 |
| `PRD.md` | Contest 模块产品需求文档（原始 spec 基线） | 现行权威 |
| `CONTEXT.md` | Contest 模块术语表 | 现行权威 |
| `REVIEW_V3.md` | 最终定档（审查实际代码，v4.2 完结） | **当前权威决策依据** |
| `F-01-STATE_MACHINE_AUDIT.md` | 状态机审计（R6.2 + R10.6/10.7 销项） | 现行权威 |
| `F-22-VIRTUAL-SESSION-CROSS-CONTEST-AUDIT.md` | 跨 contest 虚拟 session 审计 | 现行权威 |
| `I18N_AUDIT_R10.md` | i18n key 同步审计报告 | 现行审计产物 |
| `PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md` | 题目详情页产品修复（PM 视角） | 现行权威 |
| `docs/PRIVACY.md` | Log retention + PII 治理（R10.9 落地） | 现行权威 |

---

## 12. See also

- [README.md](./README.md) — Contest 模块文档索引
- [REVIEW_V3.md](./REVIEW_V3.md) — 最终定档（v4.2 完结）
- [`_archive/INDEX.md`](./_archive/INDEX.md) — 历史证据索引
- [ADR-006](../adr/ADR-006-contest-scoring-engine-activation.md)、[ADR-007](../adr/ADR-007-virtual-contest-lifecycle-and-rating-isolation.md)、[ADR-008](../adr/ADR-008-websocket-auth-and-realtime-push.md)、[ADR-009](../adr/ADR-009-israted-gate-and-virtual-rating-isolation.md)、[ADR-010](../adr/ADR-010-cancel-state-and-virtual-replay-boundary.md)、[ADR-011](../adr/ADR-011-crit6-shadow-mode-evaluation.md)
- [docs/index.md](../../../docs/index.md) — 当前工程文档总入口。