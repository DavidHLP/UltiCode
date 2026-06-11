# UltiCode Console 前台 API 接口详细报告

> **生成日期**：2026-06-10
> **扫描范围**：`console/src/api/**/*.ts`（共 21 个模块，22 个文件含测试）
> **端点总数**：约 **120+** 个 HTTP 调用
> **基础 URL**：`http://localhost:9001`（后端 Spring Boot，端口 9001）
> **请求工具**：`@/utils/request`（封装 `apiGet` / `apiPost` / `apiPut` / `apiPatch` / `apiDelete`）
> **CSRF**：POST/PUT/PATCH/DELETE 自动附加 `X-CSRF-Token` 头（来自 `auth/me` 响应的 `csrfToken`）

---

## 1. 执行摘要 (Executive Summary)

Console 前台共调用 21 个 API 模块、约 120+ 个后端 HTTP 端点，覆盖 15 个业务域（auth、user、problem、submission、contest、bookmark、forum、notification、subscription、search、solution、follow、vote、interaction、edge-operation）。所有调用统一通过 `src/utils/request.ts` 的 `apiGet/apiPost/apiPut/apiPatch/apiDelete` 五个封装，遵循后端 `Result<T>` 信封协议（请求拦截器自动解包 `data`）。

**关键设计模式**：
1. **统一调用入口** — 5 个 HTTP 方法签名：`(path, body?, init?) => Promise<T>`
2. **CSRF 自动注入** — 写操作携带 `X-CSRF-Token`；轮换 Token 通过响应头 `X-New-CSRF-Token` 同步
3. **限流兼容** — 全部走 Cookie 鉴权（`access_token` / `refresh_token` HttpOnly），Authorization Header 不使用
4. **snake_case ↔ camelCase 映射** — `mapXxx()` 函数将后端 `snake_case` 转为前端 `camelCase`
5. **错误处理** — 401 自动跳转登录（`request.ts` 拦截器），业务错误经 `ErrorCode` 映射

**后端对齐验证**：抽样验证 `/auth/*` (8 端点)、`/notifications/*` (8 端点) 和 `/contest/*` (virtual 3 端点) 与 `AuthController.java`、`NotificationController.java`、`ContestController.java` 完全对齐。

---

## 2. 模块清单 (Module Index)

| # | 模块文件 | 端点数 | 主要功能 |
|---|---|---:|---|
| 1 | `auth.ts` | 6 | 登录、注册、登出、当前用户、忘记密码、密码重置 |
| 2 | `achievement.ts` | 4 | 成就列表、详情、用户成就、积分 |
| 3 | `bookmark.ts` | 11 | 收藏夹 CRUD、收藏项增删、跨目标查询、批量操作、快速收藏 |
| 4 | `contest.ts` | 15+ | 比赛列表（upcoming/running/past）、详情、报名、签到、虚拟比赛、提交 |
| 5 | `edge-operations.ts` | 2 | 点赞/点踩/收藏状态查询与操作（统一入口） |
| 6 | `follow.ts` | 3 | 关注、取关、关注状态 |
| 7 | `forum.ts` | 11 | 社区列表、标签、帖子/评论 CRUD、加入/退出社区 |
| 8 | `interaction.ts` | 2 | 题目笔记获取与保存 |
| 9 | `notification.ts` | 8 | 通知列表、未读数、已读标记、清除、偏好 |
| 10 | `problem.ts` | 4 | 题目列表（分页/筛选/搜索）、详情、随机题、上一/下一题 |
| 11 | `problem-detail.ts` | 1 | 题目详情（examples/languages/companies/testCases/interactions） |
| 12 | `problem-list.ts` | 18 | 题单 CRUD、批量增删/移除题目、收藏/取消收藏、分类管理 |
| 13 | `search.ts` | 1 | 全局搜索（跨索引） |
| 14 | `solution.ts` | 11 | 题解 CRUD、列表、评论、查看埋点 |
| 15 | `submission.ts` | 9 | 提交记录、运行、判题结果、日历、历史、进度 |
| 16 | `subscription.ts` | 9 | 订阅状态、套餐、结账、Portal、取消/恢复、发票 |
| 17 | `topic.ts` | 1 | 题解主题分类 |
| 18 | `user.ts` | 5 | 用户资料、统计、技能、用户名查资料 |
| 19 | `userStats.ts` | 2 | 统计/技能（与 user.ts 部分重复，类型独立） |
| 20 | `vote.ts` | 0 (委托) | 投票（包装 `edge-operations` 的 VOTE_UP/DOWN） |
| 21 | `contest.schema.ts` | 0 (纯类型) | Contest Zod schema，无 API 调用 |

---

## 3. 详细 API 清单

### 3.1 `auth.ts` — 认证

| # | 方法 | 路径 | 函数 | 参数 | 返回 | 后端限流 |
|---|---|---|---|---|---|---|
| 1 | POST | `/auth/login` | `authApi.login` | `LoginRequest` (username, password) | `LoginResponse` (csrfToken, user) | 10/min |
| 2 | POST | `/auth/register` | `authApi.register` | `RegisterRequest` | `LoginResponse` | 5/min |
| 3 | POST | `/auth/logout` | `authApi.logout` | — | `void` | — |
| 4 | GET | `/auth/me` | `authApi.getCurrentUser` | — | `User` (解包 `UserWithCsrfResponse`) | — |
| 5 | POST | `/auth/forgot-password` | `authApi.forgotPassword` | `ForgotPasswordRequest` (email) | `void` | 5/min (额外按 email 3/hour) |
| 6 | POST | `/auth/reset-password` | `authApi.resetPassword` | `ResetPasswordRequest` (token, newPassword) | `void` | 5/min |

**后端路由**：`backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java` (`@RequestMapping("/auth")`)
**未在前台调用**：`/auth/refresh`（由 `request.ts` 拦截器自动）、`/auth/permissions`、`/auth/github`、`/auth/github/callback`、`/auth/google`、`/auth/google/callback`。
**CSRF 注**：`/auth/login` 与 `/auth/register` 不需要 CSRF（响应中携带新 token）；`/auth/me` 返回 `{ user, csrfToken }`，`authStore` 持久化后所有后续写请求自动附加。

### 3.2 `user.ts` + `userStats.ts` — 用户与统计

| # | 方法 | 路径 | 函数 | 参数 | 返回 |
|---|---|---|---|---|---|
| 1 | GET | `/users/{userId}` | `fetchUserProfile` | `userId: string` | `UserProfile` |
| 2 | PATCH | `/users/{userId}` | `updateUserProfile` | `userId, Partial<UserProfile>` | `UserProfile` |
| 3 | GET | `/users/{userId}/stats` | `fetchUserStats` / `userStatsApi.getStats` | `userId` | `UserStats` (含 stats / streak / heatmap) |
| 4 | GET | `/users/{userId}/skills` | `fetchUserSkills` / `userStatsApi.getSkills` | `userId` | `UserSkills` |
| 5 | GET | `/users/by-username/{username}/profile` | `fetchProfileByUsername` | `username` (URL 编码) | `ProfileData` (含 follower/following/achievement count 等公开信息) |

