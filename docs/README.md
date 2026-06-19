---
title: UltiCode 工程文档
tags: [index, docs, governance]
status: living
updated: 2026-06-19
owner: architect
---

# UltiCode 工程文档

> **你在这里**：`docs/` 是**项目级**的工程知识库。
> 仓库级规范与踩坑见根目录的 [`AGENTS.md`](../AGENTS.md) 和 [`CLAUDE.md`](../CLAUDE.md)。

## 1. 先读这份

| 你是…                                | 从这里开始                                                      |
| -------------------------------------------- | --------------------------------------------------------------- |
| **新贡献者 / 第一次提 PR**               | [`CONTRIBUTING.md`](./CONTRIBUTING.md) → [`RUNBOOK.md`](./RUNBOOK.md) §3 |
| **On-call 工程师**（事故 / 故障）     | [`RUNBOOK.md`](./RUNBOOK.md) §0（速查表）+ §4（常见问题） |
| **架构师 / 规划者**（新功能）        | [`CODEMAPS/architecture.md`](./CODEMAPS/architecture.md) + [`adr/`](./adr/) |
| **后端开发**（Spring Boot / MyBatis）      | [`CODEMAPS/backend.md`](./CODEMAPS/backend.md) + [`CONTRIBUTING.md`](./CONTRIBUTING.md) §6 |
| **前端开发**（Vue 3 / console 或 management）   | [`CODEMAPS/frontend.md`](./CODEMAPS/frontend.md) + `.claude/rules/frontend-rules.md` |
| **数据库工程师**（Flyway / MySQL）            | [`CODEMAPS/data.md`](./CODEMAPS/data.md) + `.claude/rules/database/01-flyway-migrations.md` |
| **沙箱 / 评测开发**                     | [`CODEMAPS/sandbox.md`](./CODEMAPS/sandbox.md) + [`adr/0002`](./adr/0002-sandbox-hexagonal-dform.md) |
| **运维**（部署 / 环境 / 密钥）       | [`ENV.md`](./ENV.md) + [`RUNBOOK.md`](./RUNBOOK.md) §1–2 + [`adr/0005`](./adr/0005-rolling-deploy-rollback.md) |
| **安全审查**                        | [`adr/0008`](./adr/0008-websocket-cookie-auth.md) + [`adr/0011`](./adr/0011-crit6-shadow-mode.md) |

## 2. 顶层文档

| 文件                                           | 作用                                                                 | 负责人     | 最后更新 |
| ---------------------------------------------- | ----------------------------------------------------------------------- | --------- | ------------ |
| [`CONTRIBUTING.md`](./CONTRIBUTING.md)         | 开发环境搭建、代码风格、PR 清单、评审礼仪                   | architect | 2026-06-19   |
| [`RUNBOOK.md`](./RUNBOOK.md)                   | On-call 手册：启动、健康检查、常见问题、回滚      | devops    | 2026-06-19   |
| [`ENV.md`](./ENV.md)                           | `.env` / Nacos / Vite 中每一个环境变量 — 含来源      | backend   | 2026-06-19   |
| [`DOCS_CONVENTIONS.md`](./DOCS_CONVENTIONS.md) | 在本目录树下如何编写、命名、打标签、归档、链接文档      | architect | 2026-06-19   |

## 3. 子目录

| 路径                                            | 作用                                                                                                                            | 何时往这里加文档                          |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| [`CODEMAPS/`](./CODEMAPS/)                      | 自动生成的架构快照：拓扑、模块、schema、依赖、沙箱。由 `ecc:update-codemaps` 更新。                | 系统级架构 / 模块地图          |
| [`adr/`](./adr/)                                | 架构决策记录（Architecture Decision Records）— 一个不那么显然的选择背后的**原因**                                                               | 不那么显然、横切多端、难以回退    |
| [`ops/`](./ops/)                                | 运维深读：Arthas MCP、日志查询、工具参考                                                               | 具体工具或场景的参考（非政策） |
| [`theme/`](./theme/)                            | 前端主题系统：Token、颜色模式、密度、组件原语、扩展指南 | 改前端颜色/字体/密度/动效、加新主题或新密度档 |

## 4. 按任务索引

### 4.1 「我需要…」

