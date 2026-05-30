# Plan: Contest 前端架构优化 (Phase 5)

## Summary
重构 Contest 模块前端路由与视图层级，消除 `ContestView.vue` 和 `ContestListView.vue` 的职责重叠，明确区分 Dashboard/Home、Browse、My Contests、Rankings 四大场景。同时将 `loadUserContests` 从一次性全量加载改为按需懒加载，并统一 global/local ranking 的展示入口。

## User Story
As a developer maintaining the UltiCode contest module,
I want clear, single-responsibility view components and on-demand data loading,
So that the codebase is easier to navigate, routes behave predictably, and unnecessary API calls are eliminated.

## Problem → Solution
当前 `/contest` 和 `/contest/past` 都映射到 `ContestListView.vue` 但后者无任何差异化处理；`ContestView.vue` 同时承担 Dashboard、My Contests、Rankings 三个职责，通过 `tab` prop 分支渲染，导致组件臃肿、路由语义不清。`MyContests` 进入页面即并行请求 3 组用户数据，浪费带宽。Global/Local ranking 共用同一路由和组件，无差异化参数传递。

→ 将视图拆分为独立的 `ContestHomeView`、`ContestBrowseView`、`ContestMyView`、`ContestRankingsView`，路由一一对应。Store 中拆分 `loadUserContests` 为按类型加载的 action，前端 tab 切换时触发。Rankings 页面内通过 scope toggle 区分 global/local。

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/contest-api-alignment-analysis.md`
- **PRD Phase**: Phase 5 — 前端架构优化
- **Estimated Files**: 12

---

## UX Design

### Before
```
/contest              -> ContestListView.vue  (Tabbed browse: ongoing/upcoming/finished)
/contest/past         -> ContestListView.vue  (同上，无任何差异化)
/contest/my           -> ContestView.vue       (tab="my")
/contest/global-ranking -> ContestView.vue     (tab="ranking")
/contest/local-ranking  -> ContestView.vue     (tab="ranking")
```
- `ContestView.vue` 无 tab 时渲染 Dashboard（Running + Upcoming + Ranking + Past）
- `ContestView.vue` 有 tab 时渲染对应分支
- `ContestListView.vue` 只负责 Tabbed Browse，但 `/contest/past` 没有自动选中 finished tab
- MyContests 进入即加载 registered + participated + virtual

### After
```
/contest              -> ContestHomeView.vue    (Dashboard)
/contest/browse       -> ContestBrowseView.vue  (Tabbed: ongoing/upcoming/finished)
/contest/browse/past  -> ContestBrowseView.vue  (自动选中 finished tab)
/contest/my          -> ContestMyView.vue      (My contests)
/contest/rankings    -> ContestRankingsView.vue (global/local toggle)
/contest/:slug       -> ContestDetailView.vue  (不变)
```
- 每个视图组件职责单一
- Browse 页进入 `/contest/browse/past` 自动选中 finished tab
- My Contests 默认只加载 registered，切换 tab 才加载对应数据
- Rankings 页通过 toggle 切换 global/local scope

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `/contest` | Tabbed browse list | Dashboard home | 原 Dashboard 从 `ContestView` 提取 |
| `/contest/past` | Tabbed browse list (无差异) | `/contest/browse/past` finished tab | 路由重定向或保留兼容 |
| `/contest/my` | `ContestView` tab 分支 | 独立 `ContestMyView` | 不再依赖 `tab` prop |
| `/contest/global-ranking` | `ContestView` tab 分支 | `/contest/rankings` | 组件内 toggle 切换 scope |
| `/contest/local-ranking` | `ContestView` tab 分支 | `/contest/rankings?scope=local` | 同上 |
| My Contests Tabs | 一次性加载全部 | 切换时按需加载 | 减少 2/3 的无用请求 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `console/src/router/index.ts` | 58-96 | Contest 路由定义，所有变更的起点 |
| P0 (critical) | `console/src/views/contest/ContestView.vue` | 全部 | 当前 Dashboard + tab 分支的实现 |
| P0 (critical) | `console/src/views/contest/ContestListView.vue` | 全部 | 当前 Tabbed Browse 的实现 |
| P1 (important) | `console/src/stores/contest.ts` | 253-284 | `loadUserContests` 和 user contest state |
| P1 (important) | `console/src/views/contest/components/MyContests.vue` | 全部 | My Contests UI 和 tab 结构 |
| P1 (important) | `console/src/views/contest/components/GlobalRanking.vue` | 全部 | Ranking UI 组件 |
| P2 (reference) | `console/src/api/contest.ts` | 248-266 | `fetchUserContests` API |
| P2 (reference) | `console/src/types/contest.ts` | 全部 | 类型定义 |

## External Documentation

No external research needed — feature uses established internal patterns (Vue 3 Composition API, Pinia, Vue Router, vue-i18n, shadcn-vue Tabs).

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: `console/src/router/index.ts:58-96`
Vue views use PascalCase + `View.vue` suffix. Route names use kebab-case.
```typescript
const contestRoutes: RouteRecordRaw = {
  path: "/contest",
  component: () => import("@/features/sider/AppLayout.vue"),
  children: [
    {
      path: "",
      name: "contest-list",
      component: () => import("@/views/contest/ContestListView.vue"),
    },
  ],
};
```

### STATE_MANAGEMENT
// SOURCE: `console/src/stores/contest.ts:27-98`
Pinia store with Composition API style: `ref` + `computed` + async actions.
```typescript
export const useContestStore = defineStore("contest", () => {
  const upcomingContests = ref<ContestListItem[]>([]);
  const loadingContests = ref(false);

  async function loadContests() {
    loadingContests.value = true;
    try {
      const [upcoming, running] = await Promise.all([
        fetchUpcomingContests(),
        fetchRunningContests(),
      ]);
      upcomingContests.value = upcoming.items;
      runningContests.value = running.items;
    } finally {
      loadingContests.value = false;
    }
  }
  // ...
});
```

### TAB_LAZY_LOADING
// SOURCE: `console/src/views/contest/components/MyContests.vue:71-82`
shadcn-vue Tabs with `TabsList` + `TabsContent`.
```vue
<Tabs v-else default-value="registered" class="w-full">
  <TabsList class="grid w-full max-w-md grid-cols-3">
    <TabsTrigger value="registered">...</TabsTrigger>
    <TabsTrigger value="participated">...</TabsTrigger>
    <TabsTrigger value="virtual">...</TabsTrigger>
  </TabsList>
  <TabsContent value="registered" class="space-y-4">...</TabsContent>
