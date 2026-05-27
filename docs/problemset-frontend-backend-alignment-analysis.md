# ProblemSet 前后端颗粒度与逻辑对齐分析报告

> 生成日期: 2026-05-27 | 分析范围: `http://localhost:9002/problemset`、`/problemset/list/:id`、后端 `/problems`、`/problem-lists/*` | 数据来源: 源码审查 + `localhost:9001` 实际 API 响应

---

## 1. 总结

`/problemset` 当前不是前后端同构筛选页面，而是“后端只返回第一页 20 条，前端再在这 20 条里本地筛选”的页面。后端 `/problems` 已具备分页、难度、状态、搜索、标签、排序、发布状态等 DTO/Service 能力，但 Controller 只暴露了 `page/pageSize/difficulty/status/search`；前端只发送 `category/search/userId`，并且 `search` 只在专门的 `searchProblems()` 里使用，页面主流程没有发送。结果是分类、标签、难度、会员、状态筛选大多只在前端本地执行，且受字段形态不匹配影响，部分筛选实际不可用。

运行态验证：

- `GET http://localhost:9001/problems` 返回 `pageSize=20,total=40`。
- `GET http://localhost:9001/problems?category=algorithms` 返回仍是同一批结果；后端 Controller 不接收 `category`。
- `GET http://localhost:9001/problems?tag=数组&pageSize=5` 返回仍包含非数组题；后端 Controller 不接收 `tag`。
- `GET http://localhost:9001/problems?difficulty=Easy&pageSize=5` 能正确按难度过滤。
- `GET http://localhost:9001/problem-lists/overview` 未登录返回 `40100 Unauthorized`，但 ProblemListController 内部写了未登录兜底逻辑，说明安全层与 Controller 语义不一致。

---

## 2. 关键链路

### 2.1 题库页路由与页面

- 前端路由: `console/src/router/index.ts:98`
  - `/problemset`
  - `/problemset/:category`
  - `/problemset/list/:id`
- 页面: `console/src/views/problem-set/ProblemSetView.vue:12`
  - 从 route params 读取 `category`
  - 传给 `ProblemExplorer :initial-category="category"`
- 组件: `console/src/components/problem/ProblemExplorer.vue:17`
  - 主要逻辑委托给 `useProblemExplorer(props)`

### 2.2 主数据请求

前端 `useProblemExplorer`:

- `console/src/components/problem/composables/useProblemExplorer.ts:57`
  - 调用 `fetchProblems(userId, { category: selectedCategory.value })`
  - 没有发送 `page/pageSize`
  - 没有发送页面搜索框的 `searchQuery`
  - 没有发送 `difficulty/status/tag/isPremium`

前端 API:

- `console/src/api/problem.ts:68`
  - 发送 `userId/category/search`
  - 默认期望后端返回 `{ items: unknown[] }`

后端 Controller:

- `backend-spring/src/main/java/com/ulticode/modules/problem/controller/ProblemController.java:49`
  - 只接收 `page/pageSize/difficulty/status/search`
  - 不接收 `userId/category/tag/sortBy/sortOrder/isPublished/publishStatus/limit`

后端 Service:

- `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:89`
  - 默认 `page=1,pageSize=20`
  - pageSize 上限 100
  - `buildProblemQueryWrapper()` 支持 `difficulty/status/tag/search/sortBy/sortOrder/publishStatus/isPublished`

结论：前端页面发出的参数与后端 Controller 暴露的参数不在同一粒度；Service 已经比 Controller 更细，但前端没有利用。

---

## 3. 严重问题

### 3.1 `/problemset` 只拿第一页 20 条，后续筛选基于局部数据

**证据**

- 后端默认分页: `ProblemServiceImpl.java:91-95`
- 前端主请求不带 `pageSize`: `console/src/api/problem.ts:68-80`
- 本地“加载更多”只是增加本地 slice 数量: `useProblemExplorer.ts:128-137`、`:275-278`

**影响**

当前数据库有 40 条题，但页面初始只拿 20 条。前端的搜索、标签、难度、状态、分类、随机选择都只基于这 20 条做计算。用户以为是在全题库筛选，实际是在第一页筛选。

**建议**

短期：`fetchProblems` 至少传 `pageSize=100`，让当前规模下页面展示全量。