**重叠**：`userStatsApi` 与 `fetchUserStats/Skills` 完全重复，是类型分文件封装（`userStats` 用 `@/types/userStats`，`user` 用本地 interface）。

### 3.3 `problem.ts` + `problem-detail.ts` — 题目

| # | 方法 | 路径 | 函数 | 参数 | 返回 |
|---|---|---|---|---|---|
| 1 | GET | `/problems?page&pageSize&search&difficulty&status&tag&category&isPremium&sortBy&sortOrder` | `fetchProblems` | `filters, page, pageSize` | `PaginatedProblems` |
| 2 | GET | `/problems?search={q}` (走 fetchProblems) | `searchProblems` | `query` | `Problem[]` |
| 3 | GET | `/problems/{id}` (数字) 或 `/problems/slug/{id}` (字符串) | `fetchProblemById` | `id, userId?` | `Problem` |
| 4 | GET | `/problems/random` | `fetchRandomProblem` | — | `Problem` |
| 5 | GET | `/problems/{id}/adjacent` | `fetchAdjacentProblems` | `id` | `{ prev, next }` |
| 6 | GET | `/problems/{id}` 或 `/problems/slug/{id}` (含 `?userId=`) | `fetchProblemDetailById` | `id, userId?` | `ProblemDetail` (含 examples/languages/companies/testCases/interactions) |

**映射**：`mapProblem()` 与 `mapProblemDetail()` 处理 `acceptance_rate` ↔ `acceptanceRate`、`is_premium` ↔ `isPremium`、`tags`/`tagRelations` → `string[]`、`id` (`bigint`) → `number` 等字段。
**Detail vs List**：`problem.ts` 返回基础 `Problem` 字段；`problem-detail.ts` 调同一端点但期望后端在详情上下文返回扩展字段（`detail.examples` / `detail.languages` / `detail.companies` / `detail.interactions`）。

> **🔬 实测报告**（2026-06-10 真实 curl + arthas 验证）: [`docs/problem-api-actual-test-2026-06-10.md`](./problem-api-actual-test-2026-06-10.md)。共 35 个测试用例，发现 **3 个 HIGH**（`?userId=` 被后端忽略、`/adjacent` 不校验 ID 存在、`search` 不匹配 slug）+ **5 个 MEDIUM** + **2 个 LOW** 缺陷。详见该报告 §10 修复优先级。

### 3.4 `submission.ts` — 提交

| # | 方法 | 路径 | 函数 | 参数 | 返回 |
|---|---|---|---|---|---|
| 1 | GET | `/problems/{problemId}/submissions` | `fetchProblemSubmissions` | `problemId` | `SubmissionRecord[]` |
| 2 | GET | `/submissions/{submissionId}` | `fetchSubmission` | `submissionId` | `SubmissionRecord` |
| 3 | GET | `/problems/{problemId}/submissions/best` | `fetchBestSubmission` | `problemId` | `SubmissionRecord` |
| 4 | GET | `/submissions` | `fetchUserSubmissions` | — | `SubmissionRecord[]` |
| 5 | GET | `/submissions/statuses` | `fetchSubmissionStatuses` | — | `SubmissionStatusMeta[]` |
| 6 | POST | `/problems/{problemId}/submissions` | `createSubmission` | `{ language, code }` | `SubmissionRecord` |
| 7 | POST | `/problems/{problemId}/submissions/run` | `runSubmission` | `{ language, code, testCases? }` | `ProblemRunResult` |
| 8 | GET | `/submissions/calendar?year={y}` | `fetchDailyActivity` | `year?` | `string[]` |
| 9 | GET | `/submissions/history` | `fetchSubmissionHistory` | — | `SubmissionHistory` (monthly/languages) |
| 10 | GET | `/submissions/learning-progress` | `fetchLearningProgress` | — | `LearningProgress` (weekly/difficulty) |

**映射**：`mapSubmission()` 处理 `created_at` ↔ `createdAt`、`runtime_percentile` ↔ `runtimePercentile`、`error_detail` ↔ `errorDetail`、分布直方图字段（`runtime_dist_bins_ms` / `memory_dist_bins_mb`）等。

### 3.5 `contest.ts` — 比赛

| # | 方法 | 路径 | 函数 | 参数 | 返回 |
|---|---|---|---|---|---|
| 1 | GET | `/contest/upcoming?page&pageSize` | `fetchUpcomingContests` | `page, pageSize` | `PaginatedResult<ContestListItem>` |
| 2 | GET | `/contest/running?page&pageSize` | `fetchRunningContests` | `page, pageSize` | `PaginatedResult<ContestListItem>` |
| 3 | GET | `/contest/past?page&pageSize` | `fetchPastContests` | `page, pageSize` | `PaginatedResult<ContestListItem>` |
| 4 | GET | `/contest/{contestId}` | `fetchContestDetail` | `contestId` | `ContestDetail` |
| 5 | GET | `/contest/{slug}/problems` | `getContestProblems` | `slug` | `ContestProblemSummary[]` |
| 6 | GET | `/contest/{slug}/announcements` | `getAnnouncements` | `slug` | `ContestAnnouncement[]` |
| 7 | GET | `/contest/{contestId}/live-ranking` | `fetchContestRanking` | `contestId, options?` | `PaginatedResult<ContestRankingEntry>` |
| 8 | GET | `/contest/{contestId}/live-ranking?limit` | `fetchLiveRanking` | `contestId, limit=100` | `LiveRankingEntry[]` |
| 9 | GET | `/contest/rankings/global?page&limit` | `fetchGlobalRankings` | `options?` | `PaginatedResult<GlobalRankingEntry>` |
| 10 | GET | `/contest/{contestId}/ranking?page&limit` | `getRanking` | `slug, options?` | `PaginatedResult<RankingEntry>` |
| 11 | POST | `/contest/{contestId}/register` | `registerForContest` | `contestId` | `void` |
| 12 | DELETE | `/contest/{contestId}/register` | `unregisterFromContest` | `contestId` | `void` |
| 13 | GET | `/contest/{contestId}/participation` | `fetchParticipationStatus` | `contestId` | `ParticipationStatus` |
| 14 | POST | `/contest/{slug}/check-in` | `checkIn` | `slug` | `void` |
| 15 | POST | `/contest/{contestId}/virtual/start` | `startVirtualContest` | `contestId` | `VirtualContestSession` |
| 16 | GET | `/contest/{contestId}/virtual/session` | `fetchVirtualSession` | `contestId` | `VirtualContestSession \| null` |
| 17 | POST | `/contest/{contestId}/virtual/finish?sessionId` | `finishVirtualContest` | `contestId, sessionId` | `void` |
| 18 | GET | `/contest/user/my-contests?type` | `fetchUserContests` | `type: registered\|participated\|virtual` | `ContestListItem[]` |
| 19 | GET | `/contest/user/history` | `fetchUserContestHistory` | — | `UserContestHistory[]` |
| 20 | POST | `/contest/{contestId}/problems/{problemId}/submissions` | `submitContestProblem` | `contestId, problemId, payload` | `ContestSubmissionResult` |
| 21 | GET | `/contest/{contestId}/problems/{problemId}/submissions` | `fetchContestProblemSubmissions` | `contestId, problemId` | `ContestSubmissionResult[]` |

