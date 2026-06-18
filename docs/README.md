<!-- Hand-maintained master index. NOT auto-generated. Edit freely.
     If you add/rename/move a doc, update the table here and the relevant area README. -->

# UltiCode 工程文档中心

本目录（`docs/`）是 UltiCode 仓库的**工程文档单一入口**：架构地图、运维手册、
架构决策记录（ADR）、特性项目工作区。仓库根目录的 `AGENTS.md` / `CLAUDE.md` 是
**AI 协作指南**，与本目录互补——分工见 [§5 与仓库根文档的关系](#5-与仓库根文档的关系)。

> 📌 **入口速查**：不知道去哪 → 看 [§2 按问题找文档](#2-按问题找文档problem--solution)；
> 查架构 → [CODEMAPS/](./CODEMAPS/)；查“为什么这么设计”→ [adr/](./adr/)；
> 查“怎么跑/怎么修”→ [RUNBOOK.md](./RUNBOOK.md)。

---

## 1. 目录总览（4 个桶，各司其职）

| 桶 | 路径 | 性质 | 维护方式 | 入口 |
|----|------|------|----------|------|
| **运维参考** | `docs/*.md`（根级） | 长期 living 文档：部署、环境、隐私、贡献指南、Arthas 实战 | 手工 | 本文件 §3 |
| **架构地图** | [CODEMAPS/](./CODEMAPS/) | 代码结构快照（模块/路由/依赖/数据/沙箱） | **自动生成**（`ecc:update-codemaps`） | [CODEMAPS/architecture.md](./CODEMAPS/architecture.md) |
| **架构决策** | [adr/](./adr/) | ADR-000…011，append-only，不可改名 | 手工，走评审流程 | [adr/README.md](./adr/README.md) |
| **特性项目** | [contest/](./contest/) | 单个特性（Contest）的 PRD / 计划 / 审计 / 决策工作区 | 手工，完成轮次归档到 `_archive/` | [contest/README.md](./contest/README.md) |

**新增文档时的归属判断**（详见 [DOCS-SPEC.md §放置规则](./DOCS-SPEC.md)）：

- 跨模块架构决策 → `adr/ADR-NNN-*.md`
- 某个特性的需求/设计/审查 → 建 `docs/<feature>/`（以 `contest/` 为模板）
- 代码结构地图 → 不要手写，改代码后重跑 `ecc:update-codemaps`
- 部署/运维/环境/规范 → `docs/` 根级 `*.md`

---

## 2. 按问题找文档（Problem → Solution）

> 这一节是**关系索引的核心**：从“我想做什么 / 遇到什么问题”直接跳到文档。

### 🚀 启动 / 部署 / 运维

| 我要… | 去哪 |
|-------|------|
| 首次拉起整个栈（infra + 迁移 + 应用） | [RUNBOOK §2 Startup](./RUNBOOK.md#2-startup--shutdown) · [ENV.md §One-Command](./ENV.md#one-command-workflow) |
| 看某个端口/容器对应什么、日志在哪 | [RUNBOOK §1 Stack Overview](./RUNBOOK.md#1-stack-overview) |
| 健康检查（不用 actuator） | [RUNBOOK §3 Health Checks](./RUNBOOK.md#3-health-checks) |
| 排查 `Connection refused` / 中文乱码 / Arthas 超时 / Java 版本 / Flyway drift / Node `crypto.hash` | [RUNBOOK §4 Common Issues](./RUNBOOK.md#4-common-issues) |
| 回滚代码 / 回滚迁移 / 回滚容器镜像 | [RUNBOOK §7 Rollback](./RUNBOOK.md#7-rollback-procedures) |
| Feature flag 切换与紧急回滚 | [RUNBOOK §10 Feature Flag 手册](./RUNBOOK.md#10-feature-flag-切换手册) · [ADR-005](./adr/ADR-005-rolling-deploy-playbook.md) |
| 查所有环境变量 | [ENV.md](./ENV.md) |
| CI 失败分流 | [RUNBOOK §8 CI/Failure Triage](./RUNBOOK.md#8-ci--failure-triage) |

### 🏗️ 理解架构 / “为什么这么设计”

| 我要… | 去哪 |
|-------|------|
| 系统全景 + 数据流 + 模块边界 | [CODEMAPS/architecture.md](./CODEMAPS/architecture.md) |
| 后端 26 模块 / 路由 / 分层 | [CODEMAPS/backend.md](./CODEMAPS/backend.md) |
| 前端（console + management + shared） | [CODEMAPS/frontend.md](./CODEMAPS/frontend.md) |
| 共享字体样式 / 字号 / 双前端接入方案 | [SHARED_TYPOGRAPHY_DESIGN.md](./SHARED_TYPOGRAPHY_DESIGN.md) |
| 数据库 schema / 迁移 / Redis 用途 | [CODEMAPS/data.md](./CODEMAPS/data.md) |
| 依赖与外部集成版本 | [CODEMAPS/dependencies.md](./CODEMAPS/dependencies.md) |
| 代码执行沙箱（D-form harness） | [CODEMAPS/sandbox.md](./CODEMAPS/sandbox.md) · [ADR-002](./adr/ADR-002-sandbox-hexagonal.md) |
| 某个架构决策的来龙去脉 | [adr/README.md](./adr/README.md)（ADR 索引表） |

### 🔐 安全 / 鉴权 / 隐私

| 我要… | 去哪 |
|-------|------|
| 日志留存 / PII / GDPR / 虚拟赛审计 | [PRIVACY.md](./PRIVACY.md) |
| 鉴权 / refresh token / seed 账号 / 网络暴露的不变量 | `CLAUDE.md` → **Security Invariants**（仓库根，权威） |
| 安全修复迁移 `V20260606130000` | `init-db/migrations/`（迁移源） |
| Contest 安全专项审查（鉴权/IDOR/secret） | [contest/_archive/SECURITY_REVIEW_2026-06-17.md](./contest/_archive/SECURITY_REVIEW_2026-06-17.md)（历史 v1/v2 证据；CRIT-9/10 已在代码中用 UUID 修复） |

> ⚠️ `docs/SECURITY_REVIEW_2026-06-06.md` 与 `docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md`
> 已于 2026-06-06 合并入 `RUNBOOK.md` 与 `CLAUDE.md`（commit `9ce22f921`），
> 上述链接为现行权威位置。仍引用旧文件名的页面见 [DOCS-SPEC §已知历史引用](./DOCS-SPEC.md)。

### 🐛 运行时诊断

| 我要… | 去哪 |
|-------|------|
| Arthas watch/trace/stack 真实样本 + 降级路径 | [arthas-mcp-usage.md](./arthas-mcp-usage.md) |
| JVM CPU/线程/类加载问题 | RUNBOOK §5 · `cpu-high` / `spring-context` 等 skill |
| 阻塞型 MCP 命令 30s 超时怎么办 | [arthas-mcp-usage.md §降级路径](./arthas-mcp-usage.md) · `CLAUDE.md` Arthas 段 |

### 🤝 贡献 / 规范

| 我要… | 去哪 |
|-------|------|
| 搭建开发环境 + 可用命令清单 | [CONTRIBUTING.md](./CONTRIBUTING.md) |
| 代码风格 / commit 格式 / PR checklist | [CONTRIBUTING.md §Code Style](./CONTRIBUTING.md#code-style) |
| 前后端测试范围差异 | [CONTRIBUTING.md](./CONTRIBUTING.md) “Test scope difference” |
| **文档怎么写 / 放哪 / 命名 / 归档** | [DOCS-SPEC.md](./DOCS-SPEC.md) |

---

## 3. 文档清单（每篇一句话用途）

### 根级运维参考（手工维护）

| 文档 | 用途 | 关联 |
|------|------|------|
| [RUNBOOK.md](./RUNBOOK.md) | 部署 / 健康 / 常见故障 / 回滚 / flag 切换 / CI 分流的操作手册 | 依赖 [ENV](./ENV.md)、[CODEMAPS](./CODEMAPS/)、[ADR-005](./adr/ADR-005-rolling-deploy-playbook.md) |
| [ENV.md](./ENV.md) | 所有环境变量（Docker/Flyway/PM2/Spring/Vite）+ dev 账号 + 生产边界 | 被 RUNBOOK、CONTRIBUTING 引用 |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | 开发环境、脚本清单、代码风格、PR checklist | 含脚本自动生成段 |
| [PRIVACY.md](./PRIVACY.md) | 日志留存、PII、GDPR、虚拟赛审计轨迹 | 关联 contest R10 |
| [arthas-mcp-usage.md](./arthas-mcp-usage.md) | Arthas watch/trace/stack 实战样本、超时根因、降级路径 | 关联 `CLAUDE.md` Arthas 段 |
| [SHARED_TYPOGRAPHY_DESIGN.md](./SHARED_TYPOGRAPHY_DESIGN.md) | `shared/theme` 字体 token、语义字号、console/management 密度接入与迁移方案 | 关联 [CODEMAPS/frontend.md](./CODEMAPS/frontend.md) |
| [DOCS-SPEC.md](./DOCS-SPEC.md) | 本目录的文档规范（命名/头/链接/归档/更新触发） | 本文件 §6 |

### CODEMAPS（**自动生成，勿手改**）

| 地图 | 覆盖 |
|------|------|
| [architecture.md](./CODEMAPS/architecture.md) | 系统全景、服务边界、数据流、迁移策略、shared 库、近期主题 |
| [backend.md](./CODEMAPS/backend.md) | 26 模块、43 控制器、路由、分层、WS 端点 |
| [frontend.md](./CODEMAPS/frontend.md) | console(:9002) + management(:9003) + shared 包 |
| [data.md](./CODEMAPS/data.md) | MySQL schema、Flyway、关键关系、Redis |
| [dependencies.md](./CODEMAPS/dependencies.md) | 后端/前端/shared/infra 依赖版本、CI/CD |
| [sandbox.md](./CODEMAPS/sandbox.md) | D-form 沙箱 harness、资源限制、verdict 映射、排障 |

> CODEMAPS 头部 `<!-- Generated: DATE -->` 为生成时间戳。改代码后用
> `ecc:update-codemaps` skill 重生成，**不要手工编辑**（会被覆盖）。

### ADR（[完整索引见 adr/README.md](./adr/README.md)）

| ADR | 主题 | 状态 |
|-----|------|------|
| [ADR-001](./adr/ADR-001-verdict-status-codec.md) | Verdict/SubmissionStatus 强类型 codec | Accepted |
| [ADR-002](./adr/ADR-002-sandbox-hexagonal.md) | Sandbox Hexagonal Port + LanguageProfile | Accepted |
| [ADR-003](./adr/ADR-003-queue-outbox-fencing.md) | Queue + Outbox + Generation Fence + Lease | Accepted |
| [ADR-004](./adr/ADR-004-notification-intents.md) | NotificationIntent + per-channel 投影 | Accepted |
| [ADR-005](./adr/ADR-005-rolling-deploy-playbook.md) | 滚动部署 Playbook（10 milestone + flag） | Proposed |
| [ADR-006](./adr/ADR-006-contest-scoring-engine-activation.md) | Contest 评分引擎激活 | Accepted |
| [ADR-007](./adr/ADR-007-virtual-contest-lifecycle-and-rating-isolation.md) | 虚拟竞赛生命周期 + 评级隔离 | Accepted |
| [ADR-008](./adr/ADR-008-websocket-auth-and-realtime-push.md) | WebSocket 鉴权 + realtime push | Accepted |
| [ADR-009](./adr/ADR-009-israted-gate-and-virtual-rating-isolation.md) | isRated gate + virtual-rating isolation | Accepted |
| [ADR-010](./adr/ADR-010-cancel-state-and-virtual-replay-boundary.md) | 状态机边界（CANCELLED/虚拟重放） | Accepted |
| [ADR-011](./adr/ADR-011-crit6-shadow-mode-evaluation.md) | CRIT-6 shadow 模式评估 | Accepted |

> ADR-000 为 meta/溯源；ADR-005a 是 ADR-005 的回滚演练子协议。编号不补缺、不复用。

### Contest 特性工作区（[完整地图见 contest/README.md](./contest/README.md)）

术语 → PRD/设计 → 实施计划（[EXECUTION_PLAN.md](./contest/EXECUTION_PLAN.md) R1–R10 累计）→ 审计（F-01 / F-22 / I18N_AUDIT_R10）→ 决策（REVIEW_V3）。历史证据（v1/v2 审查 + R6–R10 执行步骤）统一归档在 [contest/_archive/](./contest/_archive/)。

当前裁决：**模块 v4.2 完结**（详见 [contest/REVIEW_V3 §12](./contest/REVIEW_V3.md)）。

---

## 4. 按主题标签（Tag Map）

| Tag | 含义 | 文档 |
|-----|------|------|
| `#ops` | 部署/运维/排障 | RUNBOOK, ENV, arthas-mcp-usage, CONTRIBUTING |
| `#architecture` | 架构地图/结构 | CODEMAPS/* |
| `#frontend` | console / management / shared 前端设计与约定 | CODEMAPS/frontend, SHARED_TYPOGRAPHY_DESIGN |
| `#decision` | 不可逆/长期决策 | adr/* |
| `#feature-contest` | Contest 特性工作区 | contest/* |
| `#sandbox` | 代码执行沙箱 | CODEMAPS/sandbox, ADR-001, ADR-002 |
| `#judge-queue` | 评测投递/outbox/fencing | ADR-003, CODEMAPS/data, CODEMAPS/architecture |
| `#notification` | 通知投递 | ADR-004 |
| `#scoring` | 评分引擎 | ADR-006, contest/REVIEW_V3 |
| `#virtual-contest` | 虚拟竞赛 | ADR-007, ADR-009, ADR-010 |
| `#websocket` | WS 鉴权/实时 | ADR-008 |
| `#security` | 安全/鉴权/隐私 | PRIVACY, CLAUDE.md Security Invariants, contest/_archive/SECURITY_REVIEW |
| `#deploy` | 滚动部署/flag/回滚 | ADR-005, ADR-005a, RUNBOOK §10 |
| `#reference` | 长期参考 | ENV, CONTRIBUTING, PRIVACY |

---

## 5. 与仓库根文档的关系

避免重复，**按职责分工**：

| 文档 | 位置 | 职责 | 谁引用谁 |
|------|------|------|----------|
| `AGENTS.md` | 仓库根 | 仓库级权威指南：项目地图、工具链、启动流程、提交前自检 | 本目录被其引用为“专题文档” |
| `CLAUDE.md` | 仓库根 | AI 协作陷阱（字符集、Arthas、约定）+ Security Invariants + skill 索引 | 引用本目录各篇 |
| `.claude/rules/` | 仓库根 | 按 paths 触发的子规则（backend/frontend/database） | 与 CODEMAPS 互补 |
| `.cursor/rules/` | 仓库根 | Cursor IDE `.mdc` 规则 | — |
| `docs/`（本目录） | `docs/` | 人读的工程文档：架构、运维、决策、特性 | 被根文档引用 |

> 原则：**“怎么跑 + 陷阱”归 CLAUDE.md/AGENTS.md；“为什么 + 架构 + 决策”归 docs/**。
> 安全不变量以 `CLAUDE.md` 为权威；本目录 PRIVACY.md 聚焦数据/日志留存。

---

## 6. 维护本索引

- 新增/重命名/移动任何文档 → 同步更新本文件 §3 与对应 area README。
- 本文件**手工维护**，不在任何自动生成流水线里。
- 文档写作规范见 [DOCS-SPEC.md](./DOCS-SPEC.md)。