中期：把 `useProblemExplorer` 改为服务端分页/筛选：

- `page`
- `pageSize`
- `search`
- `difficulty`
- `status`
- `tag`
- `category`
- `isPremium`
- `sortBy/sortOrder`

后端返回分页元信息，前端 `loadMore` 请求下一页，而不是本地 slice。

### 3.2 分类筛选前后端完全不闭环

**证据**

- 前端分类常量: `console/src/constants/problem-categories.ts:3`
  - `algorithms/database/shell/concurrency`
- 前端发送 `category`: `console/src/api/problem.ts:74`
- 后端 Controller 不接收 `category`: `ProblemController.java:49-59`
- 前端本地分类判断使用 `categoryConfig.name`: `useProblemExplorer.ts:114-120`
  - `categoryConfig.name` 是 i18n key，如 `sidebar.problem.algorithms`
  - 后端 tags 是中文标签对象，如 `{ id: "数组", label: "数组" }`

**运行态验证**

`GET /problems?category=algorithms` 与 `GET /problems` 返回相同数据。

**影响**

URL `/problemset/algorithms` 看起来是分类页，实际后端无分类过滤；前端本地匹配也因 i18n key 与 tag label 不一致，基本无法命中。

**建议**

明确分类模型：

- 如果分类只是 tag 聚合，前端常量应包含实际 tag slug/label 列表，例如 `algorithms -> ["数组", "哈希表", ...]`，后端支持 `category` 到 tag 集合映射。
- 如果分类是独立字段，给 `problems` 表或关联表增加 category 维度，并在 `ProblemQueryDTO`/Controller/Mapper 中暴露。

### 3.3 tags 字段形态不匹配，导致标签筛选与分类筛选失效

**证据**

- 后端 `ProblemVO.tags` 是 `List<ProblemTagVO>`: `ProblemVO.java:163`、`:233`
- 后端组装 tags 为对象数组: `ProblemServiceImpl.java:778-792`
- 前端 `Problem.tags` 类型是 `string[]`: `console/src/types/problem.ts:8`
- 前端 mapper 只把 `tagRelations` 转字符串；如果收到 `tags`，直接强转成 `string[]`: `console/src/api/problem.ts:60-64`
- 实际响应 `tags` 是对象数组：`[{ "id": "链表", "label": "链表" }, ...]`

**影响**

`selectedTags.some((tag) => p.tags.includes(tag))` 对对象数组不会命中字符串；`allTags` 会收集对象而不是字符串；`TagFilter` 期望字符串，可能显示 `[object Object]` 或行为异常。

**建议**

前端 `mapProblem` 应同时兼容两种形态：

```ts
tags: Array.isArray(p.tags)
  ? p.tags
      .map((tag) => typeof tag === "string" ? tag : tag?.label)
      .filter(Boolean)
  : []
```

更根本的做法是统一契约：后端列表接口返回 `tags: string[]`，详情/管理接口返回富对象；或者前端类型改为 `ProblemTag[]` 并全链路适配。

### 3.4 difficulty 大小写不匹配

**证据**

- 前端类型和筛选值是 `Easy/Medium/Hard`: `console/src/types/problem.ts:5`、`ProblemFilterPanel.vue:112-138`
- 后端 `toVO()` 强制转大写: `ProblemServiceImpl.java:702`
- 实际响应是 `EASY/MEDIUM/HARD`
- 前端本地筛选直接 `selectedDifficulty.includes(p.difficulty)`: `useProblemExplorer.ts:108-110`
- 前端颜色函数只匹配 `Easy/Medium/Hard`: `ProblemResultList.vue:24-35`

**影响**

难度筛选会失败；难度显示文本因为 `.toLowerCase()` 能显示，但颜色 class 不生效。

**建议**

统一枚举。推荐后端保持实体原值 `Easy/Medium/Hard` 或前端 mapper 归一化：

```ts
const difficulty = normalizeDifficulty(p.difficulty)
```

并让请求参数也走同一枚举。

### 3.5 `/problem-lists/overview` Controller 允许匿名，但安全层阻断匿名

**证据**

