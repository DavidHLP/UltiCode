# Architecture Decision Records (ADR)

本目录记录 UltiCode 架构决策。每条决策以 `ADR-NNN-{kebab-case-title}.md` 命名, 按 Michael Nygard 的轻量 ADR 模板编写, 适配项目中文 + 表格风格。

## 索引

| ADR | 状态 | 主题 | 摘要 |
|-----|------|------|------|
| **[ADR-000](./ADR-000-hexagonal-grilling-session.md)** | Superseded → ADR-001/002/003/004/005 | Hexagonal 化设计访谈与对抗评审记录 | 记录 `/grill-me` 访谈过程 + Codex 对抗评审 5 条 finding + 永久拒绝清单 |
| **[ADR-001](./ADR-001-verdict-status-codec.md)** | Proposed | Verdict / SubmissionStatus codec 演化 | 在不动 DB 持久化值 + 前端 i18n key 的前提下, 把字符串 verdict 升级为强类型 enum + 三层 Codec |
| **[ADR-002](./ADR-002-sandbox-hexagonal.md)** | Proposed | Sandbox Hexagonal Port + LanguageProfile Strategy | `SandboxExecutor` port + Docker/InMemory 双 adapter; 5 个 LanguageProfile 集合注入 fail-fast |
| **[ADR-003](./ADR-003-queue-outbox-fencing.md)** | Proposed | Queue + Outbox + Generation Fence + JUDGING Lease | 任务投递走 Outbox 表 + 唯一约束去重; submission 加 generation/lease 列防旧 worker 覆盖与 JUDGING 卡死 |
| **[ADR-004](./ADR-004-notification-intents.md)** | Proposed | NotificationIntent + Per-Channel Projection | sealed `NotificationIntent` 替代泛型 envelope; 每 channel 独立 try-catch 失败隔离; channel-level preference 列入未来 ADR |
| **[ADR-005](./ADR-005-rolling-deploy-playbook.md)** | Proposed | 滚动部署 Playbook | 11 个独立可部署 milestone + feature flag + envelope versioning + canary gate + rollback drill |

## 编号规则

- **ADR-000** 保留给 "meta / supersede 溯源" 类记录
- **ADR-001+** 按提议时间顺序编号, **不补缺**, 不复用 (即使被 supersede 也保留编号)
- 文件名固定 `ADR-NNN-{kebab-case-title}.md` , NNN 三位补零
- 一旦 commit 进 main, **不可改名** (引用关系会失效)

## 状态流转

```
Proposed → Accepted → Implemented
        ↘ Rejected
        ↘ Superseded by ADR-XXX
```

- **Proposed**: 已写完, 待评审 (人评审 + `/codex:adversarial-review`)
- **Accepted**: 评审通过, 可执行
- **Implemented**: 实施完成 (代码已 merge + 测试通过)
- **Rejected**: 评审否决, 保留文件作为"为什么不做"的存证
- **Superseded**: 被新 ADR 取代, 头部声明 `Superseded by ADR-XXX` , 保留全文

## 评审流程

1. 在 worktree / 分支创建 ADR 文件, status = `Proposed`
2. commit + 提 PR
3. 跑 `/codex:adversarial-review --base main --background "..."` 对抗评审
4. 团队评审 PR (至少 1 reviewer approve)
5. 全部通过 → 修改 status = `Accepted` , 二次 commit, merge 进 main
6. 实施完成 → 修改 status = `Implemented` , commit
7. 后续被取代 → 头部加 `Superseded by ADR-XXX` , status = `Superseded`

## 何时写 ADR

参考 `.claude/rules/backend/07-java-design.md` #14 / #16, 下列变更**必须**走 ADR:

- 跨模块的端口 / 抽象引入 (Hexagonal port, 新 Strategy 接口)
- 持久化字段 / 表结构变更影响多模块
- 新引入框架 / 库 (Spring StateMachine, Vavr, 等)
- 部署架构变更 (新增 worker, 改部署单元)
- 安全 / 合规决策 (鉴权方式, 数据加密)

下列变更**不需要**走 ADR (走 PR review 即可):

- 单 bug fix
- 单模块内部重构, 不改对外接口
- 测试新增 / 调整
- 文档 / 注释修订
- 依赖小版本升级 (除非有 breaking change)

## References

- Michael Nygard, "Documenting Architecture Decisions" (2011) — ADR 起源
- Joel Parker Henderson, [adr-templates](https://github.com/joelparkerhenderson/architecture-decision-record) — 多种模板对比
- 项目规约: `.claude/rules/backend/07-java-design.md`
- 项目主文档: `CLAUDE.md`, `AGENTS.md`
