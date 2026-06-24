# Architecture Docs

工程架构与设计决策文档。每篇对应一个跨模块 / 跨端的设计契约或迁移方案。

| 文档 | 状态 | 范围 |
|------|------|------|
| [sidebar-menu-unification](./sidebar-menu-unification.md) | ✅ Shipped (Stage 8/8) | 把 console 与 management 的 sidebar 视觉契约沉淀到 `shared/sidebar-menu` |

**Code reviews**:

| 评审 | 评审者 | 结论 |
|------|--------|------|
| [CR 2026-06-24 (opencode)](./sidebar-menu-unification.CR-2026-06-24.md) | opencode/deepseek-v4-flash-free | 待修（重点：测试基础设施就绪度） |
| [CR 2026-06-24 (codex)](./sidebar-menu-unification.CR-2026-06-24.codex.md) | MiniMax-M3 (Codex CLI, Default) | 🔴 Request changes（重点：`SidebarGroup` 命名冲突 / 验收条件 / 行数估算） |

约定：

- **状态标记**：`📋 Planned`（计划已落定，未开工）/ `🚧 In Progress` / `✅ Shipped` / `⚠️ Superseded`
- **每篇文档头部** 必须包含：背景、目标、不变量、阶段切分、验证手段、风险、关联 commit
- **改本目录后** 同步更新 `README.md` 的索引表
- **不要把临时 WIP / 一次性 diff 总结放进这里**——`wiki/concepts/` 才是知识沉淀位