- `ProblemListController.getOverview()` 有匿名逻辑: `ProblemListController.java:30-36`
- `SecurityConfig.PUBLIC_ENDPOINTS` 未包含 `/problem-lists/**`: `SecurityConfig.java:40-76`
- 实际未登录请求返回 `40100 Unauthorized`
- `FeaturedBanners.vue` 当前用 `authStore.isAuthenticated` 避免未登录请求: `FeaturedBanners.vue:183-193`

**影响**

Controller 的 `findAll(locale)` 分支在真实安全配置下不可达。公开精选题单、公开题单详情、分享链接匿名访问等产品语义与安全层不一致。

**建议**

如果公开题单应匿名可见：

- 放开 `GET /problem-lists/overview`
- 放开 `GET /problem-lists/{id}/overview`
- 继续保护 POST/PATCH/DELETE/save/fork/category/user-lists 等写操作和用户态操作

如果题单必须登录可见，则删除 Controller 匿名分支，并让前端明确登录态 UI。

---

## 4. 高优先级不对齐

### 4.1 `userId` 参数无效，用户题目状态不是按当前用户计算

前端 `fetchProblems()` 会发送 `userId`: `console/src/api/problem.ts:73`。后端 Controller 不接收，Service 也没有使用当前用户的提交记录计算 `solved/attempted/todo`。当前响应里的 `status` 来自 `problems.status` 字段，是全局字段，不是用户维度字段。

建议改为后端从认证上下文读取 userId，按 submissions 聚合状态；前端不要传 `userId` query。

### 4.2 后端支持 `tag/sortBy/sortOrder/publishStatus/limit`，Controller 未暴露

`ProblemQueryDTO` 有字段: `limit/tag/sortBy/sortOrder/isPublished/publishStatus`。`ProblemServiceImpl.buildProblemQueryWrapper()` 也处理了这些字段。但 `ProblemController.listProblems()` 手工构造 DTO 时只设置了 `page/pageSize/difficulty/status/search`。

建议让 Controller 直接接收 `@ModelAttribute ProblemQueryDTO query`，或补齐所有 RequestParam。

### 4.3 前端搜索框没有触发服务端搜索

`DataTableToolbar` 修改 `searchQuery`，但只是本地 computed filter。对 40 条数据中第二页的题目，搜索不到。

建议搜索框 debounce 后调用服务端 `/problems?search=...`，并重置页码。

### 4.4 premium 筛选只有前端本地逻辑，后端没有 query 参数

前端有 `showPremium`: `useProblemExplorer.ts:35`、`:111-112`。后端 DTO 没有 `isPremium` filter。当前数据若第一页没有某类 premium 状态，筛选结果会误导。

建议 `ProblemQueryDTO` 增加 `isPremium`，Controller/Service 支持。

### 4.5 `limit` 别名在 DTO 存在，但 Controller 丢失

`ProblemQueryDTO.getPageSize()` 支持 `limit` 作为别名: `ProblemQueryDTO.java:19-55`。但 Controller 没接收 `limit`，所以 `GET /problems?limit=5` 实际仍返回默认 20。

---

## 5. 题单详情页对齐情况

### 5.1 基础结构基本对齐

前端 `fetchProblemListOverview()` 将后端扁平 `ProblemListDetailVO` 映射为 `{ list, problems, stats, viewer, categories }`。这与 `ProblemListView.vue` 的消费方式一致。

相关文件：

- `console/src/api/problem-list.ts:171-215`
- `console/src/views/problem-list/ProblemListView.vue:56-80`
- `ProblemListController.java:39-46`
- `ProblemListServiceImpl.java:111-221`

### 5.2 列表内 problem 粒度过薄

后端 `ProblemInListVO` 只有 `id/slug/title/difficulty/status/sortOrder/addedAt`，没有 `acceptance_rate/is_premium/has_solution/tags`。但前端把这些 problems 交给通用 `ProblemExplorer`，该组件会显示通过率、会员锁、官方题解图标、标签筛选等。

结果：

- 通过率列显示 `-`
- 标签筛选没有数据
- premium/solution 标识缺失
- `ProblemListAnalytics` 若依赖完整题目信息，也只能基于薄数据计算

建议：题单详情的 problems 要么返回与 `/problems` 列表同粒度的 `ProblemVO`，外加 `sortOrder/addedAt`；要么为 `ProblemExplorer` 增加“题单详情模式”，隐藏缺失字段对应的 UI。

### 5.3 题单 stats 是占位逻辑

