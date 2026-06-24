# Architecture Docs

工程架构与设计决策文档。每篇对应一个跨模块 / 跨端的设计契约或迁移方案。

| 文档 | 状态 | 范围 |
|------|------|------|
| [sidebar-menu-unification](./sidebar-menu-unification.md) | ✅ Shipped (Stage 8/8) | 把 console 与 management 的 sidebar 视觉契约沉淀到 `shared/sidebar-menu` |

**Code reviews**（plan-level：spec 文档评审，落地前）：

| 评审 | 评审者 | 结论 |
|------|--------|------|
| [CR 2026-06-24 (opencode)](./sidebar-menu-unification.CR-2026-06-24.md) | opencode/deepseek-v4-flash-free | 待修（重点：测试基础设施就绪度） |
| [CR 2026-06-24 (codex)](./sidebar-menu-unification.CR-2026-06-24.codex.md) | MiniMax-M3 (Codex CLI, Default) | 🔴 Request changes（重点：`SidebarGroup` 命名冲突 / 验收条件 / 行数估算） |
| [review 2026-06-24 (glm)](./sidebar-menu-unification.review.md) | glm-5.2 (Claude Code) | 🔴 Request changes（重点：CSS 未被 import / vitest config 缺失 / `SidebarGroup` 命名重复 / `AppSidebar` 范围错判） |

**Code reviews**（code-level：实际 commit 评审，落地后）：

| 评审 | 评审者 | 结论 |
|------|--------|------|
| [CR 2026-06-24 (claude)](./sidebar-menu-unification.CR-2026-06-24.claude.md) | glm-5.2 (Claude Code) | 🔴 Request changes（重点：`SidebarGroupCollapsible` 保留同款 `:open` 反模式 / 测试系统性回避 router-link / `SidebarNavUser`+`SidebarIconButton` 死组件） |
| [CR 2026-06-24 (codex-code)](./sidebar-menu-unification.CR-2026-06-24.codex-code.md) | MiniMax-M3 (Codex CLI, Default) | 🟡 Approve with nits（重点：CSS `@import` 重复 / 6 份 spec router-link 零覆盖 / management 父项 class 重复契约） |

> code-level CR 与 plan-level CR 是**不同维度**的评审：plan 关注 spec 计划与代码现状脱节，code 关注实际 commit 质量。本 sidebar-menu 一例中 plan-level CR 的 BLOCK / HIGH 全部在 §10 Landed 落地阶段被吸收，code-level CR 补的是"实施后浮现、plan 阶段未预见"的盲区。claude (glm-5.2) 与 codex-code (MiniMax-M3) 在 HIGH-1 / HIGH-2 上**独立收敛**（`:open` 反模式 + router-link 零覆盖），claude 提出根因更深（vue-router stub 缺失），codex-code 给出具体 spec 补全路径——两份互补不否定。

约定：

- **状态标记**：`📋 Planned`（计划已落定，未开工）/ `🚧 In Progress` / `✅ Shipped` / `⚠️ Superseded`
- **每篇文档头部** 必须包含：背景、目标、不变量、阶段切分、验证手段、风险、关联 commit
- **改本目录后** 同步更新 `README.md` 的索引表
- **不要把临时 WIP / 一次性 diff 总结放进这里**——`wiki/concepts/` 才是知识沉淀位