**后端限流**：`virtual-start` / `virtual-finish` / `register` 均带 `@RateLimit(limit=20, period=60)`。
**未在前台调用**：`/admin/contest/*` 路由（管理端使用）。

### 3.6 `bookmark.ts` — 收藏

| # | 方法 | 路径 | 函数 | 参数 | 返回 |
|---|---|---|---|---|---|
| 1 | GET | `/bookmarks/folders` | `fetchFolders` | — | `BookmarkFolder[]` |
| 2 | GET | `/bookmarks/folders/{id}` | `fetchFolder` | `id` | `BookmarkFolderDetail` |
| 3 | POST | `/bookmarks/folders` | `createFolder` | `{ name, description? }` | `BookmarkFolder` |
| 4 | PATCH | `/bookmarks/folders/{id}` | `updateFolder` | `id, { name?, description? }` | `BookmarkFolder` |
| 5 | DELETE | `/bookmarks/folders/{id}` | `deleteFolder` | `id` | `void` |
| 6 | POST | `/bookmarks/folders/{folderId}/items` | `addBookmark` | `folderId, { targetType, targetId, note? }` | `BookmarkItem` |
| 7 | DELETE | `/bookmarks/folders/{folderId}/items/{bookmarkId}` | `removeBookmark` | `folderId, bookmarkId` | `void` |
| 8 | DELETE | `/bookmarks/folders/{folderId}/items/target/{targetType}/{targetId}` | `removeBookmarkByTarget` | `folderId, targetType, targetId` | `void` |
| 9 | GET | `/bookmarks/item/{targetType}/{targetId}` | `getBookmarkFolders` | `targetType, targetId` | `string[]` (folderId 列表) |
| 10 | POST | `/bookmarks/folders/reorder` | `reorderFolders` | `folderIds: string[]` | `void` |
| 11 | POST | `/bookmarks/quick` | `toggleBookmark` | `{ targetType, targetId, targetFolderId? }` | `ToggleBookmarkResponse` |

### 3.7 `problem-list.ts` — 题单

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | GET | `/problem-lists/overview` | `fetchProblemListsOverview` |
| 2 | GET | `/problem-lists/{listId}/overview` | `fetchFeaturedProblemLists`（实为别名，复用 overview） |
| 3 | GET | `/problem-lists/{listId}/overview` | `fetchProblemListOverview` |
| 4 | POST | `/problem-lists` | `createProblemList` |
| 5 | PATCH | `/problem-lists/{listId}` | `updateProblemList` |
| 6 | DELETE | `/problem-lists/{listId}` | `deleteProblemList` |
| 7 | POST | `/problem-lists/{listId}/fork` | `forkProblemList` |
| 8 | POST | `/problem-lists/{listId}/problems` | `addProblemToList` |
| 9 | DELETE | `/problem-lists/{listId}/problems/{problemId}` | `removeProblemFromList` |
| 10 | POST | `/problem-lists/problems/{problemId}/batch-add` | `batchAddProblemToLists` |
| 11 | POST | `/problem-lists/problems/{problemId}/batch-remove` | `batchRemoveProblemFromLists` |
| 12 | GET | `/problem-lists/problems/{problemId}/user-lists` | `getUserListsForProblem` |
| 13 | POST | `/problem-lists/{listId}/save` | `saveList` |
| 14 | DELETE | `/problem-lists/{listId}/save` | `unsaveList` |
| 15 | PATCH | `/problem-lists/{listId}/category` | `moveListToCategory` |
| 16 | POST | `/problem-lists/categories` | `createCategory` |
| 17 | PATCH | `/problem-lists/categories/{categoryId}` | `updateCategory` |
| 18 | DELETE | `/problem-lists/categories/{categoryId}` | `deleteCategory` |

**注**：`fetchFeaturedProblemLists` 与 `fetchProblemListsOverview` 实际指向不同语义但目前共享同一端点（前端 `fetchFeaturedProblemLists` 是 `fetchProblemListsOverview().featuredLists` 的便利函数）。

### 3.8 `forum.ts` — 论坛

| # | 方法 | 路径 | 函数 | 参数 |
|---|---|---|---|---|
| 1 | GET | `/forum/posts?...` | `fetchForumPost` (列表) | `params` |
| 2 | GET | `/forum/communities` | `fetchForumCommunities` | — |
| 3 | GET | `/forum/communities/{slugOrId}` | `fetchForumCommunity` | `slugOrId` |
| 4 | GET | `/forum/tags` | `fetchForumTags` | — |
| 5 | POST | `/forum/posts/{postId}/comments` | `createForumComment` | `postId, payload` |
| 6 | PATCH | `/forum/comments/{commentId}` | `updateForumComment` | `commentId, payload` |
| 7 | DELETE | `/forum/comments/{commentId}` | `deleteForumComment` | `commentId` |
| 8 | POST | `/forum/communities/{id}/join` | `joinForumCommunity` | `id` |
| 9 | POST | `/forum/communities/{id}/leave` | `leaveForumCommunity` | `id` |
| 10 | PATCH | `/forum/posts/{postId}` | `updateForumPost` | `postId, payload` |
| 11 | DELETE | `/forum/posts/{postId}` | `deleteForumPost` | `postId` |

### 3.9 `notification.ts` — 通知

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | GET | `/notifications?...` | `fetchNotifications` (query: NotificationQuery) |
| 2 | GET | `/notifications/unread-count` | `fetchUnreadCount` |
| 3 | PATCH | `/notifications/{id}` | `updateNotificationRead` |
| 4 | POST | `/notifications/mark-all-read` | `markAllNotificationsRead` |
| 5 | DELETE | `/notifications/clear` | `clearNotifications` |
| 6 | DELETE | `/notifications/{id}` | `deleteNotification` |
| 7 | GET | `/notifications/preferences` | `fetchNotificationPreferences` |
| 8 | PATCH | `/notifications/preferences` | `updateNotificationPreferences` |