</Tabs>
```

### ERROR_HANDLING
// SOURCE: `console/src/stores/contest.ts:100-114`
Store actions catch errors, set `error` ref, then rethrow for component-level handling.
```typescript
async function loadPastContests(page: number = 1, pageSize: number = 10) {
  loadingContests.value = true;
  error.value = null;
  try {
    const result = await fetchPastContests(page, pageSize);
    pastContests.value = result.items;
    pastContestsTotal.value = result.total;
  } catch (err) {
    error.value = err instanceof Error ? err.message : "Failed to load past contests";
    throw err;
  } finally {
    loadingContests.value = false;
  }
}
```

### I18N_KEY_PATTERN
// SOURCE: `console/src/views/contest/ContestView.vue:88`
Translation keys use nested dot notation under module namespace.
```vue
<h1 class="text-3xl font-bold">{{ t("contest.list.mainTitle") }}</h1>
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `console/src/views/contest/ContestHomeView.vue` | CREATE | 提取原 `ContestView.vue` 的 Dashboard 部分（无 tab 分支） |
| `console/src/views/contest/ContestBrowseView.vue` | CREATE | 重命名并增强 `ContestListView.vue`，支持 `initialTab` prop |
| `console/src/views/contest/ContestMyView.vue` | CREATE | 独立 My Contests 页面，内嵌 `MyContests.vue` |
| `console/src/views/contest/ContestRankingsView.vue` | CREATE | 统一 Rankings 页面，支持 global/local toggle |
| `console/src/stores/contest.ts` | UPDATE | 拆分 `loadUserContests` 为按需加载 action |
| `console/src/views/contest/components/MyContests.vue` | UPDATE | 按需触发加载，tab 切换时调用对应 store action |
| `console/src/router/index.ts` | UPDATE | 重构 contest 路由映射 |
| `console/src/views/contest/ContestView.vue` | DELETE | 被拆分为多个独立视图 |
| `console/src/views/contest/ContestListView.vue` | DELETE | 被 `ContestBrowseView.vue` 替代 |
| `console/src/i18n/locales/en-US/contest.ts` | UPDATE | 补充新路由相关 i18n key |
| `console/src/i18n/locales/zh-CN/contest.ts` | UPDATE | 补充新路由相关 i18n key |
| `console/src/views/contest/components/GlobalRanking.vue` | UPDATE | 添加 scope toggle (global/local) |

