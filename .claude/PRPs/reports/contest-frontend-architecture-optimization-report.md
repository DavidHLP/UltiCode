# Implementation Report: Contest 前端架构优化

## Summary
重构 Contest 模块前端路由与视图层级，将原本职责重叠的 `ContestView.vue` 和 `ContestListView.vue` 拆分为四个独立视图（Home、Browse、My、Rankings）。同时将 `loadUserContests` 改为按需加载，并统一 global/local ranking 入口。

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | 8/10 | 9/10 |
| Files Changed | 12 | 11 |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Create ContestHomeView.vue | [done] Complete | 提取原 Dashboard 部分 |
| 2 | Create ContestBrowseView.vue | [done] Complete | 支持 initialTab prop |
| 3 | Create ContestMyView.vue | [done] Complete | 独立 My Contests 页面 |
| 4 | Create ContestRankingsView.vue | [done] Complete | global/local toggle |
| 5 | Refactor loadUserContests | [done] Complete | 向后兼容 |
| 6 | Update MyContests.vue | [done] Complete | 按需加载 |
| 7 | Update router config | [done] Complete | 旧路由保留 redirect |
| 8 | Update GlobalRanking.vue | [done] Complete | Deviation — toggle 放在父视图 |
| 9 | Add i18n translations | [done] Complete | en-US + zh-CN |
| 10 | Clean up old files | [done] Complete | 删除 ContestView.vue 和 ContestListView.vue |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | [done] Pass | vue-tsc 无新增错误（原有 zod/axios 类型错误为遗留问题） |
| Lint | [done] Pass | MyContests.vue 修复后无新增 lint 错误 |
| Build | [blocked] Pre-existing | `chart-donut/DonutChart.vue` 缺失依赖导致构建失败，与本次变更无关 |
| Integration | N/A | 纯前端重构，无需集成测试 |
| Edge Cases | [done] Pass | initialTab 非法值 fallback、旧路由 redirect 均已验证 |

## Files Changed

| File | Action | Lines |
|---|---|---|
| `src/views/contest/ContestHomeView.vue` | CREATED | +152 |
| `src/views/contest/ContestBrowseView.vue` | CREATED | +393 |
| `src/views/contest/ContestMyView.vue` | CREATED | +46 |
| `src/views/contest/ContestRankingsView.vue` | CREATED | +111 |
| `src/stores/contest.ts` | UPDATED | ±20 |
| `src/views/contest/components/MyContests.vue` | UPDATED | ±35 |
| `src/router/index.ts` | UPDATED | ±30 |
| `src/i18n/locales/en-US/contest.ts` | UPDATED | +12 |
| `src/i18n/locales/zh-CN/contest.ts` | UPDATED | +12 |
| `src/views/contest/ContestView.vue` | DELETED | −153 |
| `src/views/contest/ContestListView.vue` | DELETED | −394 |

## Deviations from Plan

1. **GlobalRanking.vue scope toggle 位置**
   - **Plan**: 在 `GlobalRanking.vue` 组件内添加 toggle
   - **Actual**: 将 toggle 放在父视图 `ContestRankingsView.vue` 中
   - **Why**: `GlobalRanking.vue` 是纯展示组件，职责单一；scope 切换属于页面级交互，放在父视图更符合组件分层原则

2. **loadGlobalRankings 参数扩展**
   - **Plan** 未明确要求修改 `loadGlobalRankings`，但 `ContestRankingsView.vue` 需要传参
   - **Actual**: 同步扩展了 `loadGlobalRankings` 以接受 `{ page?, limit?, country? }`

## Issues Encountered

1. **authStore.user.country 字段不存在**
   - 用户类型中无 `country` 字段，local ranking toggle 默认禁用
   - 处理：使用 `@ts-expect-error` + `computed` 条件渲染，后续后端补充字段后自动生效

2. **MyContests.vue 未使用的 import**
   - 原文件导入的 `TrendingUp`、`TrendingDown` 未使用
   - 修复：移除未使用 import，lint 通过

3. **预存构建/测试失败**
   - `zod` 模块未安装、`axios` 类型版本冲突、`chart-donut` 组件缺失依赖
   - 这些均为代码库原有问题，与本次重构无关

## Tests Written

本次重构以组件重组和路由调整为主，未引入新的业务逻辑函数。现有测试套件中 `problem-detail.spec.ts` 的失败与本次变更无关。

## Next Steps
- [ ] 修复代码库预存构建问题（`zod` 依赖、`chart-donut` 组件）
- [ ] 后端补充 `User.country` 字段后，local ranking 功能将自动启用
- [ ] 运行 `/code-review` 审查变更
- [ ] 运行 `/prp-pr` 创建 Pull Request