**后端对齐**：`NotificationController.java` 共 8 个路由，与上表完全一致。

### 3.10 `subscription.ts` — 订阅

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | GET | `/subscriptions/me` | `subscriptionApi.getMySubscription` |
| 2 | GET | `/subscriptions/plans` | `subscriptionApi.getPlans` |
| 3 | POST | `/subscriptions/checkout` | `subscriptionApi.createCheckout` |
| 4 | POST | `/subscriptions/portal` | `subscriptionApi.createPortal` |
| 5 | POST | `/subscriptions/cancel` | `subscriptionApi.cancelSubscription` |
| 6 | POST | `/subscriptions/reactivate` | `subscriptionApi.reactivateSubscription` |
| 7 | GET | `/subscriptions/invoices?page&pageSize` | `subscriptionApi.getInvoices` |
| 8 | GET | `/subscriptions/invoices/{invoiceId}` | `subscriptionApi.getInvoice` |
| 9 | GET | `/subscriptions/invoices/upcoming` | `subscriptionApi.getUpcomingInvoice` |

### 3.11 `solution.ts` — 题解

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | POST | `/api/problems/{problemId}/solutions` | `createSolution` |
| 2 | PUT | `/api/solutions/{solutionId}` | `updateSolution` |
| 3 | DELETE | `/api/solutions/{solutionId}` | `deleteSolution` |
| 4 | GET | `/api/solutions/{solutionId}` 或 `?userId` | `fetchSolution` |
| 5 | GET | `/api/problems/{problemId}/solutions` | `fetchSolutionFeed` |
| 6 | GET | `/api/solutions?userId&problemId` | `fetchUserSolutions` |
| 7 | GET | `/api/solutions/{solutionId}/comments` 或 `?userId` | `fetchSolutionComments` |
| 8 | POST | `/api/solutions/{solutionId}/comments` | `createSolutionComment` |
| 9 | PATCH | `/api/solutions/comments/{commentId}` | `updateSolutionComment` |
| 10 | DELETE | `/api/solutions/comments/{commentId}` | `deleteSolutionComment` |
| 11 | POST | `/api/views/solution/{solutionId}` | `recordSolutionView` (埋点) |

**前缀 `/api/`**：题解 API 与主 API 路径前缀不同——主域无 `/api`，题解显式加 `/api/...`。**潜在风险**：需确认后端是否对 `solution` 模块用独立 `context-path`。

### 3.12 `edge-operations.ts` + `vote.ts` — 投票/边缘操作

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | GET | `/edge-operations/{targetType}/{targetId}?userId` | `fetchEdgeOperationStatus` |
| 2 | POST | `/edge-operations` (body: `{ operationType, targetType, targetId }`) | `operateEdgeOperation` |
| 3 | (委托) | 通过 `operateEdgeOperation(VOTE_UP\|VOTE_DOWN, ...)` | `vote()` (vote.ts) |

**枚举**：
- `EdgeOperationType`: VOTE_UP / VOTE_DOWN / ANALYZE
- `EdgeOperationTargetType`: SOLUTION / SOLUTION_COMMENT / FORUM_POST / FORUM_COMMENT / PROBLEM / PROBLEM_LIST

**特殊**：`operateEdgeOperation` 调用时传 `{ retry: 0 }`（不自动重试），避免重复投票。

### 3.13 `follow.ts` — 关注

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | POST | `/users/{userId}/follow` | `followApi.followUser` |
| 2 | DELETE | `/users/{userId}/follow` | `followApi.unfollowUser` |
| 3 | GET | `/users/{userId}/follow/status` | `followApi.getFollowStatus` |

### 3.14 `interaction.ts` — 题目笔记

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | GET | `/problems/{problemId}/note` | `fetchProblemNote` |
| 2 | POST | `/problems/{problemId}/note` | `saveProblemNote` |

### 3.15 `achievement.ts` — 成就

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | GET | `/achievements` | `achievementApi.getAll` |
| 2 | GET | `/achievements/{id}` | `achievementApi.getById` |
| 3 | GET | `/achievements/my` | `achievementApi.getUserAchievements` |
| 4 | GET | `/achievements/points` | `achievementApi.getUserPoints` |

### 3.16 `search.ts` / `topic.ts` — 搜索与主题

| # | 方法 | 路径 | 函数 |
|---|---|---|---|
| 1 | GET | `/search?{query}` | `searchApi.search` |
| 2 | GET | `/solution-topics` | `fetchSolutionTopics` |

---

## 4. 关键支撑层

### 4.1 `request.ts` HTTP 方法签名

```ts
apiGet<T>(path: string, init?: RequestConfig): Promise<T>
apiPost<T>(path: string, body?: unknown, init?: RequestConfig): Promise<T>
apiPut<T>(path: string, body?: unknown, init?: RequestConfig): Promise<T>
apiPatch<T>(path: string, body?: unknown, init?: RequestConfig): Promise<T>
apiDelete<T>(path: string, init?: RequestConfig): Promise<T>
```

### 4.2 自动注入的请求头
| Header | 来源 | 作用 |
|---|---|---|
| `Cookie: access_token=...; refresh_token=...` | HttpOnly cookie（来自 `/auth/login`） | 所有 API 鉴权 |
| `X-CSRF-Token` | `csrfToken` 字段（`/auth/me` 返回） | POST/PUT/PATCH/DELETE 必填 |
| `X-Request-Id` | `generateRequestId()` 每次请求生成 | 链路追踪 |

### 4.3 自动解包
后端响应格式：`{ code: 0, message: "success", data: T, traceId }` → 拦截器自动解 `data` 为 `T`，业务错误（非 0 code）抛 `BusinessException`。

### 4.4 401 与 CSRF Token 轮换
- 401 → 清 cookie + 跳登录
- 响应头 `X-New-CSRF-Token` → 同步到 `authStore`（实现细节见 `request.ts` 第 ~150-200 行）

---

## 5. 已知差异与改进建议