## NOT Building

- 不修改后端 API（Phase 2/3/4 已完成）
- 不修改 `ContestDetailView.vue` 及其子组件
- 不修改 `ContestCard.vue`、`ContestTimer.vue` 等纯展示组件
- 不引入新的状态管理方案（保持 Pinia）
- 不修改路由守卫或认证逻辑
- 不修改 management 端的 contest 相关代码
- 不新增排行榜数据获取逻辑（global/local 均使用现有 `fetchGlobalRankings`，通过 `country` 参数区分）

---

## Step-by-Step Tasks

### Task 1: 创建 `ContestHomeView.vue`
- **ACTION**: 提取原 `ContestView.vue` 中 `!tab` 分支的 Dashboard 逻辑到独立文件
- **IMPLEMENT**: 
  - 复制 `ContestView.vue` 的 `<script setup>` 和 `<template>` 中 `v-if="!tab"` 的内容
  - 移除 `tab` prop 和所有 `v-else-if` 分支
  - 保留：RunningContests、UpcomingContests、GlobalRanking（左侧）、PastContests（右侧）
  - 保留所有 `onMounted` 加载逻辑（`loadContests`, `loadPastContests`, `loadGlobalRankings`）
  - 保留分页 watch
- **MIRROR**: `ContestView.vue` 的现有 Dashboard 实现
- **IMPORTS**: `vue`, `vue-router`, `pinia`, `lucide-vue-next`, `useContestStore`, `useI18n`, `Separator`, `UpcomingContests`, `RunningContests`, `GlobalRanking`, `PastContests`
- **GOTCHA**: 不要保留 `defineProps<{ tab?: string }>()`，此视图无 tab 分支
- **VALIDATE**: `pnpm type-check` 无错误；路由 `/contest` 能正常渲染 Dashboard

### Task 2: 创建 `ContestBrowseView.vue`
- **ACTION**: 基于 `ContestListView.vue` 创建支持 `initialTab` prop 的浏览视图
- **IMPLEMENT**:
  - 复制 `ContestListView.vue` 的全部内容
  - 添加 `defineProps<{ initialTab?: string }>()`
  - 在 `loadData()` 中，如果 `initialTab` 存在且合法，优先使用它设置 `activeTab`
  - 支持 `/contest/browse/past` 自动选中 `finished` tab
  - 保留原有 Tabs UI 和分页逻辑
- **MIRROR**: `ContestListView.vue` 的 Tabbed Browse 模式
- **IMPORTS**: 同 `ContestListView.vue`
- **GOTCHA**: `initialTab` 的值域应为 `"ongoing" | "upcoming" | "finished"`，非法值 fallback 到 `"ongoing"`
- **VALIDATE**: 访问 `/contest/browse/past` 时，`finished` tab 应被自动激活

### Task 3: 创建 `ContestMyView.vue`
- **ACTION**: 创建独立的 My Contests 页面视图
- **IMPLEMENT**:
  - 新文件，结构参考其他 View 组件
  - 引入并渲染 `MyContests.vue`
  - 添加页面标题和面包屑/返回按钮（如需要）
  - 在 `onMounted` 中调用 `contestStore.loadUserContests("registered")`（只加载默认 tab）
