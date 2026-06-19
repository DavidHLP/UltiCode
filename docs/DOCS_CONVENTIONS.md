---
title: 文档编写规范
tags: [docs, governance, conventions]
status: living
updated: 2026-06-19
owner: architect
---

# 文档编写规范

> **读者**：所有在 `docs/` 下编写或维护文档的人。本规范是**总纲**；[`ops/README.md`](./ops/README.md)
> 是运维子目录的专属规范，与本规范保持一致。

## 1. 目录地图

```
docs/
├── README.md                  ← 从这里开始
├── CONTRIBUTING.md            ← 仓库级贡献指南（根目录的镜像）
├── RUNBOOK.md                 ← On-call / 事故响应
├── ENV.md                     ← 每个环境变量，单一真相源
├── DOCS_CONVENTIONS.md        ← 本文件
│
├── CODEMAPS/                  ← 自动生成的架构快照
│   ├── architecture.md        ← 拓扑 + ADR 镜像
│   ├── backend.md             ← Spring Boot 模块地图
│   ├── frontend.md            ← Vue 3 路由 / store / API
│   ├── data.md                ← MySQL schema + 迁移索引
│   ├── dependencies.md        ← 三方库 + CI 矩阵
│   └── sandbox.md             ← OJ 沙箱（D-form）
│
├── adr/                       ← 架构决策记录（不可变）
│   ├── README.md              ← 模板 + 索引 + 工作流
│   └── NNNN-slug.md           ← 一个决策一篇
│
└── ops/                       ← 运维深读（子目录规范：ops/README.md）
    ├── README.md
    └── <tool-or-scenario>.md
```

### 什么**不**进 `docs/`

| 不要放在这里                                 | 放在哪儿                                     |
| ---------------------------------------------- | -------------------------------------------------- |
| 仓库级编码规则                         | `.claude/rules/<area>/`（路径触发，自动加载） |
| 仓库级指南（拓扑、工具链、踩坑）  | 根目录的 `AGENTS.md` / `CLAUDE.md`             |
| 可复用的代理提示词                         | `.claude/agents/`                                  |
| 运维技能                             | `.agents/skills/`                                  |
| 一次性 PRP / 计划 / 报告                 | `.claude/PRPs/{plans,reports,reviews}/`（gitignored） |
| 生成的架构快照（数据源）  | 数据在代码里；`CODEMAPS/*` 是渲染结果          |

## 2. 命名规则

| 规则 | 正例 | 反例 |
| ---- | ------- | ------------ |
| `kebab-case-lowercase.md` | `judge-outbox-fencing.md` | `Judge_Outbox.md` |
| 概念优先，不以角色开头 | `arthas-mcp-usage.md` | `how-to-use-arthas.md` |
| 不加数字前缀（`01-`、`02-`）— 顺序按字母排列 | `webhook-retries.md` | `04-webhook-retries.md` |
| 缩写词大写是允许的（`csrf`、`idp`、`jwt`） | `csrf-cookie-lifecycle.md` | `cookie-lifecycle-for-csrf.md` |
| 文件名与 H1 标题对齐 | `0001-verdict-status-codec.md` ↔ `# 0001 — Verdict status codec` | `0001-foo.md` ↔ `# Bar baz` |
| `README.md` 只用于索引/着陆页 | `docs/README.md`、`docs/adr/README.md` | `docs/ops/ops-index.md` |

**特例**：
- ADR：`NNNN-slug.md`（4 位数字、零填充）
- CODEMAPS：`<area>.md`（无前缀；area 是名词）
- 顶层文档：单个 `Title-Case.md`（CONTRIBUTING、RUNBOOK、ENV、README）

## 3. Frontmatter

每份已落盘的文档（`adr/` 中**极短的**交叉引用片段除外）顶部都要带 YAML frontmatter：

```yaml
---
title: <一句话，镜像 H1>
tags: [<领域>, <状态>, <子系统>]
status: living | frozen | draft
updated: YYYY-MM-DD
owner: <模块或角色>
---
```

### `tags` 词表

使用小而受控的词表，让 tag 搜索真正可用。**先复用现有 tag，再新增**。

| 分类         | 允许的值                                                        |
| -------------- | --------------------------------------------------------------------- |
| **文档类型**   | `index`、`adr`、`runbook`、`reference`、`tutorial`、`spec`            |
| **子系统**  | `auth`、`sandbox`、`contest`、`notification`、`queue`、`security`     |
| **栈**      | `backend`、`frontend`、`database`、`devops`、`infra`                  |
| **状态**     | `living`、`frozen`、`draft`、`proposed`、`superseded`                 |
| **流程**    | `governance`、`conventions`、`template`、`workflow`                    |

`tags` 控制在 3-5 个以内。超了就拆文档。

### `status` 生命周期

| 状态      | 含义                                                                                | 6 个月未触碰的动作 |
| ----------- | -------------------------------------------------------------------------------------- | -------------------------------- |
| `living`    | 主动维护，每次发版都复核                                          | 复核或降级为 `frozen` |
| `frozen`    | 历史参考；如无必要原因不修改                                  | 移到 `archive/`（未来）      |
| `draft`     | 不完整；不要从生产文档链过去                                           | 删除或晋升为 `living`   |
| `proposed`  | （仅 ADR）评审中，尚未生效                                               | 晋升为 `accepted` 或 `rejected` |

## 4. 链接

