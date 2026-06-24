# Code Review: `sidebar-menu-unification.md`

> Reviewer: opencode/deepseek-v4-flash-free
> Date: 2026-06-24
> Reviewed file: `docs/architecture/sidebar-menu-unification.md`
> Review type: 结构 + 一致性 + 可行性

---

## ✅ 好的

- **划界清晰** — 不变量（不碰后端 / i18n / 视图组件 / `useSidebarLists`）明确，风险可控
- **提交粒度合理** — 7 个 commit 独立可工作，可 revert
- **Stage 5/6 替换目标行数具体** — 便于验证工作量
- **`SidebarParentItem` 的 `onParentClick` 设计** — 正确区分 console（链接+折叠）与 management（仅折叠）
- **wiki 页面位置正确** — `concepts/sidebar-menu.md` 符合 SCHEMA.md 分层

---

## ❌ 重要问题

### 1. `vitest.config.ts` 不存在（§4 前提错误）

```
计划原文: "shared 已有 vitest.config.ts"
实际:     shared/sidebar-menu/ 下没有 vitest.config.ts
```

`shared/auth-ui/`、`shared/auth-core/`、`shared/theme/` 都有，唯独 sidebar-menu 没有。Stage 4 需要**新增这个文件**，Stage 2/3 之前就要准备好。建议提升到 Stage 1。

### 2. `@vue/test-utils` 不在 devDependencies（§4 前提错误）

```
计划原文: "测试栈：vitest + @vue/test-utils（已就绪）"
实际:     package.json devDependencies 没有 @vue/test-utils
```

需要 `pnpm add -D @vue/test-utils`。Stage 4 不能直接开写 spec。

### 3. Stage 3 标题与表格数量矛盾

```
标题: "3 个新组件"
表格: 4 行 — SidebarGroup / SidebarParentItem / SidebarNavUser / SidebarIconButton
```

而且 SidebarGroup 在 Stage 2 已经说是从 SidebarGroupCollapsible 升级而来，Stage 3 又说新建。**SidebarGroup 到底归 Stage 2 还是 Stage 3？** 建议：

- Stage 2 只给现有 3 个组件加 props（不改名）
- Stage 3 新建 SidebarGroup（替换旧 SidebarGroupCollapsible）+ 其余 3 个新组件

或者把 SidebarGroup 从 Stage 3 表格去掉，标题改为 "4 个新组件"。

### 4. Stage 7 更新对象写错

```
计划原文: "wiki/SCHEMA.md 更新：在 entities / concepts 列表追加"
实际:     SCHEMA.md 是 wiki 约定文档，没有 entity 列表
         entity/concept 列表在 wiki/index.md
```

应改为 `wiki/index.md`。

---

## ⚠️ 次要问题

| 问题 | 建议 |
|------|------|
| tsconfig.json `exclude: ["**/__tests__/**"]` | vue-tsc type-check 不会检查测试文件。这是常见模式，但如果你希望测试文件的类型也被覆盖，需要调整 exclude。当前做法可以接受。 |
| `SidebarParentItem.onParentClick` 返回 `void` | 如果点击父项行需要阻止 navigation（management 场景），`() => void` 不够。建议 `onParentClick?: (e: MouseEvent) => void`，让调用方能 `e.preventDefault()`。 |
| `.uc-sidebar-sub-list` 的 `pl-2` | `pl-2`（0.5rem/8px）在 RTL 语言下不适用。建议用 `ps-2`（padding-inline-start）保持 i18n 兼容。 |
| Stage 8 用了 `git diff --check` 但没 `git diff` | 推荐先 `git diff` 确认改了什么，再 `git diff --check` 查空白。 |

---

## 总评

**整体设计扎实，边界清晰**。修复上述 4 个重要问题后即可执行。最大隐患是测试基础设施（vitest config + vue-test-utils）需要先备好，否则 Stage 4 会卡住。建议加一个 Stage 0.5（或者把 vitest config 归入 Stage 1）来补测试基础设施。
