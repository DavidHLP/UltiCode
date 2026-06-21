---
title: 文档 Wiki Schema
tags: [schema, governance, workflow]
status: living
updated: 2026-06-21
owner: architect
---

# 文档 Wiki Schema

> **定位**：`docs/` 是一个由 LLM **增量维护、持续累积、互相链接**的活知识库——不是「被读的静态文档」。本文件是这套工作流的**唯一 schema**：它同时定义 *理念*（三层 / 三动作）和*静态规范*（命名 / frontmatter / 链接 / 更新流程）。
>
> 灵感来自 karpathy 的 *LLM Wiki* 模式：与其每次提问都从原始文档重新检索（RAG），不如让 LLM 一次性把知识编译进一个**持久、互链、持续维护**的 wiki，然后保持它最新。wiki 是一个随每个新源、每次探索不断增值的复利产物——交叉引用已在、矛盾已被标记、综述已反映读过的所有东西。
>
> **入口**：[[index]] 是 wiki 内容目录；[[log]] 是维护时间线；本文件是规则。

## 0. 这是什么，为什么

维护一个知识库真正累的不是读和想，而是**记账**——更新交叉引用、保持摘要新鲜、标记新数据与旧结论的矛盾、维持几十页之间的一致性。人类会放弃 wiki，因为维护成本增长快于价值。LLM 不嫌烦、不忘更新交叉引用、能一次触碰十几页。

**分工**：你（人类）负责**寻源、探索、提问**；LLM 负责**总结、交叉引用、归档、记账**。人类极少手写 wiki 页——手写仅限于决策的「为什么」叙事和本 schema。

### 三层分工（不重叠）

| 层 | 是什么 | 谁拥有 |
| --- | --- | --- |
| **raw** 原始源 | 代码本身（一等）+ 外部文章 / gist / 调研 / 运行现象 | 人类收集；代码即真源 |
| **wiki** 层 | `docs/` 下所有综合产物（entity / concept / decision / codemap / ops / theme 页） | **LLM 增量维护**；人类读、寻源、提问 |
| **schema** | 本文件 + [[index]] + [[log]] | 人类与 LLM 共同演进 |

## 1. 三层

### 1.1 raw —— 知识从哪里来

UltiCode 是工程项目，原始源分三类：

**A. 代码源（一等公民，无需收集）** —— `backend-spring/`、`console/`、`management/`、`shared/`、`init-db/migrations/`。代码是真理之源，已被两条管道自动综合：

- [[codemap/README]] —— 架构镜像（首批手写，可由 `ecc:update-codemaps` 接管）
- codegraph MCP —— 实时符号图（`codegraph_context` / `codegraph_trace` / `codegraph_impact`）

ingest 代码源 = PR 触碰的模块；**无需**手工搬进 raw。

**B. 外部源（需要主动收集）** —— 技术文章、规范、第三方 gist（如本 schema 的灵感来源 karpathy llm-wiki）、调研笔记、线上事故复盘。放进 `docs/raw/<source-slug>.md`（原文 + 批注）。`docs/raw/` **按需创建**：有第一个外部源要 ingest 时才建，不预先建空目录。

**C. 运行现象** —— Arthas / 日志 / 监控里观察到的实际行为。沉淀路径：工具向 → [[ops/README]]；认知向 → 对应 entity / concept 页的「已知行为」段。

### 1.2 wiki —— 页面类型

| 类型 | 目录 | 记什么 | 何时写 | 生命周期 |
| --- | --- | --- | --- | --- |
| **实体页** | `entities/` | **关于某个稳定实体的一切**（跨 decision / codemap / ops 综合） | 该实体被 ≥2 处引用、值得有「总页」 | living |
| **概念页** | `concepts/` | **横切概念**（exactly-once、virtual contest） | 一个 idea 跨多个模块 | living |
| 决策记录 | `decisions/` | 一个不那么显然决策的**为什么** | 决策点 | accepted 后 frozen |
| 架构镜像 | `codemap/` | 代码**现在长什么样**（镜像） | 手写基线；可由 `ecc:update-codemaps` 接管 | 随源覆盖 |
| 运维深读 | `ops/` | 工具 / 场景参考 | 引入新工具或场景 | living |
| 主题专题 | `theme/` | 主题系统切面 | 改前端颜色 / 字体 / 密度 | living |

