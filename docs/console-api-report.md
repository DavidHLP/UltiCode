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