- **MIRROR**: `ContestView.vue` 的页面级包装结构（标题区 + Separator + 内容区）
- **IMPORTS**: `vue`, `useContestStore`, `MyContests`, `useI18n`, `Separator`, `Trophy`
- **GOTCHA**: 不要一次性加载全部三种用户比赛数据，只加载默认 tab（registered）
- **VALIDATE**: 进入页面只触发 1 次 API 请求（type=registered）

### Task 4: 创建 `ContestRankingsView.vue`
- **ACTION**: 创建统一的 Rankings 页面，支持 global/local 切换
- **IMPLEMENT**:
  - 页面级包装，包含标题 "Rankings"
  - 引入 `GlobalRanking.vue`
  - 添加 scope toggle（global / local）
  - global: 调用 `fetchGlobalRankings()` 不带 country
  - local: 调用 `fetchGlobalRankings({ country: userCountry })`，需要获取当前用户国家
  - 使用 `Tabs` 或简单的按钮组切换 scope
- **MIRROR**: `ContestView.vue` 中 ranking tab 的展示方式
- **IMPORTS**: `vue`, `useContestStore`, `GlobalRanking`, `useI18n`, `Tabs`, `TabsList`, `TabsTrigger`, `useAuthStore`（获取用户国家）
- **GOTCHA**: 用户国家信息可能不在 `authStore.user` 中，需确认字段名（可能是 `country` 或 `region`），若不存在则 local 选项禁用或 fallback
- **VALIDATE**: 切换 global/local 时触发对应 API 请求，UI 正确更新

### Task 5: 重构 `loadUserContests` 为按需加载
- **ACTION**: 在 store 中拆分全量加载为类型化的按需加载
- **IMPLEMENT**:
  - 修改 `loadUserContests` 签名：`async function loadUserContests(type?: "registered" | "participated" | "virtual")`
  - 如果传入 `type`，只请求该类型数据并更新对应 state
  - 如果不传 `type`，保持原有行为（同时加载三种，向后兼容）
  - 添加三个独立 state 的 loading flag，或复用现有 `loadingContests`
- **MIRROR**: `console/src/stores/contest.ts:257-273`
- **IMPORTS**: 无新增
- **GOTCHA**: 确保向后兼容 — 调用方不传参数时行为不变
- **VALIDATE**: 单测或手动验证：调用 `loadUserContests("registered")` 只触发 1 个 API 请求

### Task 6: 更新 `MyContests.vue` 为按需加载
- **ACTION**: 修改组件，tab 切换时按需加载对应数据
- **IMPLEMENT**:
  - `onMounted` 中只加载 `registered` 数据：`contestStore.loadUserContests("registered")`
  - 为 `Tabs` 添加 `@update:value` 或 watch `activeTab`（shadcn-vue Tabs v-model）
  - 当 tab 切换到 `participated` 或 `virtual` 时，如果对应数组为空，调用 `loadUserContests(type)`
  - 保留 `contestHistory` 的加载（在 `participated` tab 需要）
- **MIRROR**: `MyContests.vue` 现有的 Tabs 结构
- **IMPORTS**: 无新增
- **GOTCHA**: `contestHistory` 目前只在 participated tab 使用，但加载逻辑耦合在 `onMounted`。建议拆分为：仅在切换到 participated 且 `contestHistory` 为空时才加载
- **VALIDATE**: Network tab 验证：进入页面 2 个请求（registered + history），切换 virtual 时 1 个请求