**实体页 vs 决策 vs 镜像**（关键区分）：

- **决策**记「为什么这么定」（一次，frozen）。
- **镜像**记「代码现在是什么样」（随源覆盖）。
- **实体页 / 概念页**记「关于 X 的综合认知」——把散落在决策 / 镜像 / ops / 代码里关于 X 的信息**综合**成一页，并随每个新源更新、标记矛盾。**这是 llm-wiki 模式的核心产物：知识编译一次、保持最新，而非每次查询重算。**

### 1.3 schema —— 本文件

本文件 + [[index]] + [[log]]。跑顺后，重复出现的约定沉淀回本文件相应章节。

## 2. 三动作工作流

### 2.1 Ingest —— 纳入新源

| 触发 | 动作 |
| --- | --- |
| 代码改动 PR | 触碰模块若有 entity / concept 页 → 更新；新模块 → 评估是否建 entity 页；满足决策触发条件 → 写决策记录；镜像按需刷新 |
| 外部源（文章 / gist） | 丢进 `docs/raw/` → LLM 读 → 提取 → 更新 / 新建相关 wiki 页 → frontmatter `sources:` 记溯源 → 与现有页矛盾则显式标记 |
| 运行现象（日志 / Arthas） | 工具向沉淀进 [[ops/README]]；认知向沉淀进对应 entity / concept 页的「已知行为」段 |

**每个 ingest 动作的四件事**：