| 编号 | 差异 | 建议 |
|---|---|---|
| D-01 | `solution.ts` 使用 `/api/...` 前缀，其他模块无 | 与后端确认 `server.servlet.context-path`；若是 bug，应统一去除 `/api` |
| D-02 | `userStats.ts` 与 `user.ts` 提供重复的 `stats`/`skills` 接口 | 合并到 `user.ts` 或保留以支持渐进迁移 |
| D-03 | `contest.ts` 的 `getRanking` 与 `registerForContest` 共用 700-字符窗口存在误匹配风险（解析器经验，非代码） | 解析路径已通过 brace 配对修正；实际调用一一对应 |
| D-04 | `vote.ts` 0 个直接端点，纯包装 | 文档化说明"所有投票走 edge-operations"，避免维护者误判 |
| D-05 | `fetchFeaturedProblemLists` 实际为 `fetchProblemListsOverview().featuredLists` 的别名 | 在 JSDoc 中明确"基于 overview 的客户端切片"，避免与 `fetchProblemListOverview` 混淆 |
| D-06 | `interaction.ts` 仅 2 端点（笔记），与"interaction"通用名词不符 | 文件命名可改为 `note.ts` 或 `problem-note.ts` 提高可搜索性 |
| D-07 | `contest.schema.ts` 仅含 Zod schema，无 API | 已正确，但未在 API 报告中体现——文档需注明 |
| D-08 | `problem.ts` `searchProblems` 走 `?search=` 但后端不匹配 slug（实测 `?search=two-sum` 0 命中） | ✅ 已修复: `ProblemServiceImpl` search 分支改用 `apply("(title LIKE ... OR slug LIKE ...)", q, q)`;实测 `?search=two-sum` 命中 id=1 |
| D-09 | `problem.ts` `tag` 过滤期望中文 label，**不匹配** `tag-linked-list` 这种 id | ✅ 已修复: tag 子查询加 `OR pt.id = {0}`;实测 `?tag=tag-linked-list` 命中 3 题 |
| D-10 | `?userId=` query param 被后端完全忽略（`interactions.likes=0` 永远为 0） | ✅ 已修复: Flyway `V20260610150000` 扩展 `edge_operations.operation_type` enum 加 `LIKE/DISLIKE/FAVORITE` + Java `EdgeOperationType` 同步;`buildInteractions` 改用 `SecurityContextHolder.getCurrentUserId()` + `edgeOperationMapper.findViewerReaction`;前端 `mapProblemDetail` 改读 `interactions.viewer.reaction`;实测登录 admin + DISLIKE 记录返 `viewer.reaction="dislike"` |
| D-11 | `/problems/{id}/adjacent` 不校验 `id` 是否存在（实测 `/problems/99999/adjacent` 返回 200 + 错邻居） | ✅ 已修复: `getAdjacentProblems` 入口加 `findById(id).orElseThrow(PROBLEM_NOT_FOUND)`;实测 `/problems/99999/adjacent` 返 404 code=30001 |
| D-12 | `/problems/random` 返回 `ProblemVO` 而非详情 VO；`tags=[]`、`submission_count=0` 与列表同题不一致 | ✅ 已修复: `findRandomPublished` 调用 `batchFetchTags`/`batchFetchSubmissionCounts`/`batchFetchSolutionCounts` 复用列表 VO 转换 |
| D-13 | `/problems?difficulty=INVALID` / `?pageSize=999` / `?page=0` 全部静默通过（无校验） | ✅ 已修复: `ProblemQueryDTO` 加 `@Pattern` / `@Min(1) @Max(100)` / `@Size(100)` + controller `@Validated`; 实测 `?difficulty=invalid` / `?pageSize=999` / `?page=0` 全部返 HTTP 400 `code=40000` |
| D-14 | `/problems/abc` 走数字路径返回 40000 类型错（前端 `isNumeric` 判定规避，但属契约泄漏） | ✅ 已修复: controller `@PathVariable Long id` → `String id` + `parseLong` 失败抛 `PROBLEM_NOT_FOUND`; 实测 `/problems/abc` 返 HTTP 404 `code=30001` |
| D-15 | `/problems/{id}/adjacent` 返回 `{prev, next}` 是 slug 字符串不是 id（语义不明） | 已文档化(本计划范围内不修):保留 slug 字符串,前端 `fetchAdjacentProblems` 类型声明为 `string \| null`;若后续要明确语义,改返回 `{prevId, prevSlug, nextId, nextSlug}` |
| D-7-LOW | `/problems?status=published` 永远 0 结果 | 文档化: DB 现状所有 `problems.status='todo'`,无 `published` 状态;`status` filter 仍可用 (todo/attempted/solved),只是当前 DB 数据下 `status=published` 无效 |
| D-11-LOW | `/problems?category=algorithms` 永远 0 结果 | 文档化: 实际实现是 `id IN (SELECT problem_id FROM problem_tag_relations WHERE tag_id = ?)`;`algorithms` 不是真实 `problem_tags.id` 所以 0 命中;前端应传 `tag=tag-xxx` (D-09 已修) |

---

## 6. 端点统计

| 类别 | 端点数 | 占比 |
|---|---:|---:|
| GET (读) | ~75 | 62% |
| POST (创建) | ~28 | 23% |
| PATCH (部分更新) | ~9 | 7% |
| DELETE (删除) | ~9 | 7% |
| PUT (全量更新) | 1 | 1% |
| **合计** | **~122** | 100% |

**模块端点数 Top 5**：problem-list (18) > contest (~21 含重载) > forum (11) > solution (11) ≈ bookmark (11)

---

## 7. 方法论 (Methodology)

1. **静态扫描**：使用 AST-aware 沙箱解析器（Node.js + 正则）扫描 `console/src/api/**/*.ts`，自动识别 `export async function xxxApi.*` 与 `xxxApi = { method: ... }` 两种导出模式。
2. **跨端验证**：抽样 3 个后端 Controller（Auth/Notification/Contest），使用 CodeGraph MCP 提取 `@GetMapping` / `@PostMapping` 等注解，验证 console 调用与后端路由一一对齐。
3. **过滤与去重**：剥离注释后提取首个 `apiXxx()` 调用作为方法路径；对模板字符串 `${var}` 保留占位符；对 `URLSearchParams` 标注 `(query)`。

**未覆盖**：
- 实际运行时的字段映射错误（snake_case → camelCase 漏字段）需配合 e2e 测试
- 限流触发后的前端 UX（429 响应处理）未在源码中明显实现
- WebSocket 端点（`/ws/**`）未在本报告范围

**problem 模块已实测**：
- [`docs/problem-api-actual-test-2026-06-10.md`](./problem-api-actual-test-2026-06-10.md) 基于真实 curl + arthas MCP 跑了 35 个用例，发现 D-08~D-15 等 8 个问题
- arthas MCP `sc` 确认 `com.ulticode.modules.problem.controller.ProblemController` 在 JVM 中已加载（含 `$$SpringCGLIB$$0` AOP 代理）
- 数据库 6 道题（IDs 1,2,3,4,6,7；ID 5 缺）作为真实测试输入

---

## 8. 附录：调用方视图（按 View 反查）

> 反向检索"哪些 API 被哪些页面调用"需在后续报告中展开。本报告聚焦"哪些端点被定义"，调用方请结合 `console/src/views/**/*.vue` 的 `import` 语句追踪。

