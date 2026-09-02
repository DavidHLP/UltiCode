<a id="4-按主题标签tag-map"></a>
---
title: Contest 模块文档索引
tags: [contest, index]
status: living
updated: 2026-06-18
owner: backend+product
---

> **作用**：UltiCode Contest 模块（含虚拟竞赛）的**历史归档**需求、设计、审查、决策文档集中地（`v4.2 完结` 时的快照，已归档于 `docs/archive/contest/`）。
> **维护者**：后端 + 产品（归档后不更新）
> **最后更新**：2026-06-18（`R10` 收口；模块 `v4.2` 完结为权威裁决；详见 [REVIEW_V3 §12](./REVIEW_V3.md)）**（归档快照，非现行）**
> **历史证据**：归档于 [`_archive/`](./_archive/)（v1/v2 审查 + R6–R10 执行计划 + LOW 收口）

---

## 📁 文档地图

### 📖 术语表 + 现行权威决策（必读）

| 文件 | 用途 | 何时读 |
|------|------|--------|
| **[CONTEXT.md](./CONTEXT.md)** | Contest 模块术语表（Real/Virtual Participant、Scoring Mode、Rating 等） | 阅读任何 contest 文档前先对齐术语 |
| **[REVIEW_V3.md](./REVIEW_V3.md)** ⭐ | **v3 最终定档**（审查实际代码，4 维度 + 复核） | **当前权威决策依据** |

### 📐 设计 / 需求（上游）

| 文件 | 用途 | 何时读 |
|------|------|--------|
| **[PRD.md](./PRD.md)** | Contest 模块产品需求文档（PM 视角 + 52 finding → 需求映射） | 评估产品方向、新功能立项 |
| **[PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md](./PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md)** | 竞赛题目详情页产品问题与修复文档（PM/业务视角） | 评估竞赛题目列表、做题页、赛后复盘体验 |

### 📋 实施计划（中游 — 现行汇总 + 历史归档）

| 文件 | 用途 | 何时读 |
|------|------|--------|
| **[EXECUTION_PLAN.md](./EXECUTION_PLAN.md)** ⭐ | **R1–R10 累计执行计划**（汇总 + 验收总表 + plan 误判分析） | **实施首选入口**：当前唯一的实施汇总 |
| [`_archive/`](./_archive/) | R6–R10 详细执行步骤 + v1/v2 审查证据 | 复盘某轮 finding / 追溯历史决策 |
| [`_archive/INDEX.md`](./_archive/INDEX.md) | 归档文件索引 + 何时查这里 | 进入 `_archive/` 之前先看 |

### 🔍 审计与决策（下游 — 现行权威）

| 文件 | 用途 | 何时读 |
|------|------|--------|
| **[F-01-STATE_MACHINE_AUDIT.md](./F-01-STATE_MACHINE_AUDIT.md)** | 状态机审计（R6.2 落地 + R10.6/10.7 销项） | 验证状态机不变量 |
| **[F-22-VIRTUAL-SESSION-CROSS-CONTEST-AUDIT.md](./F-22-VIRTUAL-SESSION-CROSS-CONTEST-AUDIT.md)** | 跨 contest 虚拟 session 审计（R7.2 落地） | 验证虚拟 session 隔离不变量 |
| **[I18N_AUDIT_R10.md](./I18N_AUDIT_R10.md)** | i18n key 同步审计报告（R10.3 落地） | 验证 i18n 一致性 |

---

## 🚀 推荐阅读顺序

**如果你要决策（产品/技术 lead）**：

1. [REVIEW_V3.md §1 + §12](./REVIEW_V3.md)（v4.2 定档结论 + R10 deferred 项）
2. [REVIEW_V3.md §7 + §9](./REVIEW_V3.md)（v2 finding 去向 + 重新定档 checklist 全部 ✅）
3. [PRD.md §11](./PRD.md)（P1-P5 决策项）

**如果你要实施（后端/前端工程师）**：

1. [CONTEXT.md](./CONTEXT.md)（对齐术语：Real/Virtual Participant、Scoring Mode、Rating）
2. [EXECUTION_PLAN.md](./EXECUTION_PLAN.md) ⭐ 通读（R1–R10 累计 + 验收总表 + plan 误判）
3. 特定轮次详细步骤：[_archive/EXECUTION_PLAN_R{6,7,8,9,10}_*.md](./_archive/)
4. [REVIEW_V3.md §10–§12](./REVIEW_V3.md)（R7/R8/R9 收口记录）
5. ADR 集：[ADR-006](../adr/ADR-006-contest-scoring-engine-activation.md) + [ADR-007](../adr/ADR-007-virtual-contest-lifecycle-and-rating-isolation.md) + ADR-008/009/010/011

