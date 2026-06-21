---
title: 文档目录（index）
tags: [index, docs, governance]
status: living
updated: 2026-06-21
owner: architect
---

# 文档目录（index）

> `docs/` 是一个 LLM 增量维护的活 wiki。本文件是**唯一全局内容目录**——每个 wiki 页一行。规则见 [[SCHEMA]]；维护时间线见 [[log]]。
>
> 子目录（`adr/` / `ops/` / `theme/` / `CODEMAPS/`）只在此列入口 + 一句话，子域细节由各自 `README.md` 承载——**不重复造 catalog**（[[SCHEMA#9 不要做的事]]）。

## 1. 你是谁？从这里开始

| 你是… | 从这里开始 |
| --- | --- |
| **新贡献者 / 第一次提 PR** | [[CONTRIBUTING]] → [[RUNBOOK]] §3 |
| **On-call 工程师**（事故 / 故障） | [[RUNBOOK]] §0（速查表）+ §4（常见问题） |
| **架构师 / 规划者**（新功能） | [[CODEMAPS/architecture]] + [[adr/README]] |
| **后端开发**（Spring Boot / MyBatis） | [[backend]] + [[CONTRIBUTING]] §6 |
| **前端开发**（Vue 3 / console 或 management） | [[frontend]] + `.claude/rules/frontend-rules.md` + [[0012-shared-auth-ui-extraction\|adr/0012]] |
| **数据库工程师**（Flyway / MySQL） | [[data]] + `.claude/rules/database/01-flyway-migrations.md` |
| **沙箱 / 评测开发** | [[sandbox]] + [[0002-sandbox-hexagonal-dform\|adr/0002]] + [[sandbox-dform]] |
| **运维**（部署 / 环境 / 密钥） | [[ENV]] + [[RUNBOOK]] §1–2 + [[0005-rolling-deploy-rollback\|adr/0005]] |
| **安全审查** | [[0008-websocket-cookie-auth\|adr/0008]] + [[0011-crit6-shadow-mode\|adr/0011]] + [[0012-shared-auth-ui-extraction\|adr/0012]] |
| **维护 docs/ 知识库**（人类或 LLM） | [[SCHEMA]]（三层 / 三动作 / 写作规范）+ [[log]] |

## 2. 顶层文档

| 文件 | 作用 | 最后更新 |
| --- | --- | --- |
| [[SCHEMA]] | **wiki 工作流 + 写作规范合一**：三层（raw/wiki/schema）、三动作（ingest/query/maintain）、命名 / frontmatter / 链接 / 归档 | 2026-06-21 |
| [[CONTRIBUTING]] | 开发环境搭建、代码风格、PR 清单、评审礼仪 | 2026-06-19 |
| [[RUNBOOK]] | On-call 手册：启动、健康检查、常见问题、回滚 | 2026-06-19 |
| [[ENV]] | `.env` / Nacos / Vite 中每一个环境变量 — 含来源 | 2026-06-19 |

## 3. 子目录

| 入口 | 作用 |
| --- | --- |
| [[CODEMAPS/README]] | 自动生成的架构快照：拓扑、模块、schema、依赖、沙箱（`ecc:update-codemaps` 周更） |
| [[adr/README]] | 架构决策记录 — 一个不那么显然选择背后的**原因** |
| [[ops/README]] | 运维深读：Arthas MCP、日志查询、工具参考 |
| [[theme/README]] | 前端主题系统：Token、颜色模式、密度、组件原语、扩展指南 |

## 4. 实体页（entities/）

关于某个稳定实体的**综合总页**——跨 ADR / CODEMAPS / ops 综合，随源更新。

| 页 | 一句话 | 更新 |
| --- | --- | --- |
| [[contest]] | 比赛实体：评分激活、等级、虚拟赛、取消态（综合 adr/0006–0010） | 2026-06-21 |
| [[submission]] | 提交 / 判题实体：verdict 编解码、outbox fencing、防重（综合 adr/0001/0003/0005a） | 2026-06-21 |
| [[sandbox-dform]] | D-form 沙箱：六边形架构、verdict pipeline、Python preamble 契约（综合 adr/0001–0003） | 2026-06-21 |

## 5. 概念页（concepts/）

横切概念——一个 idea 跨多个模块。

| 页 | 一句话 | 更新 |
| --- | --- | --- |
| [[exactly-once]] | 精确一次交付模式：outbox + fencing + 防重列（综合 adr/0003/0004） | 2026-06-21 |
| [[virtual-contest]] | 虚拟比赛：评级隔离、isRated 门控、取消态回放（综合 adr/0006/0007/0009/0010） | 2026-06-21 |

## 6. 按任务索引

### 「我需要…」

| 任务 | 跳转 |
| --- | --- |
| 第一次搭建开发环境 | [[CONTRIBUTING#2 First Time Setup\|`CONTRIBUTING.md` §2]] |
| 新增后端模块 | [[CONTRIBUTING\|`CONTRIBUTING.md` §3 + §6]] + [[backend]] |
| 新增 Flyway 迁移 | [[data]] + `.claude/rules/database/01-flyway-migrations.md` |
| 新增被两个前端共同消费的共享包 | [[CONTRIBUTING\|`CONTRIBUTING.md` §6 (Shared)]] + [[frontend]] + [[0012-shared-auth-ui-extraction\|adr/0012]]（参考：`shared/auth-ui` 模板） |
| 修改 auth UI / 视觉一致性 | [[frontend]] §"Shared Packages" + [[0012-shared-auth-ui-extraction\|adr/0012]] |
| 排查 9001 启动崩溃循环 | [[RUNBOOK#41 Backend Crash Loops On Startup Count Rising\|`RUNBOOK.md` §4.1]] |
| 回滚一次失败的部署 | [[RUNBOOK#53 Container Rollback Prod\|`RUNBOOK.md` §5.3]] + [[0005-rolling-deploy-rollback\|adr/0005]] |
| 新增环境变量 | [[ENV]]（完整表）+ `application.yml` + `docker-compose.yml` |
| 在 Claude 会话里使用 Arthas | [[arthas-mcp-usage]]（深读） |
| 为一个不那么显然的设计决策举证 | 在 [[adr/README]] 下写一篇新 ADR |
| 改前端颜色 / 字体 / 密度 / 加新主题 | [[theme/README]] 整套 + `.claude/rules/frontend-rules.md` |

### 「我在评审一个 PR…」

| PR 类型 | 要读的文档 |
| --- | --- |
| 新增后端模块 / 服务 | `springboot-rules.md` + 对应模块的 CODEMAPS 段 + 相关 entity 页 |
| 新增 Flyway 迁移 | `database/01-flyway-migrations.md` + 本次迁移对应的 ADR（如有） |
| 修改鉴权 / 安全 | `adr/0008` + `security-reviewer` 代理规则 + `adr/0012`（如果触动 shared/auth-ui） |
| 修改共享 DTO / 枚举 | `cross-stack-dto-granularity-alignment` 技能 + 相关 ADR |
| 修改部署 / 回滚 | `adr/0005` + `adr/0005a` + `RUNBOOK.md` §5 |
| 修改比赛评分 / 等级 | [[contest]] + `adr/0006` + `adr/0007` + `adr/0009` + `adr/0010` |
| 修改沙箱 / 评测 | [[sandbox-dform]] + `adr/0002` + [[sandbox]] |
| 修改前端主题 | [[theme/README]] 全套 + `frontend-rules.md` |

## 7. 不在这里的内容

这些东西在别处 — 不要重复：

- **仓库级规范与踩坑** → [`AGENTS.md`](../AGENTS.md)、[`CLAUDE.md`](../CLAUDE.md)
- **路径触发的编码规则** → [[../.claude/rules/README|.claude/rules/]]
- **可复用的代理提示词** → [`.claude/agents/`](../.claude/agents/)
- **运维技能** → [`.agents/skills/`](../.agents/skills/)
- **一次性 PRP / 计划 / 评审** → `.claude/PRPs/`（gitignored）
- **工具缓存** → `docs/.claudian/`、`docs/.obsidian/`（本地，gitignored）