1. 更新或新建相关 wiki 页
2. 在 frontmatter `sources:` 记溯源
3. 发现与现有页矛盾时显式标记（见 [§2.3](#23-maintain--保持新鲜)）
4. **新建 / 重命名 / 删除任何页 → 更新 [[index]] → 追加 [[log]]**（否则目录会与实际漂移）

### 2.2 Query —— 提问与综合

回答前**先查 wiki**，按这个顺序（从结构化到字面量）：

1. `codegraph_*` —— 代码符号 / 调用链 / 影响面（结构化，最快）
2. [[index]] —— 先读目录，定位相关页再 drill in（中等规模下 index 足够，无需 embedding RAG）
3. `ctx_search` —— 已索引内容（本 wiki + 已 ingest 的外部源）
4. Obsidian backlinks / outgoing links —— 看这页连着什么
5. `grep` —— 字面量（字符串、注释、日志、迁移文件名）

回答**带引用**——指向相关笔记或字面量代码路径（规则见 [§6](#6-链接)）。

> **关键洞察**：可复用的综合——「X 与 Y 的关系」「X 的总览」——**归档回 wiki** 作为新页（entity / concept / 对比表）。探索也要复利，不要让好答案消失在对话历史里。

### 2.3 Maintain —— 保持新鲜

- **交叉引用可解析**：所有双链都指向 vault 内真实 `.md`
- **`updated:` 新鲜**：触碰即改 frontmatter 日期
- **矛盾要么解决、要么显式标记**——用 Obsidian callout，不要静默并存：

  ```markdown
  > [!warning] 与 [[0008-websocket-cookie-auth]] 存在张力
  > 此处说「query token 可接受」，0008 明确禁止。以 0008 为准；本段待修订。
  ```

- **孤儿页**（无入链）定期审视：补链，或归档

## 3. index.md 与 log.md 格式

### 3.1 [[index]] —— content catalog

- **唯一全局目录**。每页一行：`- [[link]] 一句话 summary · status · updated`
- 按 category 分组：entity / concept / decisions / codemap / ops / theme / reference
- 对子目录（`decisions/`、`ops/`、`theme/`、`codemap/`）只列**入口 + 一句话**，不展开其内部页
- **保留角色入口表**（「你是新贡献者 / on-call / 架构师… → 从哪开始」）和「按任务索引」
- 每次 ingest 新建 / 重命名 / 删除页时同步更新

### 3.2 [[log]] —— append-only 时间线

- 条目格式：`## [YYYY-MM-DD] ingest|maintain|bootstrap | Title`（可被 `grep "^## \[" log.md | tail -5` 解析）
- 记录：ingest 了什么源、maintain 做了什么、lint 通过情况
- 给出 wiki 演化的时间线，帮 LLM 理解最近发生过什么

## 4. 命名规则

| 规则 | 正例 | 反例 |
| --- | --- | --- |
| `kebab-case-lowercase.md` | `judge-outbox-fencing.md` | `Judge_Outbox.md` |
| 概念优先，不以角色开头 | `arthas-runtime-diagnostics.md` | `how-to-use-arthas.md` |
| 不加数字前缀（`01-`、`02-`）— 顺序按字母排列 | `webhook-retries.md` | `04-webhook-retries.md` |
| 缩写词大写是允许的（`csrf`、`jwt`、`oauth`） | `csrf-cookie-lifecycle.md` | `cookie-lifecycle-for-csrf.md` |
| 文件名与 H1 标题对齐 | `sandbox-d-form.md` ↔ `# Sandbox D-form` | `foo.md` ↔ `# Bar baz` |
| `README.md` 只用于索引 / 着陆页 | `docs/README.md`、`docs/decisions/README.md` | `docs/ops/ops-index.md` |

**特例**：

- **决策记录**：`NNNN-slug.md`（4 位数字、零填充，如 `0001-judge-outbox-and-generation-fencing.md`）。目录名用直白的 `decisions/`（而非 `adr/` 缩写）。
- **架构镜像**：`<area>.md`（无前缀；area 是名词）。目录名用小写 `codemap/`（与其余目录统一）。
- **顶层文档**：单个 `Title-Case.md`（README、SCHEMA、CONTRIBUTING、RUNBOOK、ENV）。

## 5. Frontmatter

每份已落盘的文档顶部都要带 YAML frontmatter：

```yaml
---
title: <一句话，镜像 H1>
tags: [<领域>, <状态>, <子系统>]
status: living | frozen | draft
updated: YYYY-MM-DD
owner: <模块或角色>
---
```

### 5.1 wiki 扩展字段（entity / concept 页必填）

- `sources:` —— 溯源列表（代码路径 / 迁移全文件名 / 外部 URL）。让「这页认知从哪来」可审计。
- `aliases:` —— Obsidian 别名，利搜索 / graph（如实体页 `aliases: [提交, 判题结果]`）。

决策记录 / 镜像 / reference 页按需补 `sources:`，不强制。

### 5.2 `tags` 词表

使用小而受控的词表，让 tag 搜索真正可用。**先复用现有 tag，再新增**。

| 分类 | 允许的值 |
| --- | --- |
| **文档类型** | `entity`、`concept`、`decision`、`mirror`、`runbook`、`reference`、`schema`、`index` |
| **子系统** | `judging`、`sandbox`、`contest`、`auth`、`notification`、`queue`、`security`、`forum`、`moderation` |
| **子系统 Facets** | `exactly-once`、`virtual`、`shadow`、`rating`、`scoring`、`lease`、`fence`、`lifecycle`、`websocket`、`deploy`、`rollback`、`observability`、`diagnostics` |
| **栈** | `backend`、`frontend`、`database`、`devops`、`infra` |
| **文档范围** | `docs`、`ops`、`architecture`、`contributing`、`env`、`incident` |
| **状态** | `living`、`frozen`、`draft`、`superseded` |
| **流程** | `governance`、`conventions`、`workflow` |

`tags` 控制在 3-5 个以内。超了就拆文档。新增前先看本表是否已有近义词。

### 5.3 `status` 生命周期

| 状态 | 含义 | 长期未触碰的动作 |
| --- | --- | --- |
| `living` | 主动维护，每次发版都复核 | 复核或降级为 `frozen` |
| `frozen` | 历史参考；如无必要原因不修改 | 移到 `archive/`（未来） |
| `draft` | 不完整；不要从生产文档链过去 | 删除或晋升为 `living` |
| `superseded` | （主要决策记录）已被新决策取代 | 在新决策中互相引用 |

## 6. 链接

`docs/` 是一个 **Obsidian 双链知识库**（vault 根 = `docs/`）。vault 内笔记之间用 **`[[wikilink]]`**；代码路径、迁移文件名、仓库根文件、外部 URL 保持**字面量**（它们不是 vault 笔记）。

> **取舍**：`[[wikilink]]` 在 GitHub 上显示为纯文本（不可点），换来 Obsidian 图谱 / backlinks / outgoing links 全功能。GitHub 端浏览靠「按住 Alt/Cmd 点击 wikilink」或直接在 Obsidian 里打开本 vault。

| 想要做的 | 怎么做 |
| --- | --- |
| 链接到 vault 内另一篇笔记 | `[[arthas-runtime-diagnostics]]`（最短 basename；别名加 `\|`：`[[RUNBOOK\|手册]]`） |
| 链接到另一篇笔记的某节 | `[[SCHEMA#6-链接\|SCHEMA §6]]`（用**真实标题文本**） |
| 链接到一篇决策记录 | `[[0001-judge-outbox-and-generation-fencing]]` — **总是用文件名**而非编号，重编号后链接仍有效 |
| 链接到 `decisions/`、`theme/` 等子目录索引 | 指向其 README，**带路径前缀**消除 basename 歧义：`[[decisions/README]]`、`[[theme/README]]`（多个 `README.md` 同名，裸 `[[README]]` 会歧义） |
| 链接到代码里的模块 / 类 / 文件 | **字面量路径**：`backend-spring/src/main/java/com/ulticode/modules/submission/` |
| 链接到某次迁移 | **字面量全文件名**：`init-db/migrations/V20260613100000__Create_Judge_Outbox.sql`（不缩写） |
| 链接到仓库根文件（`AGENTS.md`、`CLAUDE.md`） | **Markdown 相对路径**：`[`AGENTS.md`](../AGENTS.md)`（vault 外，不转 wikilink） |
| 链接到某个工作流 / 脚本 | **字面量路径**：`.github/workflows/ci.yml`、`scripts/dev/up.sh` |
| 引用外部 RFC / 文档 | 纯 URL 放在 `## 参考 / References` 节；不要散落在正文里 |

**交叉校验**：所有从索引笔记（[[index]]、子目录 `README.md`）出发的 `[[wikilink]]` 都必须可解析为 vault 内某篇 `.md`。定期排查越界链接（指向 `docs/` 之外的 `../` 路径）确保它们都是有意保留的字面量。

## 7. 引用代码与迁移

- **迁移**是不可变的历史事实。**总是用全文件名**（`V20260613100000__Create_Judge_Outbox.sql`）引用；不要写成「那次创建了 outbox 表的迁移」。
- **代码路径**指向**模块**，不要指向实现细节。`backend-spring/src/main/java/com/ulticode/modules/submission/fence/` 可以；`SubmissionStateMachine.java:142` **不行** — 行号会失效。
- **模块**（Spring `modules/*`）和 **包**（`shared/auth-core`）是稳定引用。路由和 store 名不是。

## 8. 更新流程

**节奏**：

| 节奏 | 操作 |
| --- | --- |
| 每个 PR | 触到文档时，更新它的 `updated:` frontmatter、相关 [[index]] 条目；新建 / 删除页时追加 [[log]] |
| 每周 | 触碰面大时用 `ecc:update-codemaps` 从源码重新生成 `codemap/*` |
| 每季度 | 手工核对决策索引与镜像表，修正偏差 |
| 被替代时 | 新决策把旧决策置为 `status: superseded` + `superseded_by: NNNN`；两份文件在索引中并存 |

**单次编辑步骤**：

1. **编辑文档**。
2. **更新 `updated:`** — frontmatter 改为当天日期。
3. **同步 [[index]]** — 如果改动新建 / 重命名 / 删除了页，**同一个 PR** 里更新 [[index]] 并追加 [[log]]。
4. **交叉链接** — 这次改动会让另一篇文档的 `[[wikilink]]` 失效的话，**同一个 PR** 里修复。

## 9. 不要做的事

- **不要造第二个 catalog。** [[index]] 是唯一全局目录；子目录 `README.md` 是子域目录。两者不重复——顶层 index 对子目录只列入口 + 一句话。
- **不要把 `AGENTS.md` 内容复制进 `docs/`。** 仓库级规则住在根目录。`docs/` 装的是**项目工程**知识（这套代码的架构、运维、决策），不是编码风格或工具链约束。
- **不要在文档里写密钥。** Gitleaks 会扫这棵树。要引用某个凭据，请用 `<redacted>` 或 `.env` 变量名。
- **不要为「组织上的整洁」创建空子目录。** 先有文档再有目录，否则就别建（`raw/`、`archive/` 都是首次需要时才建）。
- **不要为「就这一篇」打破命名约定。** 一致性胜过局部巧思；下一个维护者是按 glob 读的。
- **不要把代码路径 / 迁移名 / 仓库根文件转成 `[[wikilink]]`。** 它们不是 vault 笔记，转了会在图谱里产生断链。wikilink 只用于 `docs/` 内的 `.md` 笔记之间。

## 10. 启动状态

- 已建首批 **entity 页**：[[submission]]、[[contest]]、[[sandbox-d-form]]、[[judge-queue]]、[[refresh-token]]。
- 已建首批 **concept 页**：[[exactly-once-judging]]、[[virtual-contest]]、[[shadow-mode-cutover]]、[[notification-idempotency]]。
- 已建首批 **决策记录**：[[decisions/README]] + [[0001-judge-outbox-and-generation-fencing]]、[[0002-sandbox-d-form-hexagonal]]、[[0003-refresh-token-hash-only-storage]]、[[0004-notification-intent-and-delivery-ledger]]。
- 已建首批 **架构镜像**：[[codemap/README]] + backend-modules、judging-pipeline、frontend-apps。
- 已建首批 **运维深读**：[[ops/README]] + arthas-runtime-diagnostics。
- 已建 **主题专题**入口：[[theme/README]]。
- 当前 `docs/` **没有** `raw/`——在**第一次真实外部源 ingest 需求**时创建。
- 本 schema 与 [[index]] / [[log]] **共同演进**：跑顺后，重复出现的约定沉淀回本文件相应章节。

## 11. 工具栈

| 工具 | 在 wiki 工作流里的用途 |
| --- | --- |
| codegraph MCP（`codegraph_*`） | **ingest 代码源 / query 代码行为**的首选：符号、调用链、影响面、踪迹 |
| `ctx_search` / `ctx_batch_execute` | query 时检索已索引内容，原始字节不进对话 |
| Obsidian（桌面）+ graph view | **本 vault 原生浏览器**：看 wiki 形态、哪些页是 hub、哪些是孤儿 |
| [[index]] / [[log]] | query 入口 / 维护时间线（见 [§3](#3-indexmd-与-logmd-格式)） |
| `ecc:update-codemaps` | ingest 代码改动后重生成 [[codemap/README]] 镜像 |

## 12 / 参考 / References

- karpathy, *LLM Wiki* —— <https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f>（本 schema 的理念来源）
- [[index]] — wiki 内容目录
- [[log]] — 维护时间线
- [[decisions/README]] — 决策记录专属工作流
- [[codemap/README]] — 架构镜像约定
- [[ops/README]] — 运维子目录规范
- [[theme/README]] — 主题系统专题
