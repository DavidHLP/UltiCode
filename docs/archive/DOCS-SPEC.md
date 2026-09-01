<!-- Hand-maintained. The repo-wide doc convention. Keep it short and enforceable. -->

# docs/ 文档规范（DOCS-SPEC）

本文件规定 `docs/` 目录下文档的**命名、元信息、链接、目录归属、归档与更新触发**。
目标是让任何人在 6 个月后仍能找到东西、敢改、不破坏链接。配套入口索引：
[README.md](./contest/README.md#4-按主题标签tag-map)。

> 原则：**能不写就不写；写了就要维护。** 空模板比没有文档更糟——禁止建空文件。

---

## 1. 目录归属（放哪）

| 内容类型 | 去向 | 判定 |
|----------|------|------|
| 跨模块架构决策（引入 port / 抽象 / 改不变量） | `adr/ADR-NNN-*.md` | “6 个月后还成立吗？”成立 → ADR |
| 特性的 PRD / 设计 / 审查 / 计划 | `docs/<feature>/`（模板见 [contest/](./contest/)） | 一个特性一个目录，自带 README |
| 代码结构地图（模块/路由/schema/依赖） | `CODEMAPS/` | **只走自动生成**，不手写 |
| 部署 / 运维 / 环境 / 规范 / 隐私 | `docs/*.md`（根级） | 长期 living 参考文档 |
| 一次性笔记 / 草稿 | **不进 `docs/`** | 放本地或 PR 描述，避免污染索引 |

**反模式**：把架构决策写进 CODEMAPS（会被重生成覆盖）、把运维步骤写进 ADR（ADR 只记“为什么”，不记“怎么做”）、为单个 bug 建一篇 `docs/`。

---

## 2. 命名规则

| 位置 | 规则 | 示例 |
|------|------|------|
| 根级 `docs/*.md` | `UPPERCASE.md` 或 `kebab-case.md`，语义名词 | `RUNBOOK.md`、`ENV.md`、`arthas-mcp-usage.md` |
| `CODEMAPS/*.md` | 小写单名词 | `architecture.md`、`backend.md`、`sandbox.md` |
| `adr/*.md` | `ADR-NNN-{kebab-case-title}.md`，NNN 三位补零，**不改名** | `ADR-002-sandbox-hexagonal.md` |
| `adr/` 子协议 | `ADR-NNNx-{title}.md`（不占主编号） | `ADR-005a-rollback-drill-protocol.md` |
| 特性目录 | `UPPERCASE.md` + 版本/专项后缀 | `REVIEW_V3.md`、`SECURITY_REVIEW.md` |

- 文件名**不用日期前缀**（日期进 frontmatter / 正文，避免改名）。
- 重命名 = 改链接：全局搜旧名，更新 [README.md §3](./README.md) 与 area README。
- **ADR 一旦 commit 进 main，禁止改名**（引用会断；详见 [adr/README.md](./adr/README.md#编号规则)）。

---

## 3. 元信息头（每篇文档顶部）

两种合法形式，**二选一，新文档优先 YAML**：

### A. YAML frontmatter（推荐，手写文档用）

```yaml
---
title: Sandbox 代码执行沙箱
tags: [sandbox, architecture, reference]
status: living          # living | accepted | proposed | superseded | archived
updated: 2026-06-18
owner: backend
---
```

- `tags`：取自 [README.md §4 Tag Map](./contest/README.md#4-按主题标签tag-map)，新增 tag 同步登记到 §4。
- `status: living` = 持续维护的参考文档；`accepted/proposed/superseded` 主要给 ADR 用；`archived` 见 §5。

### B. HTML 注释头（CODEMAPS 自动生成用）

```markdown
<!-- Generated: 2026-06-18 | Java 735 · Vue 821 · ... | Token estimate: ~780 -->
```

**CODEMAPS 必须保留 B 形式**（生成器写入），手工文档用 A 形式。两者不混用。

---

## 4. 链接规则（引用）

| 场景 | 用法 | 说明 |
|------|------|------|
| 指向同目录或子目录文档 | 相对链接 `[x](./RUNBOOK.md)` | GitHub + Obsidian 都解析，**首选** |
| 指向锚点 | `[RUNBOOK §4](./RUNBOOK.md#4-common-issues)` | 锚点 = 小写化 + 连字符 |
| 指向仓库根文档 | `` `AGENTS.md` `` 反引号或相对 `../AGENTS.md` | 根文档用反引号提示“在仓库根” |
| 跨文档强关联 | 在文末加 `## See also` 列表 | 双向可发现 |

- **不要用** 绝对路径 `/home/...` 或带域名的内部链接。
- Obsidian wikilink `[[note]]` 可用，但在纯 GitHub 仓库里相对链接更通用——**本仓库默认相对链接**，wikilink 仅在确需 Obsidian 双链时用。
- 引用某 ADR：写成 `[ADR-002](./adr/ADR-002-sandbox-hexagonal.md)`，不要只写裸 `ADR-002`（不可点）。

---

## 5. 归档与版本

| 情况 | 处理 |
|------|------|
| ADR 被新决策取代 | 旧 ADR **保留**，头部加 `Superseded by ADR-XXX`，状态改 `superseded` |
| ADR 被否决 | 保留作“为什么不做”存证，状态 `rejected` |
| **特性轮次完成** | 详细步骤移入 `<feature>/_archive/EXECUTION_PLAN_R{N}_{YYYY-MM-DD}.md`（命名带日期 + 轮次，避免重名）；现行 `<feature>/EXECUTION_PLAN.md` 追加 §Round 摘要 + 验收表行 |
| **历史 v1/v2 审查证据** | 移入 `<feature>/_archive/REVIEW_v{N}_{YYYY-MM-DD}.md` 等命名；新建 `<feature>/_archive/INDEX.md` 作为索引 |
| 文档整体过期且无替代 | 移到 `<feature>/_archive/` 或删除，**并在 [README.md §3](./README.md) 去掉其行** |
| 同文档版本演进 | `_V2.md` / `_V3.md` 保留旧版，新版本号递增 |

**绝不**：静默删除被引用的文档；改 ADR 文件名；删 Flyway 已应用迁移（类比同理）。

---

## 6. 更新触发（什么时候改文档）

| 事件 | 要改的文档 |
|------|-----------|
| 新增/重命名/移动文件 | [README.md](./README.md) §3 + 对应 area README |
| 引入跨模块 port / 抽象 / 改不变量 | 新建 ADR（走 [adr/README 评审流程](./adr/README.md#评审流程)） |
| 后端模块/路由/分层变化 | 重跑 `ecc:update-codemaps` → 刷新 `CODEMAPS/backend.md`、`architecture.md` |
| 新增环境变量 | [ENV.md](./ENV.md) + 该变量的 Consumer 列 |
| 新增 feature flag | [RUNBOOK §10](./RUNBOOK.md#10-feature-flag-切换手册) flag 表 + 相关 ADR |
| 新增/修改数据表 | [CODEMAPS/data.md](./CODEMAPS/data.md)（自动）+ 若影响不变量则补 ADR |
| 安全相关变更 | 先读 `CLAUDE.md` Security Invariants；涉及 PII/日志则同步 [PRIVACY.md](./PRIVACY.md) |

---

## 6.5 防碎片化规则（防止文档无限增长）

> 本节是 §5/§6 的**预防性补丁**。若不遵守，下面任何一条都会让 `docs/<feature>/` 退化为"碎片海洋"。

### 6.5.1 单一权威原则

| 类别 | 现行权威（只此一份） | 历史证据（归档） |
|------|---------------------|------------------|
| 特性执行计划 | `<feature>/EXECUTION_PLAN.md`（所有轮次累计 + 验收总表） | `<feature>/_archive/EXECUTION_PLAN_R{N}_{YYYY-MM-DD}.md` |
| 特性审查报告 | `<feature>/REVIEW_V{N}.md`（最高版本即权威） | `<feature>/_archive/REVIEW_v{N-1}_{YYYY-MM-DD}.md` |
| Finding 原始清单 | 已纳入现行 REVIEW_V{N} §7 对照表 | `<feature>/_archive/FINDINGS_RAW_v{N-1}_{YYYY-MM-DD}.md` |

**禁止**：在同一 feature 下同时维护多份"并行执行计划"或"并行审查报告"。新轮次必须合并到现有 `EXECUTION_PLAN.md`，而不是新开 `EXECUTION_PLAN_R{N}.md` 在主目录。

### 6.5.2 归档触发（何时移入 `_archive/`）

| 触发 | 操作 |
|------|------|
| 某轮次的所有 Round 实施完成 + 验收签字 | 详细步骤文件 → `_archive/EXECUTION_PLAN_R{N}_{YYYY-MM-DD}.md`；当前 `EXECUTION_PLAN.md` 追加 §Round 摘要 + 验收表行 |
| 新 REVIEW_V{N+1} 发布 | 旧 `REVIEW_V{N}.md` → `_archive/REVIEW_v{N}_{YYYY-MM-DD}.md` |
| 历史审计产物（如 SECURITY_REVIEW、DESIGN_ANALYSIS、FINDINGS_RAW）确认无现行引用 | 移到 `_archive/`，并更新 `_archive/INDEX.md` |
| 临时草稿 / scratchpad | **不进 `docs/`**，本地 `.scratch/` 或 PR 描述 |

### 6.5.3 命名规范（避免归档内重名）

| 类型 | 格式 | 示例 |
|------|------|------|
| 归档执行计划 | `<NAME>_R{N}_{YYYY-MM-DD}.md` | `EXECUTION_PLAN_R6_2026-06-17.md` |
| 归档审查报告 | `<NAME>_v{N}_{YYYY-MM-DD}.md` | `REVIEW_v1_2026-06-17.md`、`REVIEW_v2_2026-06-17.md` |
| 归档其他 | `<NAME>_{YYYY-MM-DD}.md` | `SECURITY_REVIEW_2026-06-17.md`、`DESIGN_ANALYSIS_2026-06-16.md` |

日期必须为该文档**最后修改日期**（用 git log 取最后 commit 日期，或文件 frontmatter `updated`）。

### 6.5.4 索引义务

任何新建的 `_archive/` 必须配套 `_archive/INDEX.md`，列出：
1. 归档文件清单 + 角色 + 是否仍有效
2. "何时查这里"（按复盘/追溯/查找三种场景）
3. "维护规则"（只读、改名约束、不删原则）

无 INDEX 的 `_archive/` 是反模式，等于把碎片藏起来但忘了归档。

### 6.5.5 反例（看到就要纠正）

- ❌ 在 `docs/contest/` 同时存在 `EXECUTION_PLAN.md` 和 `EXECUTION_PLAN_R10.md`（R10 应合并入主 EXECUTION_PLAN 或归档）
- ❌ 同一 feature 下保留 `REVIEW.md` / `REVIEW_V2.md` / `REVIEW_V3.md` 在主目录（V1/V2 应归档到 `_archive/`，只留最高版本）
- ❌ 把 "R6.2 实施时审计" 这类**审计产物**堆在主目录（应放 `<feature>/F-NN-{topic}.md` 作为现行审计权威，或归档）
- ❌ 新建空 `_archive/` 没有 INDEX（违反 §6.5.4）
- ❌ 归档文件**修改正文**（违反只读原则；如需修订，写 `_archive/UPDATE_LOG.md` 指向新文档）

> **CODEMAPS 滞后是正常的**（生成器有 ~500ms~手动触发延迟）。不要为对齐 CODEMAPS 去改代码；反过来——改完代码再重生 CODEMAPS。

---

## 7. 已知历史引用（已知 dangling，待清理）

以下引用指向**已合并/移除**的文档，现行权威位置已替代：

| 旧路径（已不存在） | 现行位置 | 仍引用旧名的文件 |
|-------------------|----------|------------------|
| `docs/SECURITY_REVIEW_2026-06-06.md` | `CLAUDE.md` Security Invariants + [contest/_archive/SECURITY_REVIEW_2026-06-17.md](./contest/_archive/SECURITY_REVIEW_2026-06-17.md) | `CLAUDE.md`（仓库根）、contest/PRD、contest/REVIEW_V3 |
| `docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md` | [RUNBOOK.md](./RUNBOOK.md) 各节 + `CLAUDE.md` | RUNBOOK §9、ENV.md |

> contest/ 下的引用属**历史审查记录**（point-in-time），按 §5 保留不改正文；
> living 文档（RUNBOOK、ENV、CLAUDE.md）的引用应在下次编辑时顺手改向现行位置。

### 7.1 已完成的归档清理（2026-06-18）

| 旧路径 | 新路径 | 备注 |
|--------|--------|------|
| `docs/contest/PLAN.md` | `docs/contest/_archive/PLAN_v1.0_2026-06-17.md` | v1.0 早期设想（含 HMAC，已被代码现实取代） |
| `docs/contest/REVIEW.md` | `docs/contest/_archive/REVIEW_v1_2026-06-17.md` | v1 审查（对象 PLAN.md） |
| `docs/contest/REVIEW_V2.md` | `docs/contest/_archive/REVIEW_v2_2026-06-17.md` | v2 合并报告（对象 PLAN.md） |
| `docs/contest/SECURITY_REVIEW.md` | `docs/contest/_archive/SECURITY_REVIEW_2026-06-17.md` | Security 专项（CRIT-9/10 已用 UUID 修复） |
| `docs/contest/FINDINGS_RAW.md` | `docs/contest/_archive/FINDINGS_RAW_v1-v2_2026-06-17.md` | v1/v2 原始 finding 清单 |
| `docs/contest/DESIGN_ANALYSIS.md` | `docs/contest/_archive/DESIGN_ANALYSIS_2026-06-16.md` | 早期设计分析 |
| `docs/contest/EXECUTION_PLAN_R10.md` | `docs/contest/_archive/EXECUTION_PLAN_R10_2026-06-18.md` | R10 详细步骤 |
| `docs/contest/completed/EXECUTION_PLAN_R6.md` | `docs/contest/_archive/EXECUTION_PLAN_R6_2026-06-17.md` | R6 详细步骤 |
| `docs/contest/completed/EXECUTION_PLAN_R7.md` | `docs/contest/_archive/EXECUTION_PLAN_R7_2026-06-17.md` | R7 详细步骤 |
| `docs/contest/completed/EXECUTION_PLAN_R8.md` | `docs/contest/_archive/EXECUTION_PLAN_R8_2026-06-17.md` | R8 详细步骤 |
| `docs/contest/completed/EXECUTION_PLAN_R9.md` | `docs/contest/_archive/EXECUTION_PLAN_R9_2026-06-17.md` | R9 详细步骤 |
| `docs/contest/completed/LOW_REMAINING.md` | `docs/contest/_archive/LOW_REMAINING_R8.6_2026-06-17.md` | LOW F-35~F-47 收口 |

> 引用已同步更新到 `docs/README.md`、`docs/contest/README.md`、`docs/contest/EXECUTION_PLAN.md`。
> 后续 PR 中若仍引用旧名，应一并改为新路径（或在 §7 表格追加一行解释）。