### Task 7: 更新路由配置
- **ACTION**: 重构 `router/index.ts` 中的 `contestRoutes`
- **IMPLEMENT**:
  ```typescript
  const contestRoutes: RouteRecordRaw = {
    path: "/contest",
    component: () => import("@/features/sider/AppLayout.vue"),
    children: [
      {
        path: "",
        name: "contest-home",
        component: () => import("@/views/contest/ContestHomeView.vue"),
      },
      {
        path: "browse",
        name: "contest-browse",
        component: () => import("@/views/contest/ContestBrowseView.vue"),
      },
      {
        path: "browse/past",
        name: "contest-browse-past",
        component: () => import("@/views/contest/ContestBrowseView.vue"),
        props: { initialTab: "finished" },
      },
      {
        path: "my",
        name: "contest-my",
        component: () => import("@/views/contest/ContestMyView.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "rankings",
        name: "contest-rankings",
        component: () => import("@/views/contest/ContestRankingsView.vue"),
      },
      {
        path: ":slug",
        name: "contest-detail",
        component: () => import("@/views/contest/detailed/ContestDetailView.vue"),
      },
    ],
  };
  ```
- **MIRROR**: `router/index.ts:58-96`
- **IMPORTS**: 无新增
- **GOTCHA**: 
  - `/contest/past` 旧路由需添加 redirect：`
    { path: "past", redirect: { name: "contest-browse-past" } }
    `
  - `/contest/global-ranking` 和 `/contest/local-ranking` 旧路由需添加 redirect 到 `contest-rankings`
- **VALIDATE**: 所有旧路由访问后 URL 正确跳转，无 404

### Task 8: 更新 `GlobalRanking.vue` 支持 scope 切换
- **ACTION**: 在组件内添加 global/local toggle
- **IMPLEMENT**:
  - 添加 `scope` ref：`'global' | 'local'`
  - 在标题区域添加 toggle 按钮组或 TabsTrigger
  - `watch(scope, ...)` 时重新请求数据（通过 emit 或 prop callback 让父视图处理）
  - 由于数据获取在 store/view 层，组件只需 emit `update:scope` 事件
- **MIRROR**: `GlobalRanking.vue` 现有结构
- **IMPORTS**: 无新增
- **GOTCHA**: 保持组件的展示职责，不直接在组件内调用 API，通过事件通知父视图
- **VALIDATE**: 点击 toggle 后父视图正确切换 API 参数

### Task 9: 补充 i18n 翻译
- **ACTION**: 在 contest 翻译文件中补充新页面标题和文案
- **IMPLEMENT**:
  - `en-US/contest.ts` 和 `zh-CN/contest.ts` 中添加：
    - `contest.home.title`
    - `contest.browse.title`
    - `contest.my.title`
    - `contest.rankings.title`
    - `contest.rankings.global`
    - `contest.rankings.local`
- **MIRROR**: 现有 contest i18n 结构
- **IMPORTS**: 无
- **GOTCHA**: 确保 key 命名与现有风格一致（小写 camelCase，模块前缀）
- **VALIDATE**: 切换语言后新页面标题正确显示

### Task 10: 清理旧文件
- **ACTION**: 删除 `ContestView.vue` 和 `ContestListView.vue`
- **IMPLEMENT**:
  - 确认所有引用已迁移后删除旧文件
  - 检查是否有其他文件 import 这两个旧视图（如 `router/index.ts` 已更新则一般无）
- **MIRROR**: 无
- **IMPORTS**: 无
- **GOTCHA**: 先完成所有新文件创建和路由更新后再删除，避免编译中断
- **VALIDATE**: `pnpm type-check` 通过，无残留引用

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `ContestBrowseView` renders with initialTab | `props.initialTab = "finished"` | `activeTab === "finished"` | Yes — 非法 tab 值 fallback |
| `loadUserContests("registered")` | type="registered" | 只更新 `registeredContests` | No |
| `loadUserContests()` | 无参数 | 更新全部三个数组（向后兼容） | Yes — 兼容旧调用方 |
| `MyContests` tab switch | 切换到 "virtual" | 如果 `virtualContests` 为空则触发 API | Yes — 已有数据不重复加载 |
| Route `/contest/past` | 访问旧路由 | 重定向到 `/contest/browse/past` | Yes |
| `ContestRankingsView` scope toggle | local scope + 无用户国家 | 禁用 local 或优雅降级 | Yes |