| 想要做的                                  | 怎么做                                                       |
| ---------------------------------------------------- | ------------------------------------------------------------------ |
| 链接到 `docs/` 下另一个文件                      | 相对路径：`[`ops/arthas-mcp-usage.md`](./ops/arthas-mcp-usage.md)` |
| 链接到另一篇文档的某节                     | `[`RUNBOOK §4`](./RUNBOOK.md#4-common-issues)`（GitHub 风格锚点） |
| 链接到代码里的模块 / 类 / 文件              | 路径：`backend-spring/.../modules/auth/service/AuthService.java`   |
| 链接到某次迁移                                   | `init-db/migrations/V<ts>__<name>.sql`（写全文件名，不要缩写） |
| 链接到一篇 ADR                                        | `[`adr/0008`](./adr/0008-websocket-cookie-auth.md)` — **总是用文件名**而不是编号，这样重编号后链接依然有效 |
| 链接到某个工作流 / 脚本                           | 路径：`.github/workflows/cd-deploy.yml`、`scripts/dev/up.sh`       |
| 引用外部 RFC / 文档                       | 纯 URL 放在 `## References` 节；不要散落在正文里          |

**交叉校验规则**：所有从 `docs/README.md`（或任何子目录 `README.md`）出发的链接都必须可解析。定期执行 `find docs -name '*.md' | xargs -I {} grep -l '\.\./\.\./' {}` 排查可疑模式。

## 5. 引用代码与迁移

- **迁移**是不可变的历史事实。**总是用全文件名**（`V20260613100000__Create_Judge_Outbox.sql`）引用；不要写成"那次创建了 outbox 表的迁移"。
- **代码路径**指向**模块**，不要指向实现细节。`backend-spring/.../modules/submission/service/` 可以；`backend-spring/.../submission/service/SubmissionService.java:142` **不行** — 行号会失效。
- **模块**（Spring）和 **包**（`shared/auth-core`）是稳定引用。路由和 store 名不是。

## 6. 状态与归档

| 触发                                             | 操作                                                                 |
| --------------------------------------------------- | ---------------------------------------------------------------------- |
| 文档错误 / 过时 / 不再适用         | 就地修复，更新 `updated:`，**不要**改 `status`              |
| 文档被新文档替代                        | 旧文档改为 `status: superseded` + `superseded_by: NNNN`；新 ADR supersede 旧 ADR |
| 文档引用的功能被移除           | 移到 `archive/`（当该目录存在后）；改为 `status: frozen`        |
| 文档覆盖的模块 / 包被删除          | 移到 `archive/` + 顶部加一行 "模块已于 YYYY-MM-DD 移除" |
| 文档处于 `draft` 超过 90 天                  | 删除，或晋升为 `living`                                       |

> **为什么还没有 `archive/`？** 未来 12 个月内预计不会移除任何当前顶层文档。
> 第一次需要时再创建 `docs/archive/`，沿用本规范；不要把文档移到仓库根目录。

## 7. 更新流程

1. **编辑文档** — 在特性分支里修改。
2. **更新 `updated:`** — frontmatter 改为合入当天的日期。
3. **同步索引** — 如果改动影响了 `README.md`（顶层）或 `adr/README.md` / `ops/README.md`
   的目录，**同一个 PR** 里更新它们。
4. **交叉链接** — 这次改动会让另一篇文档的链接失效的话，**同一个 PR** 里修复。
5. **PR 清单**（在 PR 描述中粘贴，仅在文档改动时使用）：

   ```markdown
   - [ ] `updated:` frontmatter 已更新
   - [ ] 如果目录变动，`docs/README.md` 和/或子目录 `README.md` 已更新
   - [ ] 外发链接已验证
   - [ ] 如有代码路径变动，已重跑自动生成 `CODEMAPS/*`（`ecc:update-codemaps`）
   - [ ] 相关 ADR 复核过
   ```

## 8. 不要做的事

- **不要把 `AGENTS.md` 内容复制进 `docs/`。** 仓库级规则住在根目录。`docs/` 装的是
  **项目工程**知识（这套代码的架构、运维、决策），不是编码风格或工具链约束。
- **不要在文档里写密钥。** Gitleaks 会扫这棵树。要引用某个凭据，请用 `<redacted>`
  或 `.env` 变量名。
- **不要为"组织上的整洁"创建空子目录。** 先有文档再有目录，否则就别建。
- **不要为"就这一篇"打破命名约定。** 一致性胜过局部巧思；下一个维护者是按 glob 读的。
- **不要手写自动生成的 `CODEMAPS/*` 内容。** 那个 `<!-- Generated: ... -->` 头
  就是提示：如果你这次的编辑是保持它准确的唯一动作，跑 `ecc:update-codemaps`。

## 9. 推荐工具

| 工具                         | 用途                                                       |
| ---------------------------- | --------------------------------------------------------- |
| `ecc:update-codemaps`        | 从源码重新生成 `CODEMAPS/*`                       |
| `ecc:update-docs`            | 从真源文件（脚本、schema）同步文档           |
| `code-reviewer`（代理）      | 审文档 PR 的语气、完整性、断链      |
| `obsidian-vault`（技能）     | 如果你维护一个并行的 Obsidian vault，它会双向同步    |
| 根目录 `mempalace.yaml` + `entities.json` | `mempalace` 用 `documentation` 房间索引 `docs/`，用于跨项目回忆；`keywords` 与 `tags` 保持对齐 |

## 参见

- [`docs/README.md`](./README.md) — 从这里开始
- [`docs/adr/README.md`](./adr/README.md) — ADR 专属工作流
- [`docs/ops/README.md`](./ops/README.md) — 运维子目录规范