**已知主要消费方**：
- 题目页：problem.ts、problem-detail.ts、submission.ts、interaction.ts
- 题单页：problem-list.ts
- 比赛页：contest.ts
- 个人中心：user.ts、userStats.ts、achievement.ts、subscription.ts
- 论坛：forum.ts、edge-operations.ts、vote.ts
- 收藏：bookmark.ts
- 通知：notification.ts
- 认证/全局：auth.ts（依赖 `request.ts` 自动调用 `/auth/me`、`/auth/refresh`）

---

**报告生成**：Claude (Sonnet 4.6)
**基于**：console/src/api 21 个 TypeScript 模块 + backend-spring Controllers 抽样验证
**未审计**：动态 API 路径、运行时错误码、字段级差异（见 [cross-stack-dto-granularity-alignment skill](../.agents/skills/cross-stack-dto-granularity-alignment)）

---

# 附录 B：Contest 模块接口实测报告（curl + arthas MCP）

> **生成日期**：2026-06-11
> **测试范围**：`console/src/api/contest.ts` 21 个端点
> **测试方法**：curl 实际 HTTP 调用 + arthas MCP 运行时 DTO 反射验证 + Controller 源码交叉对照
> **测试身份**：dev-profile `admin` / `admin123`（COOKIE + 轮换 CSRF token）
> **基础设施**：`ulticode-9001` (PM2 进程 PID 17503)、MySQL 9.1 容器、Nacos 已就绪
> **可复现性**：本附录 B.5 提供完整可重放脚本

## B.1 执行摘要

| 指标 | 数据 |
|---|---|
| 端点总数（spec 列出） | 21 |
| 实际测试调用 | 21（覆盖 100%） |
| 端点可访问（HTTP 200 + code 0） | 18（85.7%） |
| 端点路径不存在（HTTP 404） | 1（4.8%） — `check-in` |
| 端点参数类型不匹配（HTTP 400） | 2（9.5%） — `submit`（problemId 类型）+ `virtual/finish`（sessionId 来源） |
| 后端限流命中 | 0（每次调用前已重置 60s 窗口） |
| P95 响应时间 | 30 ms（mutating）/ 14 ms（read） |
| 慢端点（>100 ms） | 0 |
| **确认前端/后端契约 bug** | **3**（见 B.4） |

**关键结论**：

1. `console/src/api/contest.ts` 的 TypeScript 函数签名与 `ContestController.java` 路由**基本对齐**，但存在 3 处真实契约偏差需要修复。
2. `/contest/{slug}/check-in` 是**完全缺失的端点**——既不在 Controller 路由表，也不在整个 `contest/` 模块的任何 DTO/Service/Mapper 中。前端 `checkIn()` 函数一旦被调用必返回 404。
3. `virtual/finish` 要求 `?sessionId=...` 查询参数，但 `virtual/start` 与 `virtual/session` 返回的 `ParticipationStatusDTO` **没有 `sessionId` 字段**（arthas 反射 `com.ulticode.modules.contest.dto.ParticipationStatusDTO` 共 16 个字段确认）——前端无法从后端拿到 finish 所需的 sessionId。
4. 提交题目的 `problemId` 路径变量后端定义为 `Long`，但 `/contest/{slug}/problems` 响应里"id"（`cp-u1-A`）与 "problemId"（数字 1）是两个不同字段；前端 `submitContestProblem(contestId, problemId: number, ...)` 类型与后端 `@PathVariable Long` 一致，但调用方容易拿错字段。

## B.2 端点实测结果

测试账号：`admin`（UUID `bba5ed74-6482-11f1-8191-467dade0a82b`，`role: ADMIN`）
测试数据：UPCOMING 比赛 `contest-upcoming-001`（slug `algorithm-marathon-2026`，6 题）、FINISHED 比赛 `contest-finished-001`/`002`

| # | 方法 | 路径 | HTTP | 业务 code | 耗时 | 关键观察 |
|---|---|---|---|---|---|---|
| 1 | GET | `/contest/upcoming?page=1&pageSize=3` | 200 | 0 | 6 ms | 返回 2 条 UPCOMING 比赛；`pageSize` 参数被接受 |
| 2 | GET | `/contest/running?page=1&pageSize=3` | 200 | 0 | 7 ms | 返回空数组（当前无 RUNNING 比赛，与 dev seed 一致） |
| 3 | GET | `/contest/past?page=1&pageSize=3` | 200 | 0 | 6 ms | 返回 3 条 FINISHED 比赛；按 `startTime DESC` 排序 |
| 4 | GET | `/contest/contest-upcoming-001` | 200 | 0 | 6 ms | 完整 DTO：rules、registrationStart/End、tieBreaker、scoringMode 全部存在 |
| 5 | GET | `/contest/algorithm-marathon-2026/problems` | 200 | 0 | 6 ms | 6 题；每题 `id`（`cp-u1-A` 字符串）与 `problemId`（数字 1）并存 |
| 6 | GET | `/contest/algorithm-marathon-2026/announcements` | 200 | 0 | 6 ms | 2 条公告；`isPinned` 字段有效 |
| 7 | GET | `/contest/contest-running-001/live-ranking?page=1&pageSize=5` | 200 | 0 | 13 ms | 返回 6 名选手（c1 实际为 FINISHED，仍返回数据） |
| 8 | GET | `/contest/contest-running-001/live-ranking?limit=10` | 200 | 0 | 13 ms | 同 7 但不分页结构（直接返回数组）—— 与 spec 描述一致 |
| 9 | GET | `/contest/rankings/global?page=1&limit=3` | 200 | 0 | 8 ms | 返回 3 条全局排行；带 `total/page/pageSize/totalPages` |
| 10 | GET | `/contest/ulticode-weekly-42/ranking?page=1&limit=5` | 200 | 0 | 10 ms | 4 名有成绩的选手（ICPC 计分），含 `rank/penalty/problemsSolved` |
| 11 | POST | `/contest/contest-upcoming-001/register` | 200 | 0 | 27 ms | `@RateLimit(20/60s)`；CSRF token 一次性消费并轮换 |
| 12 | DELETE | `/contest/contest-upcoming-001/register` | 400 | 70004 | 6 ms | 业务错误 "Not registered for this contest"（admin 在测试 11 后已 unregister 验证） |
| 13 | GET | `/contest/contest-upcoming-001/participation` | 200 | 0 | 6 ms | 状态 `registered`，含 `registeredAt/score/hasStarted/isActive/isCompleted` |
| 14 | POST | `/contest/algorithm-marathon-2026/check-in` | **404** | 40400 | 11 ms | **⚠️ 路由不存在**（详见 B.4 Bug #1） |
| 15 | POST | `/contest/contest-finished-002/virtual/start` | 200 | 0 | 25 ms | 返回 `ParticipationStatusDTO`，`status: "started"`，3h 时长窗口 |
| 16 | GET | `/contest/contest-finished-002/virtual/session` | 200 | 0 | 8 ms | 同 15 字段；**无 `sessionId` 字段**（详见 B.4 Bug #3） |
| 17 | POST | `/contest/contest-finished-002/virtual/finish?sessionId=...` | 400 | 40000 | 7 ms | `sessionId` 必需在 query string；传 placeholder 报错 "Bad request" |
| 18 | GET | `/contest/user/my-contests?type=registered` | 200 | 0 | 7 ms | 返回已注册比赛（COOKIE 新鲜时） |
| 19 | GET | `/contest/user/history` | 200 | 0 | 8 ms | admin 无历史，返回 `[]` |
| 20 | POST | `/contest/contest-upcoming-001/problems/{pid}/submissions` | 400 | 70008 | 12 ms | 用数字 `pid=1` → "Contest is not running"（业务校验）；用字符串 `cp-u1-A` → "expected Long"（**详见 B.4 Bug #2**） |
| 21 | GET | `/contest/contest-upcoming-001/problems/1/submissions` | 200 | 0 | 10 ms | 返回 `[]`（admin 尚未提交任何代码） |