| 任务                                                    | 跳转                                                                          |
| ------------------------------------------------------- | ------------------------------------------------------------------------------ |
| 第一次搭建开发环境          | [`CONTRIBUTING.md` §2](./CONTRIBUTING.md#2-first-time-setup)                    |
| 新增后端模块                                    | [`CONTRIBUTING.md` §3 + §6](./CONTRIBUTING.md) + [`CODEMAPS/backend.md`](./CODEMAPS/backend.md) |
| 新增 Flyway 迁移                                  | [`CODEMAPS/data.md`](./CODEMAPS/data.md) + `.claude/rules/database/01-flyway-migrations.md` |
| 新增被两个前端共同消费的共享包      | [`CONTRIBUTING.md` §6 (Shared)](./CONTRIBUTING.md) + [`CODEMAPS/frontend.md`](./CODEMAPS/frontend.md) |
| 排查 9001 启动崩溃循环                                 | [`RUNBOOK.md` §4.1](./RUNBOOK.md#41-backend-crash-loops-on-startup--count-rising) |
| 回滚一次失败的部署                               | [`RUNBOOK.md` §5.3](./RUNBOOK.md#53-container-rollback-prod) + [`adr/0005`](./adr/0005-rolling-deploy-rollback.md) |
| 新增环境变量                                        | [`ENV.md`](./ENV.md)（完整表） + `application.yml` + `docker-compose.yml`   |
| 在 Claude 会话里使用 Arthas                          | [`ops/arthas-mcp-usage.md`](./ops/arthas-mcp-usage.md)（深读）             |
| 在 code review 里为一个不那么显然的设计决策举证       | 在 [`adr/`](./adr/) 下写一篇新 ADR（模板：[`adr/README.md`](./adr/README.md)） |
| 改前端颜色 / 字体 / 密度 / 加新主题                  | [`theme/`](./theme/) 整套（6 篇，按需挑） + `.claude/rules/frontend-rules.md` |

### 4.2 「我在评审一个 PR…」

| PR 类型                            | 要读的文档                                              |
| ---------------------------------- | -------------------------------------------------------------- |
| 新增后端模块 / 服务    | `springboot-rules.md` + 对应模块的 CODEMAPS 段     |
| 新增 Flyway 迁移            | `database/01-flyway-migrations.md` + 本次迁移对应的 ADR（如有） |
| 修改鉴权 / 安全            | `adr/0008` + `security-reviewer` 代理规则                   |
| 修改共享 DTO / 枚举        | `cross-stack-dto-granularity-alignment` 技能 + 相关 ADR   |
| 修改部署 / 回滚          | `adr/0005` + `adr/0005a` + `RUNBOOK.md` §5                     |
| 修改比赛评分 / 等级   | `adr/0006` + `adr/0007` + `adr/0009` + `adr/0010`              |
| 修改前端主题（颜色/字体/密度） | [`theme/`](./theme/) 全套 + `frontend-rules.md`  |

## 5. 维护契约

| 节奏   | 操作                                                                                            |
| --------- | ------------------------------------------------------------------------------------------------- |
| 每个 PR    | 触到文档时，更新它的 `updated:` frontmatter 和本 README 中相关索引       |
| 每周    | `ecc:update-codemaps` 从源码重新生成 `CODEMAPS/*`（自动）                                  |
| 每季度 | 手工核对 `adr/` 索引与 `CODEMAPS/architecture.md` 镜像表，修正偏差                 |
| 被替代时 | 新 ADR 把旧 ADR 置为 `status: superseded`；两份文件在索引中并存                  |

完整规则见 [`DOCS_CONVENTIONS.md`](./DOCS_CONVENTIONS.md)。

## 6. 不在这里的内容

这些东西在别处 — 不要重复：

- **仓库级规范与踩坑** → [`AGENTS.md`](../AGENTS.md)、[`CLAUDE.md`](../CLAUDE.md)
- **路径触发的编码规则** → [`.claude/rules/`](../.claude/rules/)
- **可复用的代理提示词** → [`.claude/agents/`](../.claude/agents/)
- **运维技能** → [`.agents/skills/`](../.agents/skills/)
- **一次性 PRP / 计划 / 评审** → `.claude/PRPs/`（gitignored；布局见
  `~/.claude/projects/.../memory/MEMORY.md`）

## 7. 工具缓存（非真理之源）

- `docs/.claudian/` — 本地 Obsidian / Claudian 会话缓存；gitignored
- `docs/.obsidian/` — 本地 Obsidian 工作区配置；gitignored