**如果你要 security 审查（安全工程师）**：

1. [SECURITY_REVIEW.md (归档)](./_archive/SECURITY_REVIEW_2026-06-17.md) 完整
2. [REVIEW_V2.md (归档)](./_archive/REVIEW_v2_2026-06-17.md) §0 + §2-3
3. [PLAN.md (归档)](./_archive/PLAN_v1.0_2026-06-17.md) §1.3 + §2.2 + §6.1（重点章节，**仅作历史**）

**如果你要追溯 finding 来源**：

1. [`_archive/FINDINGS_RAW_v1-v2_2026-06-17.md`](./_archive/FINDINGS_RAW_v1-v2_2026-06-17.md)（原始 finding 表）
2. [`_archive/REVIEW_v1_2026-06-17.md`](./_archive/REVIEW_v1_2026-06-17.md) §2-5（v1 视角分类与裁决）
3. [`_archive/SECURITY_REVIEW_2026-06-17.md`](./_archive/SECURITY_REVIEW_2026-06-17.md)（Security 视角专项）
4. [REVIEW_V3.md §7](./REVIEW_V3.md)（finding 在代码中的最终去向对照表）

---

## 📊 当前状态

| 维度 | 状态 |
|------|------|
| 审查对象 | **实际代码**（v1/v2 审查 PLAN.md，v3 转为审查代码） |
| 当前裁决 | **v4.2 完结** —— R1–R10 全部落地；P0 阻断项 0；详见 [REVIEW_V3.md §12](./REVIEW_V3.md) |
| 重新定档 checklist | 全部 ✅（R1–R10）；见 [EXECUTION_PLAN.md §7](./EXECUTION_PLAN.md) |
| 已确认修复 | 49 PRD finding + 12 LOW + 6 F-SEC HIGH/CRITICAL + i18n + 性能 + 鉴权链（详见各 R 计划） |
| R10 收口 | 5/9 ✅ (R10.1.1/R10.3/R10.6/R10.7/R10.8/R10.9) + 4/9 plan 误判 (R10.1.2 DEFERRED / R10.2 / R10.4 ABORTED / R10.5 DEFERRED) + 0 行代码改动 |
| R11+ 候选项 | MED-3 `WebSocketAuthenticationException` 顶层化 · R10.5 替代方案（单 SQL JOIN）· R9.3 banner 缺陷 · F-22 业务决策 · CRIT-6 shadow · F-15 TS enum · F-32 ContestTimer 统一 |
| 待复核（代码侧） | ✅ **R10 已销项**：F-01 §3.1 `#5 finishVirtualContest` + F-01 §6.4 F-06 `timeFromStart` 均无 violation（详见 [F-01-STATE_MACHINE_AUDIT.md §3.1/§6.4](./F-01-STATE_MACHINE_AUDIT.md)） |
| SECURITY 残留风险 | F-SEC-10/13 已 R10.8/10.9 关单（[init-db/README.md](../../../init-db/README.md) / [docs/PRIVACY.md](../PRIVACY.md)）；F-SEC-12/14 留业务/文档决策（详见 [REVIEW_V3 §12](./REVIEW_V3.md)） |

---

## 🔄 文档历史