后端 stats 当前：

- `solvedCount=0`
- `attemptedCount=0`
- `todoCount=total`
- `progress=0.0`

见 `ProblemListServiceImpl.java:186-194`。

这与前端展示“练习进度/分析”的语义不一致。建议按当前用户 submissions 计算，匿名用户可返回 null 或全 todo，并在前端明确处理。

### 5.4 添加题目后前端本地追加的数据粒度不稳定

`handleAddProblem()` 添加成功后直接把搜索结果里的 `problem` append 到本地 `problems`: `useProblemListOperations.ts:219-225`。搜索结果来自 `/problems?search=...`，是完整 `ProblemVO` 映射；题单详情初始 problems 是薄 `ProblemInListVO`。同一数组可能混合两种粒度。

建议添加/删除后重新拉取 `fetchProblemListOverview()`，或后端 add 接口返回标准化的 `ProblemInListVO`。

---

## 6. 建议契约

### 6.1 `/problems` 查询契约

建议后端公开：

```text
GET /problems
  page: number = 1
  pageSize: number = 50, max 100
  search?: string
  difficulty?: EASY|MEDIUM|HARD
  status?: solved|attempted|todo
  tag?: string
  category?: algorithms|database|shell|concurrency
  isPremium?: boolean
  sortBy?: id|title|difficulty|acceptanceRate|createdAt
  sortOrder?: asc|desc
```

响应：

```json
{
  "items": [
    {
      "id": 40,
      "slug": "reverse-linked-list",
      "title": "反转链表",
      "difficulty": "EASY",
      "acceptanceRate": 74.5,
      "status": "todo",
      "isPremium": false,
      "hasSolution": true,
      "tags": ["链表", "递归"]
    }
  ],
  "page": 1,
  "pageSize": 50,
  "total": 40,
  "totalPages": 1
}
```

### 6.2 前端 `Problem` 模型

建议统一成 camelCase：

```ts
interface Problem {
  id: number
  slug: string
  title: string
  difficulty: "EASY" | "MEDIUM" | "HARD"
  acceptanceRate: number
  status: "solved" | "attempted" | "todo"
  isPremium: boolean
  hasSolution: boolean
  tags: string[]
}
```

保留 snake_case 只在 API mapper 内部兼容，不进入组件层。

---

## 7. 修复优先级

| 优先级 | 问题 | 修复建议 |
|---|---|---|
| CRITICAL | `/problemset` 只筛第一页 20 条 | 改服务端分页/筛选，或短期传 `pageSize=100` |
| CRITICAL | tags 对象数组被前端当 string[] | 修复 `mapProblem()`，统一 tags 契约 |
| CRITICAL | difficulty `EASY` vs `Easy` | 前端 mapper 或后端响应统一枚举 |
| HIGH | category 参数后端忽略、本地也不匹配 | 设计 category/tag 映射并接入 Controller/Service |
| HIGH | `/problem-lists/overview` 匿名逻辑被 SecurityConfig 阻断 | 明确公开策略，放开 GET 或删除匿名分支 |
| HIGH | userId 参数无效，状态非用户维度 | 后端按认证用户计算状态，前端移除 userId query |
| MEDIUM | Controller 未暴露 DTO 已支持字段 | 用 `@ModelAttribute ProblemQueryDTO` 或补齐 RequestParam |
| MEDIUM | 题单详情 problem 粒度过薄 | 返回完整 ProblemVO + sortOrder/addedAt |
| MEDIUM | 题单 stats 占位 | 按用户 submissions 计算 |
| LOW | 添加题目后本地数组混合粒度 | add/remove 后重新拉取详情 |

---

## 8. 验证方式

本次未修改业务代码，未运行测试。验证命令：

```bash
curl -sS 'http://localhost:9001/problems'
curl -sS 'http://localhost:9001/problems?category=algorithms'
curl -sS 'http://localhost:9001/problems?tag=数组&pageSize=5'
curl -sS 'http://localhost:9001/problems?difficulty=Easy&pageSize=5'
curl -sS 'http://localhost:9001/problem-lists/overview'
```

Playwright MCP 当前不可用，报错为本机 Chrome 未安装 Playwright Extension，因此未能采集浏览器网络面板；页面/API 行为由源码和后端 HTTP 响应交叉验证。