### Edge Cases Checklist
- [ ] `initialTab` 传入非法值（如 `"invalid"`）→ fallback 到 `"ongoing"`
- [ ] 用户未登录访问 `/contest/my` → 路由守卫重定向到登录页
- [ ] `authStore.user.country` 不存在 → local ranking 按钮禁用或隐藏
- [ ] `loadUserContests` 不传参数 → 行为与重构前一致
- [ ] 快速切换 MyContests tab → 不触发重复请求（可用 loading flag 保护）
- [ ] 旧书签 `/contest/global-ranking` → 正确重定向到新路由

---

## Validation Commands

### Static Analysis
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console
pnpm type-check
```
EXPECT: Zero type errors

### Lint
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console
pnpm lint
```
EXPECT: No lint errors

### Unit Tests
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console
pnpm test
```
EXPECT: All tests pass

### Browser Validation
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/console
pnpm dev
```
EXPECT:
- [ ] `/contest` 显示 Dashboard（Running + Upcoming + Ranking + Past）
- [ ] `/contest/browse` 显示 Tabbed Browse，默认 ongoing
- [ ] `/contest/browse/past` 自动选中 finished tab
- [ ] `/contest/my` 显示 My Contests，默认加载 registered
- [ ] `/contest/rankings` 显示 Rankings，可切换 global/local
- [ ] `/contest/past` 重定向到 `/contest/browse/past`
- [ ] `/contest/global-ranking` 重定向到 `/contest/rankings`
- [ ] 切换 MyContests tab 时按需加载，Network tab 验证请求数量

---

## Acceptance Criteria
- [ ] `ContestHomeView.vue`、`ContestBrowseView.vue`、`ContestMyView.vue`、`ContestRankingsView.vue` 创建完成
- [ ] `ContestView.vue` 和 `ContestListView.vue` 已删除
- [ ] 路由映射更新，旧路由有 redirect
- [ ] `loadUserContests` 支持按需加载，向后兼容
- [ ] `MyContests.vue` 按需触发加载
- [ ] i18n 文案补充完整
- [ ] `pnpm type-check` 零错误
- [ ] `pnpm lint` 无错误
- [ ] `pnpm test` 通过
- [ ] 浏览器手动验证所有路由正常

## Completion Checklist
- [ ] 代码遵循 Vue 3 Composition API + Pinia 模式
- [ ] 错误处理匹配 store 中 try/catch + error ref 模式
- [ ] i18n key 遵循 `contest.{page}.{key}` 命名
- [ ] 无硬编码值
- [ ] 无未使用 import
- [ ] 向后兼容：`loadUserContests()` 无参调用行为不变
- [ ] 自包含 — 无需进一步提问即可实施

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 其他模块或测试仍引用旧视图文件名 | Medium | High | 全局 grep `ContestView` 和 `ContestListView` 确认无残留引用后再删除 |
| `authStore.user.country` 字段不存在 | Medium | Medium | 添加可选链 `?.` 和 fallback 逻辑，local 按钮条件渲染 |
| 路由重定向导致 SEO/外部链接失效 | Low | Low | 保留 redirect 而非直接删除路由，外部链接仍可访问 |
| MyContests 按需加载引入竞态条件 | Low | Medium | tab 切换时检查 loading flag，避免重复请求 |

## Notes
- `ContestView.vue` 当前虽然通过 `tab` prop 承担了多个职责，但它的 "Dashboard" 模式（无 tab）是 `/contest` 路由的主要用途。重命名为 `ContestHomeView.vue` 更准确地反映其职责。
- `ContestListView.vue` 实际上是一个 browse/discovery 页面，重命名为 `ContestBrowseView.vue` 更准确。
- `/contest/past` 路由的原始意图可能是直接展示已结束的比赛，但在当前实现中没有任何特殊处理。通过 `initialTab="finished"`  prop 可以满足这一需求。
- Global/Local ranking 的差异目前仅体现在 API 的 `country` 参数上，UI 展示逻辑完全相同，因此统一为单个视图 `ContestRankingsView.vue` 是合理的。
- 如果后续需要更复杂的 ranking 页面（如按时间段筛选、图表展示等），可以在 `ContestRankingsView.vue` 中扩展，无需再次拆分路由。
