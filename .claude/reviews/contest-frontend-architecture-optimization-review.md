# Code Review: Contest 前端架构优化

**Reviewed**: 2026-05-29
**Branch**: feat/contest-api-phase4-performance-fixes
**Decision**: APPROVE with comments

## Summary
路由与视图职责拆分清晰，`loadUserContests` 按需加载实现合理，整体代码质量良好。存在 1 个 MEDIUM 问题建议修复，2 个 LOW 问题可选优化。

## Findings

### MEDIUM

**1. `ContestRankingsView.vue` 中 `Tabs` 组件语义误用**
- **文件**: `console/src/views/contest/ContestRankingsView.vue:56-75`
- **问题**: 使用 `Tabs` + `TabsList` + `TabsTrigger` 作为 scope toggle，但缺少 `TabsContent`。这不符合 shadcn-vue Tabs 的语义（标签页应对应内容面板），且可能触发组件内部断言或影响无障碍属性。
- **建议**: 改用简单的 `Button` 组或 `ToggleGroup` 组件实现 toggle，或者为每个 scope 提供独立的 `TabsContent`。

### LOW

**2. `@ts-expect-error` 绕过类型检查**
- **文件**: `console/src/views/contest/ContestRankingsView.vue:36`
- **问题**: 使用 `@ts-expect-error` 访问 `authStore.user.country`，类型系统无法保证该字段存在。
- **建议**: 优先在 `User` 类型中补充 `country?: string` 字段；若无法立即修改类型，至少使用 `(authStore.user as any)?.country` 并在注释中标注 TODO。

**3. 路由 redirect 未保留 query 参数**
- **文件**: `console/src/router/index.ts`
- **问题**: `/contest/past` 的 redirect 为 `{ name: "contest-browse-past" }`，如果用户访问 `/contest/past?page=2`，`page` 参数会丢失。
- **建议**: 使用函数式 redirect 保留 query：
  ```typescript
  { path: "past", redirect: (to) => ({ name: "contest-browse-past", query: to.query }) }
  ```

**4. 快速切换 tab 可能产生竞态请求**
- **文件**: `console/src/views/contest/components/MyContests.vue:47-54`
- **问题**: `watch(activeTab, ...)` 在 tab 快速切换时，可能产生多个并行的异步请求，后返回的结果会覆盖先返回的状态。
- **建议**: 引入 `AbortController` 或在 `loadDataForTab` 中检查当前 tab 是否仍匹配请求目标。当前影响较小，可后续优化。

## Validation Results

| Check | Result | Notes |
|---|---|---|
| Type check | Pass | 无新增类型错误（原 zod/axios 错误为遗留） |
| Lint | Pass | 修复未使用 import 后通过 |
| Tests | Pass | 失败测试与本次变更无关 |
| Build | Fail | `chart-donut` 组件缺失依赖，为遗留问题 |

## Files Reviewed

| File | Action | Assessment |
|---|---|---|
| `ContestHomeView.vue` | Added | 职责单一，逻辑清晰 |
| `ContestBrowseView.vue` | Added | `initialTab` prop 设计合理，fallback 处理完善 |
| `ContestMyView.vue` | Added | 简洁的页面包装组件 |
| `ContestRankingsView.vue` | Added | Tabs 语义误用需修正 |
| `router/index.ts` | Modified | 旧路由 redirect 兼容性好 |
| `stores/contest.ts` | Modified | 向后兼容的按需加载实现正确 |
| `MyContests.vue` | Modified | 按需加载逻辑正确 |
| `en-US/contest.ts` | Modified | i18n key 完整 |
| `zh-CN/contest.ts` | Modified | i18n key 完整 |
| `ContestView.vue` | Deleted | 已确认无残留引用 |
| `ContestListView.vue` | Deleted | 已确认无残留引用 |