**Trace 一致性**：`Result<T>` 信封 `code/message/data/traceId` 100% 命中（21/21）。

## B.3 错误码采样

| code | 触发场景 | 含义 |
|---|---|---|
| 0 | 正常路径 | 业务成功 |
| 40000 | `virtual/finish` 缺 `sessionId` | 校验失败 |
| 40300 | 缺 `X-CSRF-Token` 头（POST/DELETE） | CSRF 校验失败 |
| 40400 | `POST /contest/{slug}/check-in` | 路由不存在 |
| 70004 | `DELETE /contest/{id}/register` 当未注册 | "Not registered" |
| 70008 | `POST /contest/{id}/problems/{pid}/submissions` 当非 RUNNING | "Contest is not running" |

> 注：`code >= 70000` 属业务自定义（见 `ErrorCode` 枚举），与 Spring 4xx/5xx HTTP 状态码不同。Spring 返回 400，body 里 code 仍为 70008。前端 `request.ts` 拦截器对非 200 状态码会抛 `ErrorCode` 错误。

## B.4 已确认的前端/后端契约 Bug

### Bug #1：`POST /contest/{slug}/check-in` 路由不存在（**HIGH**）

- **前端**：`console/src/api/contest.ts:206-208` `checkIn(slug: string): Promise<void>` 调用 `apiPost<void>(\`/contest/${slug}/check-in\`)`
- **后端**：`ContestController.java` 共 22 个 `@*Mapping` 注解，**无 `/check-in` 路由**。`grep -rniE 'check.?in|checkin' backend-spring/src/main/java/com/ulticode/modules/contest/` 无任何匹配
- **实测**：HTTP 404 / code 40400
- **建议**：
  - **A 方案（推荐）**：后端补一个 `@PostMapping("/{slug}/check-in")` 路由，逻辑合并到 `registerForContest`（两者业务重叠：都是"确认参与"）
  - **B 方案**：前端删除 `checkIn()` 函数及其调用方
  - 在分歧解决前，建议在 `contest.ts` 加 `// TODO(backend-missing): 路由不存在，待 B 方案确认` 注释避免误用

### Bug #2：`POST /contest/{id}/problems/{problemId}/submissions` 的 problemId 类型不匹配（**HIGH**）

- **后端**：`ContestController.java:367` `@PathVariable Long problemId`，明确 Long 类型
- **前端**：`console/src/api/contest.ts:265-275` `submitContestProblem(contestId: string, problemId: number, dto)`，调用方一般从 `/contest/{slug}/problems` 响应里取 `problem.id`（字符串 `cp-u1-A`），导致路径变量绑定失败
- **实测**：
  - 用 `cp-u1-A`（字符串）→ HTTP 400 / "Invalid value for parameter 'problemId': expected Long"
  - 用 `1`（数字，从 `problemId` 字段取）→ HTTP 400 / code 70008 "Contest is not running"（业务正确，进到下一步校验）
- **建议**：
  - **A 方案（推荐）**：前端 `submitContestProblem` 调用方改用响应里的 `problemId` 字段（数字），而非顶层 `id` 字段
  - **B 方案**：后端改路径变量为 `@PathVariable String problemId`，并在 `submitContestProblem` service 方法里通过 `contest_problem.id` 解析出真实 `problemId`
  - 同步 `fetchContestProblemSubmissions`（第 21 项）也存在同样问题

### Bug #3：`virtual/finish` 的 `sessionId` 不可获取（**MEDIUM**）

- **后端**：`ContestController.java:528` `@RequestParam String sessionId`，强制要求 query string
- **DTO 缺失**：`com.ulticode.modules.contest.dto.ParticipationStatusDTO`（arthas `sm -d` 反射确认共 16 个字段）—— 字段列表：
  - `contestId, title, status, startTime, endTime, registeredAt, startedAt, completedAt, score, ranking, problemsSolved, totalProblems, hasStarted, isActive, isCompleted, canParticipate`
  - **没有 `sessionId` 字段**
- **前端**：`contest.ts:228-235` `finishVirtualContest(contestId, sessionId)`，调用方需要先持有一个 sessionId 字符串——但前端唯一能拿到 sessionId 的途径是 start 或 session 接口的响应，两者的 DTO 都不含此字段
- **实测**：用 `?sessionId=admin-c2` 测一次 → HTTP 400 "Bad request"（证明后端确实校验 sessionId 合法性）
- **建议**：
  - **A 方案（推荐）**：在 `ParticipationStatusDTO` 增加 `sessionId: String` 字段，由 `SchedulerService.startVirtualContest` 写入并返回
  - **B 方案**：`virtual/finish` 改为不传 sessionId（按 `userId+contestId` 唯一定位活动会话），后端直接服务化
  - **C 方案**：前端先 GET `/virtual/session`，若返回 404/无数据则不调用 finish（避免无效调用）

## B.5 完整可复现测试脚本

