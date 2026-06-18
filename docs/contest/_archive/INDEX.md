---
title: Contest 模块历史证据索引（_archive/）
tags: [contest, archive, history]
status: archived
updated: 2026-06-18
owner: backend
---

# Contest 模块历史证据索引（`_archive/`）

> **作用**：归档 `docs/contest/` 历史 v1/v2 审查证据 + R6–R10 执行计划 + LOW 收口记录。**这些文档不构成现行决策依据**，仅供复盘与 finding 溯源。
> **现行权威**：[`../EXECUTION_PLAN.md`](../EXECUTION_PLAN.md)（R1–R10 累计） + [`../REVIEW_V3.md`](../REVIEW_V3.md)（最终定档 v4.2 完结） + [Contest ADR 集](../../adr/)。
> **维护原则**：归档文件**只读**，不再修改正文；任何修订应写入 `_archive/UPDATE_LOG.md` 并指向新文档。

---

## 索引

### 历史 v1/v2 审查证据（2026-06-17 v1-v2 阶段）

| 文档 | 角色 | 关键结论 | 是否仍有效 |
|------|------|----------|------------|
| [`PLAN_v1.0_2026-06-17.md`](./PLAN_v1.0_2026-06-17.md) | v1.0 早期修复计划（HMAC/appSecret 等方案） | HMAC/appSecret 已被代码现实（UUID）取代，**不沿用 Phase 0-9 框架** | ❌ 仅作决策溯源 |
| [`REVIEW_v1_2026-06-17.md`](./REVIEW_v1_2026-06-17.md) | v1 审查报告（6 视角，79 finding） | REJECT-WITH-MAJOR-REVISIONS（对象：PLAN.md） | ❌ 已被 V3 取代 |
| [`REVIEW_v2_2026-06-17.md`](./REVIEW_v2_2026-06-17.md) | v2 合并报告（7 视角，93 finding） | REJECT-WITH-MAJOR-REVISIONS（对象：PLAN.md）；含 Security 重试 | ❌ 已被 V3 取代 |
| [`SECURITY_REVIEW_2026-06-17.md`](./SECURITY_REVIEW_2026-06-17.md) | Security 专项审查（14 finding） | CRIT-9/10（IDOR/appSecret）已在代码中用 UUID 方案修复 | ❌ 已被代码现实解决 |
| [`FINDINGS_RAW_v1-v2_2026-06-17.md`](./FINDINGS_RAW_v1-v2_2026-06-17.md) | 原始 finding 表（v1/v2 JSON 化清单） | 79 finding；finding 在代码中的去向见 [`../REVIEW_V3.md` §7](../REVIEW_V3.md) | ❌ 仅作 finding 溯源 |
| [`DESIGN_ANALYSIS_2026-06-16.md`](./DESIGN_ANALYSIS_2026-06-16.md) | 早期设计分析（PM + 架构师视角） | 经对抗性审查 17/20 条 claim 完全属实 | ❌ 仅作设计背景 |

### 历史执行计划（R6–R10 详细步骤）

| 文档 | 主题 | 工期 | 状态 |
|------|------|------|------|
| [`EXECUTION_PLAN_R6_2026-06-17.md`](./EXECUTION_PLAN_R6_2026-06-17.md) | R6 — 11 项 P0/P1 + 2 项历史债 | 6.5–11.5 人日 | ✅ 全部落地 |
| [`EXECUTION_PLAN_R7_2026-06-17.md`](./EXECUTION_PLAN_R7_2026-06-17.md) | R7 — MED/LOW 收口 + F-15 完整化 | 6.5–9 人日 | ✅ 全部落地 |
| [`EXECUTION_PLAN_R8_2026-06-17.md`](./EXECUTION_PLAN_R8_2026-06-17.md) | R8 — Review fixups + CRIT-6 灰度决策 | 4.5–5 人日 | ✅ 全部落地 |
| [`EXECUTION_PLAN_R9_2026-06-17.md`](./EXECUTION_PLAN_R9_2026-06-17.md) | R9 — 性能缓存收口 + i18n 接入 + multi-tab 检测 | 3–3.5 人日 | ✅ 全部落地 |
| [`EXECUTION_PLAN_R10_2026-06-18.md`](./EXECUTION_PLAN_R10_2026-06-18.md) | R10 — 残留 deferred 收口 + 运维安全补全 | 计划 3.5–4 人日 / **实际 0 行代码** | ⚠️ 5/9 ✅ + 4/9 plan 误判 |

### 历史 LOW 收口

| 文档 | 主题 | 状态 |
|------|------|------|
| [`LOW_REMAINING_R8.6_2026-06-17.md`](./LOW_REMAINING_R8.6_2026-06-17.md) | LOW F-35~F-47 收口状态 | ✅ 全部关闭 |

---

## 何时查这里

- **复盘某轮 finding 的代码侧最终去向** → 先看 `../REVIEW_V3.md` §7，再回到本目录对应 R-plan 的"实施记录"段
- **追溯 v1/v2 审查为何 REJECT** → 看 `REVIEW_v1_2026-06-17.md` + `REVIEW_v2_2026-06-17.md` + `FINDINGS_RAW_v1-v2_2026-06-17.md`
- **理解为什么 HMAC/appSecret 被 UUID 取代** → 看 `PLAN_v1.0_2026-06-17.md` §1.3 vs `REVIEW_V3.md` §5
- **查阅 R10 plan 误判细节** → 看 `EXECUTION_PLAN_R10_2026-06-18.md` §"⚠️ plan 误判" 各小节

---

## 维护规则

1. **本目录只读**：归档文件不再修改正文；任何修订应写入 `_archive/UPDATE_LOG.md` 并指向新文档
2. **改名 = 改链接**：若需要重命名（极少发生），同步更新 `../README.md` §11 与本文档
3. **新归档**：仅当产生新的"point-in-time 审计/审查证据"且无现行权威可替代时，才追加新归档文件
4. **不删归档**：与 ADR 规则一致，引用关系一旦建立就不能删（详见 [DOCS-SPEC.md §5](../../DOCS-SPEC.md)）