| 日期 | 事件 |
|------|------|
| 2026-06-16 | 早期设计分析（[DESIGN_ANALYSIS (归档)](./_archive/DESIGN_ANALYSIS_2026-06-16.md)） |
| 2026-06-17 上午 | PRD + 修复计划（[PLAN (归档)](./_archive/PLAN_v1.0_2026-06-17.md)）出炉 |
| 2026-06-17 中午 | v1 多 Agent 审查（6 视角，REJECT，[REVIEW_V1 (归档)](./_archive/REVIEW_v1_2026-06-17.md)） |
| 2026-06-17 下午 | Security 视角重试（捕获 2 个全新 CRITICAL，[SECURITY_REVIEW (归档)](./_archive/SECURITY_REVIEW_2026-06-17.md)） |
| 2026-06-17 下午 | v2 合并报告（[REVIEW_V2 (归档)](./_archive/REVIEW_v2_2026-06-17.md)）+ 文档统一迁移至 `docs/contest/` |
| 2026-06-17 晚 | **v3 最终定档**（[REVIEW_V3.md](./REVIEW_V3.md)）—— 审查对象从 PLAN.md 转为实际代码，裁决"不建议合入，补齐 P0 后重新定档" |
| 2026-06-17 晚 | **R1–R5** 全部落地（5 项 P0 收口，[EXECUTION_PLAN.md](./EXECUTION_PLAN.md)），本地 review APPROVE |
| 2026-06-17 晚 | **v3.1 复审** + **R6** 11 项 P0/P1 + 2 项历史债收口（[EXECUTION_PLAN_R6 (归档)](./_archive/EXECUTION_PLAN_R6_2026-06-17.md)） |
| 2026-06-17 晚 | **R7** MED/LOW 收口（49 finding 全部关闭或显式 deferred，[EXECUTION_PLAN_R7 (归档)](./_archive/EXECUTION_PLAN_R7_2026-06-17.md)） |
| 2026-06-17 晚 | **R8** review fixups + ADR-011 灰度决策（[EXECUTION_PLAN_R8 (归档)](./_archive/EXECUTION_PLAN_R8_2026-06-17.md)） |
| 2026-06-17 晚 | **R9** 性能缓存收口 + i18n 接入 + multi-tab 检测 —— **模块 v4.2 完结**（[EXECUTION_PLAN_R9 (归档)](./_archive/EXECUTION_PLAN_R9_2026-06-17.md)） |
| 2026-06-17 晚 | **F-01 状态机审计 doc**（[F-01-STATE_MACHINE_AUDIT.md](./F-01-STATE_MACHINE_AUDIT.md)）— R6.2 实施时系统化审计 §3.1/§6.4 待复核项 |
| 2026-06-18 | **R10 收口**（[EXECUTION_PLAN_R10 (归档)](./_archive/EXECUTION_PLAN_R10_2026-06-18.md)）— 5/9 ✅ + 4/9 plan 误判 + 0 行代码改动；**模块 v4.2 完结为权威裁决**，v4.3 收口不成立 |
| 2026-06-18 | 新增 **竞赛题目详情页产品修复文档**（[PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md](./PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md)） |
| 2026-06-18 | **文档重构**：v1/v2 审查证据 + R6–R10 执行计划统一归档到 [`_archive/`](./_archive/)；新建 [EXECUTION_PLAN.md](./EXECUTION_PLAN.md) 作为 R1–R10 累计唯一入口 |

---

## 📝 维护约定

### 添加新文档

- **设计/需求类**：放 `DESIGN_*.md` / `PRD_*.md`
- **实施计划类**：放 `EXECUTION_PLAN.md`（汇总，不开新文件）或 `_archive/EXECUTION_PLAN_R{N}_*.md`（历史归档）
- **审查/审计类**：现行权威 → `REVIEW_V3.md` / `F-*.md`；历史 → `_archive/`
- **原始 finding**：归档到 `_archive/FINDINGS_RAW_*.md`
- **新增/重命名**：同步更新本 README 的"文档地图"和"阅读顺序"

### 命名约定

- 现行权威：全大写文件名 `EXECUTION_PLAN.md` / `REVIEW_V3.md` / `CONTEXT.md`
- 归档文件：`<NAME>_<version>_<YYYY-MM-DD>.md`（例：`PLAN_v1.0_2026-06-17.md`、`EXECUTION_PLAN_R6_2026-06-17.md`）
- 专项审计：`F-NN-{topic}.md`（例：`F-01-STATE_MACHINE_AUDIT.md`、`F-22-VIRTUAL-SESSION-CROSS-CONTEST-AUDIT.md`）
- 产品修复：`PROBLEM_DETAIL_PAGE_PRODUCT_FIX.md` 命名空间

### 归档规则（防止后续碎片化）

- 任何已完成的历史执行计划（"R{N}"轮次结束后）→ 移到 `_archive/`
- 任何被新决策取代的审查报告 → 移到 `_archive/`
- 任何 POINT-IN-TIME 审计产物（已过 R{N} 收口且无现行引用）→ 移到 `_archive/`
- 归档前：头部加 frontmatter `status: archived` + 更新本 README §11 + 更新 `_archive/INDEX.md`

### Git 管理

- 重大修改后**强制**重新走 v1 → v2 流程：
  1. 改 `PLAN.md`（若需新建）
  2. 重跑多 Agent 审查
  3. 生成新 REVIEW 文件
  4. 更新本 README
- 单文件 typo 修复直接 commit，不需要新版本号
- 重新提交时附 finding 对照表（已解决 / 已转移 / 未解决）