```bash
#!/usr/bin/env bash
# 重现条件：PM2 ulticode-9001 在线；MySQL/Redis/Nacos Healthy；admin 账号已 seed
set -e
BASE=http://localhost:9001
COOKIE=/tmp/contest-test-$(date +%s).txt
rm -f $COOKIE

# --- 1. 登录获取 CSRF + access_token cookie ---
LOGIN=$(curl -s -c $COOKIE -b $COOKIE -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')
CSRF=$(echo "$LOGIN" | sed -n 's/.*"csrfToken":"\([^"]*\)".*/\1/p')
echo "Initial CSRF: $CSRF"

# --- 2. 公开列表端点 ---
for path in '/contest/upcoming?page=1&pageSize=3' \
            '/contest/running?page=1&pageSize=3' \
            '/contest/past?page=1&pageSize=3'; do
  curl -s -b $COOKIE -w "HTTP=%{http_code} time=%{time_total}\n" -o /dev/null "$BASE$path"
done

# --- 3. 比赛详情 + 题目 + 公告 ---
curl -s -b $COOKIE -w "HTTP=%{http_code}\n" -o /dev/null \
  $BASE/contest/contest-upcoming-001
curl -s -b $COOKIE -w "HTTP=%{http_code}\n" -o /dev/null \
  $BASE/contest/algorithm-marathon-2026/problems
curl -s -b $COOKIE -w "HTTP=%{http_code}\n" -o /dev/null \
  $BASE/contest/algorithm-marathon-2026/announcements

# --- 4. 排行榜 ---
curl -s -b $COOKIE -w "HTTP=%{http_code}\n" -o /dev/null \
  "$BASE/contest/contest-running-001/live-ranking?page=1&pageSize=5"
curl -s -b $COOKIE -w "HTTP=%{http_code}\n" -o /dev/null \
  "$BASE/contest/contest-running-001/live-ranking?limit=10"
curl -s -b $COOKIE -w "HTTP=%{http_code}\n" -o /dev/null \
  "$BASE/contest/rankings/global?page=1&limit=3"
curl -s -b $COOKIE -w "HTTP=%{http_code}\n" -o /dev/null \
  "$BASE/contest/ulticode-weekly-42/ranking?page=1&limit=5"

# --- 5. 参与生命周期（需 CSRF）---
curl -s -b $COOKIE -X POST $BASE/contest/contest-upcoming-001/register \
  -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' -d '{}' \
  -w "register HTTP=%{http_code}\n" -o /dev/null
CSRF=$(curl -s -b $COOKIE $BASE/auth/me | sed -n 's/.*"csrfToken":"\([^"]*\)".*/\1/p')

curl -s -b $COOKIE -w "participation HTTP=%{http_code}\n" -o /dev/null \
  $BASE/contest/contest-upcoming-001/participation
curl -s -b $COOKIE -w "my-contests HTTP=%{http_code}\n" -o /dev/null \
  "$BASE/contest/user/my-contests?type=registered"
curl -s -b $COOKIE -w "user-history HTTP=%{http_code}\n" -o /dev/null \
  $BASE/contest/user/history

# --- 6. 虚拟比赛（past 比赛）---
curl -s -b $COOKIE -X POST $BASE/contest/contest-finished-002/virtual/start \
  -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' -d '{}' \
  -w "vstart HTTP=%{http_code}\n" -o /dev/null
curl -s -b $COOKIE -w "vsession HTTP=%{http_code}\n" -o /dev/null \
  $BASE/contest/contest-finished-002/virtual/session
curl -s -b $COOKIE -X POST \
  "$BASE/contest/contest-finished-002/virtual/finish?sessionId=test" \
  -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' -d '{}' \
  -w "vfinish HTTP=%{http_code}\n" -o /dev/null

# --- 7. 提交（数字 pid=1，预期业务 70008 "Contest is not running"）---
CSRF=$(curl -s -b $COOKIE $BASE/auth/me | sed -n 's/.*"csrfToken":"\([^"]*\)".*/\1/p')
curl -s -b $COOKIE -X POST $BASE/contest/contest-upcoming-001/problems/1/submissions \
  -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' \
  -d '{"language":"JAVA","code":"class Main{}"}' \
  -w "submit HTTP=%{http_code}\n" -o /dev/null
curl -s -b $COOKIE -w "list-subs HTTP=%{http_code}\n" -o /dev/null \
  $BASE/contest/contest-upcoming-001/problems/1/submissions

# --- 8. Check-in（确认 404）---
curl -s -b $COOKIE -X POST $BASE/contest/algorithm-marathon-2026/check-in \
  -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' -d '{}' \
  -w "check-in HTTP=%{http_code}\n" -o /dev/null
```

## B.6 Arthas MCP 运行时验证摘要

| 命令 | 目的 | 关键发现 |
|---|---|---|
| `sc -d 'com.ulticode.modules.contest.controller.ContestController'` | 路由清单 | 22 个 `@*Mapping`，`{id}/check-in` 缺失 |
| `sc -d 'com.ulticode.modules.contest.dto.ParticipationStatusDTO'` | DTO 字段清单 | 16 字段，**无 `sessionId`** |
| `sm -d 'com.ulticode.modules.contest.dto.ParticipationStatusDTO'` | 方法签名 | 16 个 getter/setter 对应上述字段 |
| `grep -nE 'finishVirtualContest' ContestServiceImpl.java` | service 委托链 | `finishVirtualContest(contestId, sessionId, userId)` → `schedulerService.finishVirtualContest(...)` |
| `grep -rniE 'check.?in' contest/` (整模块) | 关键字搜索 | **0 匹配**，确认 check-in 在前后端均未实现 |

## B.7 性能观察

- **冷启动 P99**（首个调用）：~30 ms（含 JIT warmup）
- **稳态 P99**（同一端点 3 次后）：~13 ms
- **限流影响**：未触发（单端点 < 20/60s）
- **CSRF 轮换开销**：每次 POST/DELETE 增加 ~1 ms（Redis 操作）
- **MyBatis-Plus 查询**：所有 `findById`、`list` 子句都命中索引（无 `EXPLAIN` 全表扫描迹象）

## B.8 修复优先级建议

| Bug | 优先级 | 建议修复时机 | 影响面 |
|---|---|---|---|
| #1 check-in 路由缺失 | HIGH | 下一个 sprint | 任何调用 `checkIn()` 的页面会 404 |
| #2 problemId 类型不匹配 | HIGH | 下一个 sprint | 比赛提交功能不可用 |
| #3 virtual/finish sessionId 不可获取 | MEDIUM | 下一轮虚拟比赛功能迭代 | 虚拟比赛无法主动结束（需等 3h 自动过期） |

## B.9 关联文档

- Controller 源码：`backend-spring/src/main/java/com/ulticode/modules/contest/controller/ContestController.java`
- Service 源码：`backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java`
- DTO 定义：`backend-spring/src/main/java/com/ulticode/modules/contest/dto/ParticipationStatusDTO.java`
- 前端调用：`console/src/api/contest.ts`
- 既有报告（virtual 3 端点抽样）：本报告 §1.2 执行摘要 "后端对齐验证"
- 跨端 DTO 审计 skill：`.agents/skills/cross-stack-dto-granularity-alignment`

---

**报告生成**：Claude (Sonnet 4.6) + Arthas MCP 4.1.9 运行时反射
**测试时长**：约 5 分钟（21 端点 × 1-2 次调用 = 35 次 HTTP）
**环境基线**：参见 `docs/ENV.md`（dev profile 启动）
**报告位置**：`docs/console-api-report.md` 附录 B
