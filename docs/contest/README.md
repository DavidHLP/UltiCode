# Contest 模块文档

> **作用**：UltiCode Contest 模块（含虚拟竞赛）的需求、设计、审查、决策文档集中地
> **维护者**：后端 + 产品
> **最后更新**：2026-06-17

---

## 📁 文档地图

### 📐 设计 / 需求 (上游)

| 文件 | 用途 | 何时读 |
|------|------|--------|
| **[PRD.md](./PRD.md)** | Contest 模块产品需求文档（PM 视角合理性分析 + 52 finding → 需求映射）| 评估产品方向、新功能立项 |
| **[DESIGN_ANALYSIS.md](./DESIGN_ANALYSIS.md)** | 早期设计分析（前置文档，PM + 架构师视角）| 了解决策历史背景 |

### 📋 实施计划 (中游)

| 文件 | 用途 | 何时读 |
|------|------|--------|
| **[PLAN.md](./PLAN.md)** | 完整修复计划（10 Phase / 22 commit / 5 周）| 实施、改计划、review 计划 |

### 🔍 审查 / 决策 (下游)

| 文件 | 用途 | 何时读 |
|------|------|--------|
| **[REVIEW.md](./REVIEW.md)** | v1 审查报告（6 视角，79 finding，REJECT-WITH-MAJOR-REVISIONS）| 证据链，对照历史判断 |
| **[SECURITY_REVIEW.md](./SECURITY_REVIEW.md)** | Security 专项审查（14 finding，含 2 个全新 CRITICAL）| 鉴权、IDOR、secret 治理 |
| **[REVIEW_V2.md](./REVIEW_V2.md)** ⭐ | **v2 合并报告**（7 视角，93 finding，含 Security 整合）| 当前权威决策依据 |
| **[FINDINGS_RAW.md](./FINDINGS_RAW.md)** | 原始 finding 表（来自 6 视角审查的 JSON 化清单）| 追溯 finding 来源 |

---

## 🚀 推荐阅读顺序

**如果你要决策（产品/技术 lead）**：
1. [PRD.md](./PRD.md) §11（P1-P5 决策项）
2. [REVIEW_V2.md](./REVIEW_V2.md) §1 + §9（执行摘要 + 裁决）
3. [REVIEW_V2.md](./REVIEW_V2.md) §7（Top 10 重写建议）

**如果你要实施（后端/前端工程师）**：
1. [PLAN.md](./PLAN.md) 通读（了解整体方案）
2. [REVIEW_V2.md](./REVIEW_V2.md) §2-3（必须修复的 CRITICAL + HIGH）
3. [SECURITY_REVIEW.md](./SECURITY_REVIEW.md)（CRIT-9/10 + HIGH-SEC-* 必读）
4. [REVIEW_V2.md](./REVIEW_V2.md) §10（重新提交清单，逐项打勾）

**如果你要 security 审查（安全工程师）**：
1. [SECURITY_REVIEW.md](./SECURITY_REVIEW.md) 完整
2. [REVIEW_V2.md](./REVIEW_V2.md) §0 + §2-3（v2 整合视角）
3. [PLAN.md](./PLAN.md) §1.3 + §2.2 + §6.1（重点章节）

**如果你要追溯 finding 来源**：
1. [FINDINGS_RAW.md](./FINDINGS_RAW.md)（原始 finding 表）
2. [REVIEW.md](./REVIEW.md) §2-5（v1 视角分类与裁决）
3. [SECURITY_REVIEW.md](./SECURITY_REVIEW.md)（Security 视角专项）

---

## 📊 当前状态

| 维度 | 状态 |
|------|------|
| 修复计划 | **存在**，待重大修改（REJECT-WITH-MAJOR-REVISIONS） |
| 审查次数 | 2 (v1 = 6 视角, v2 = 7 视角含 Security 重试) |
| Finding 总数 | **93** |
| 待修 CRITICAL | **10** (8 来自主报告 + 2 来自 Security) |
| 预计实施工时 | **14-16 人周** (v2 比 v1 多 1 周 Security Reconcile) |
| 重新提交要求 | 见 [REVIEW_V2.md §10](./REVIEW_V2.md) checklist |

---

## 🔄 文档历史

| 日期 | 事件 |
|------|------|
| 2026-06-16 | 早期设计分析（DESIGN_ANALYSIS.md） |
| 2026-06-17 上午 | PRD + 修复计划（PLAN.md）出炉 |
| 2026-06-17 中午 | v1 多 Agent 审查（6 视角，REJECT，REVIEW.md） |
| 2026-06-17 下午 | Security 视角重试（捕获 2 个全新 CRITICAL，SECURITY_REVIEW.md） |
| 2026-06-17 下午 | v2 合并报告（REVIEW_V2.md）+ 文档统一迁移至 `docs/contest/` |

---

## 📝 维护约定

### 添加新文档

- **设计/需求类**：放 `DESIGN_*.md` / `PRD_*.md`
- **实施计划类**：放 `PLAN_*.md`
- **审查类**：放 `REVIEW_*.md` / `SECURITY_*.md`
- **原始 finding**：放 `FINDINGS_*.md`
- **新增/重命名**：同步更新本 README 的"文档地图"和"阅读顺序"

### 命名约定

- 全大写文件名：`PLAN.md` / `REVIEW_V2.md` / `SECURITY_REVIEW.md`
- 版本后缀：`_V2.md` / `_V3.md` 表示同文档的版本演进
- 专项后缀：`_SECURITY` / `_PERFORMANCE` 表示专项审查

### Git 管理

- 重大修改后**强制**重新走 v1 → v2 流程：
  1. 改 PLAN.md
  2. 重跑多 Agent 审查
  3. 生成新 REVIEW 文件
  4. 更新本 README
- 单文件 typo 修复直接 commit，不需要新版本号
- 重新提交时附 finding 对照表（已解决 / 已转移 / 未解决）
