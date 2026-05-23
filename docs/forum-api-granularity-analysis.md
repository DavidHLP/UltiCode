# Forum 前后端 API 颗粒度对齐分析

> 分析日期: 2026-05-23
> 前端页面 (用户端): `http://localhost:9002/forum`
> 前端页面 (管理端): `http://localhost:9003/forum/posts`
> 前端路径 (用户端): `console/src/views/forum/`
> 前端路径 (管理端): `management/src/views/forum/`
> 后端路径 (用户端): `backend-spring/src/main/java/com/ulticode/modules/forum/controller/ForumController.java`
> 后端路径 (管理端): `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminForumController.java`

---

## 总体结论

**对齐状态: 存在 3 处高风险差异、5 处中低风险差异，需优先修复。**

- 用户端 18 个 API 端点中，16 个完全对齐，2 个前端缺失对应函数（join/leave community）。
- 管理端 12 个 API 端点中，10 个完全对齐，2 个后端缺失对应端点（flag/unflag）。
- DTO 字段覆盖率约 85%，主要差异集中在嵌套/扁平结构不一致、字段缺失、端点缺失。

---

## 一、用户端 API 端点对齐表

| 操作 | 前端 API 函数 | 前端路径 | 后端 Controller 方法 | 状态 |
|---|---|---|---|---|
| 获取全部帖子 | `fetchForumPosts` | `GET /forum/posts` | `ForumController.getAllPosts` | ✅ 对齐 |
| 获取单条帖子 | `fetchForumPost` | `GET /forum/posts/{id}` | `ForumController.getPostById` | ✅ 对齐 |
| 获取我的帖子 | `fetchMyForumPosts` | `GET /forum/me/posts` | `ForumController.getMyPosts` | ✅ 对齐 |
| 创建帖子 | `createForumPost` | `POST /forum/posts` | `ForumController.createPost` | ⚠️ DTO 字段差异，见差异 #4 |
| 更新帖子 | `updateForumPost` | `PATCH /forum/posts/{id}` | `ForumController.updatePost` | ⚠️ DTO 字段差异，见差异 #4 |
| 删除帖子 | `deleteForumPost` | `DELETE /forum/posts/{id}` | `ForumController.deletePost` | ✅ 对齐 |
| 获取帖子线程 | `fetchForumThread` | `GET /forum/posts/{id}/thread` | `ForumController.getPostThread` | ✅ 对齐 |
| 记录分享 | `recordForumShare` | `POST /forum/posts/{id}/share` | `ForumController.recordShare` | ✅ 对齐 |
| 记录浏览 | `recordForumView` | `POST /forum/posts/{id}/view` | `ForumController.recordView` | ✅ 对齐 |
| 创建评论 | `createForumComment` | `POST /forum/posts/{id}/comments` | `ForumController.createComment` | ✅ 对齐 |
| 更新评论 | `updateForumComment` | `PATCH /forum/comments/{id}` | `ForumController.updateComment` | ✅ 对齐 |
| 删除评论 | `deleteForumComment` | `DELETE /forum/comments/{id}` | `ForumController.deleteComment` | ✅ 对齐 |
| 获取社区列表 | `fetchForumCommunities` | `GET /forum/communities` | `ForumController.getAllCommunities` | ✅ 对齐 |
| 获取社区详情 | `fetchForumCommunity` | `GET /forum/communities/{slugOrId}` | `ForumController.getCommunity` | ✅ 对齐 |
| 获取社区帖子 | `fetchCommunityPosts` | `GET /forum/communities/{slug}/posts` | `ForumController.getCommunityPosts` | ✅ 对齐 |
| 获取标签列表 | `fetchForumTags` | `GET /forum/tags` | `ForumController.getAllTags` | ✅ 对齐 |
| 获取快速筛选 | `fetchForumQuickFilters` | `GET /forum/quick-filters` | `ForumController.getQuickFilters` | ✅ 对齐 |
| 加入社区 | **缺失** | — | `ForumController.joinCommunity` | ❌ 见差异 #5 |
| 离开社区 | **缺失** | — | `ForumController.leaveCommunity` | ❌ 见差异 #5 |

---

## 二、管理端 API 端点对齐表

| 操作 | 前端 API 函数 | 前端路径 | 后端 Controller 方法 | 状态 |
|---|---|---|---|---|
| 获取帖子列表 | `forumApi.getPosts` | `GET /admin/forum/posts` | `AdminForumController.getPosts` | ✅ 对齐 |
| 获取社区列表 | `forumApi.getCommunities` | `GET /admin/forum/communities` | `AdminForumController.getCommunities` | ✅ 对齐 |
| 删除帖子 | `forumApi.deletePost` | `DELETE /admin/forum/posts/{id}` | `AdminForumController.deletePost` | ✅ 对齐 |
| 置顶帖子 | `forumApi.pinPost` | `POST /admin/forum/posts/{id}/pin` | `AdminForumController.pinPost` | ✅ 对齐 |
| 取消置顶 | `forumApi.unpinPost` | `POST /admin/forum/posts/{id}/unpin` | `AdminForumController.unpinPost` | ✅ 对齐 |
| 锁定帖子 | `forumApi.lockPost` | `POST /admin/forum/posts/{id}/lock` | `AdminForumController.lockPost` | ✅ 对齐 |
| 解锁帖子 | `forumApi.unlockPost` | `POST /admin/forum/posts/{id}/unlock` | `AdminForumController.unlockPost` | ✅ 对齐 |
| 批量操作 | `forumApi.bulkAction` | `POST /admin/forum/bulk` | `AdminForumController.bulkAction` | ✅ 对齐 |
| 获取帖子详情 | `forumApi.getPostDetail` | `GET /admin/forum/posts/{id}` | `AdminForumController.getPost` | ⚠️ 字段结构差异，见差异 #2 |
| 获取审计历史 | `forumApi.getPostAuditHistory` | `GET /admin/forum/posts/{id}/audit` | `AdminForumController.getPostAuditHistory` | ✅ 对齐 |
| 标记帖子 | `forumApi.flagPost` | `POST /admin/forum/posts/{id}/flag` | **缺失** | ❌ 见差异 #3 |
| 取消标记 | `forumApi.unflagPost` | `POST /admin/forum/posts/{id}/unflag` | **缺失** | ❌ 见差异 #3 |

---

## 三、DTO 字段对齐详情

### 3.1 用户端帖子列表/详情 (`ForumPost` / `ForumPostVO`)

| 字段 | 前端类型 | 后端类型 | UI 使用 | 状态 |
|---|---|---|---|---|
| `id` | `string` | `String` | 是 | ✅ |
| `title` | `string` | `String` | 是 | ✅ |
| `excerpt` | `string` | `String` | 是 | ✅ |
| `tags` | `string[]` | `List<String>` | 是（搜索过滤） | ✅ |
| `isPinned` | `boolean` | `Boolean` | 是 | ✅ |
| `isLocked` | `boolean` | `Boolean` | 是 | ✅ |
| `isSaved` | `boolean` | `Boolean` | 是 | ✅ |
| `createdAt` | `string` | `LocalDateTime` | 是（相对时间） | ✅ |
| `voteState` | `string` | `String` | 是 | ✅ |
| `author` | `{ id, username, avatar }` | 扁平字段 `userId/authorUsername/authorAvatar` | 是 | ⚠️ 结构差异，见差异 #1 |
| `community` | `{ id, name, slug }` | 扁平字段 `communityId/communityName/communitySlug` | 是 | ⚠️ 结构差异，见差异 #1 |
| `flair` | `{ type, text }` | 扁平字段 `flairType/flairLabel` | 是 | ⚠️ 结构差异，见差异 #1 |
| `stats` | `object` | `Object` | 是（likes/dislikes/comments/views） | ⚠️ 类型宽泛 |
| `likes` | `number` | **无直接字段** | 是 | ⚠️ 前端假设 stats 内含，见差异 #6 |
| `dislikes` | `number` | **无直接字段** | 是 | ⚠️ 前端假设 stats 内含，见差异 #6 |
| `media` | `ForumPostMedia[]` | `Object` | 是 | ⚠️ 后端类型为 Object |
| `impressions` | `number` | `Integer` | 否 | ✅ |
| `communityId` | `string` | `String` | 内部使用 | ✅ |
| `permalink` | **无** | `String` | 否 | ⚠️ 后端冗余字段 |
| `isAuthor` | **无** | `Boolean` | 否 | ⚠️ 后端有，前端未使用 |
| `isMember` | **无** | `Boolean` | 否 | ⚠️ 后端有，前端未使用 |
| `isFlagged` | **无** | `Boolean` | 否 | ⚠️ 后端有，前端未使用（用户端不展示） |

### 3.2 用户端评论 (`ForumComment` / `ForumCommentVO`)

| 字段 | 前端类型 | 后端类型 | UI 使用 | 状态 |
|---|---|---|---|---|
| `id` | `string` | `String` | 是 | ✅ |
| `body` | `string` | `String` | 是 | ✅ |
| `createdAt` | `string` | `LocalDateTime` | 是 | ✅ |
| `parentId` | `string` | `String` | 是 | ✅ |
| `isPinned` | `boolean` | `Boolean` | 是 | ✅ |
| `isLocked` | `boolean` | `Boolean` | 是 | ✅ |
| `author` | `{ id, username, avatar }` | 扁平字段 `authorId/authorUsername/authorAvatar` | 是 | ⚠️ 结构差异，同差异 #1 |
| `replies` | `ForumComment[]` | `List<ForumCommentVO>` | 是 | ✅ |
| `upvotes` | `number` | **无** | 是 | ⚠️ 前端有，后端无，见差异 #7 |
| `likes` | `number` | **无** | 是 | ⚠️ 前端有，后端无，见差异 #7 |
| `dislikes` | `number` | **无** | 是 | ⚠️ 前端有，后端无，见差异 #7 |
| `userVote` | `0 \| 1 \| -1` | **无** | 是 | ⚠️ 前端有，后端无，见差异 #7 |
| `markdown` | **无** | `String` | 否 | ⚠️ 后端有，前端无 |
| `editedAt` | **无** | `LocalDateTime` | 否 | ⚠️ 后端有，前端无 |
| `isFlagged` | **无** | `Boolean` | 否 | ⚠️ 后端有，前端无 |
| `flaggedReason` | **无** | `String` | 否 | ⚠️ 后端有，前端无 |
| `replyCount` | **无** | `Long` | 否 | ⚠️ 后端有，前端无 |
| `isAuthor` | **无** | `Boolean` | 否 | ⚠️ 后端有，前端无 |

### 3.3 用户端社区 (`ForumCommunity` / `ForumCommunityVO`)

| 字段 | 前端类型 | 后端类型 | 状态 |
|---|---|---|---|
| `id` | `string` | `String` | ✅ |
| `name` | `string` | `String` | ✅ |
| `slug` | `string` | `String` | ✅ |
| `description` | `string` | `String` | ✅ |
| `members` | `number` | `Integer` | ✅ |
| `online` | `number` | `Integer` | ✅ |
| `icon` | `string` | `String` | ✅ |
| `color` | `string` | `String` | ✅ |
| `banner` | `string` | `String` | ✅ |
| `postsCount` | `number` | `Integer` | ✅ |
| `postsToday` | `number` | `Integer` | ✅ |
| `postsWeek` | `number` | `Integer` | ✅ |
| `isOfficial` | `boolean` | `Boolean` | ✅ |
| `isFeatured` | `boolean` | `Boolean` | ✅ |
| `sortOrder` | `number` | `Integer` | ✅ |
| `visibility` | `"PUBLIC" \| "RESTRICTED" \| "PRIVATE"` | `String` | ✅ |
| `isMember` | `boolean` | `Boolean` | ✅ |
| `userRole` | `"OWNER" \| "MODERATOR" \| "MEMBER"` | `String` | ✅ |
| `rules` | `ForumCommunityRule[]` | **无（在 DetailVO 中）** | ✅ 通过 DetailVO 获取 |
| `links` | `ForumCommunityLink[]` | **无（在 DetailVO 中）** | ✅ 通过 DetailVO 获取 |

### 3.4 管理端帖子列表/详情 (`ForumPost` / `AdminForumPostVO`)

| 字段 | 前端类型 | 后端类型 | UI 使用 | 状态 |
|---|---|---|---|---|
| `id` | `string` | `String` | 是 | ✅ |
| `title` | `string` | `String` | 是 | ✅ |
| `excerpt` | `string` | `String` | 是 | ✅ |
| `content` | `string` | `String` | 是（Drawer 内容预览） | ✅ |
| `userId` | `string` | `String` | 是（IDs 区块） | ✅ |
| `communityId` | `string` | `String` | 是（IDs 区块） | ✅ |
| `viewCount` | `number` | `Integer` | 是 | ✅ |
| `commentCount` | `number` | `Integer` | 是 | ✅ |
| `upvotes` | `number` | `Integer` | 是 | ✅ |
| `downvotes` | `number` | `Integer` | 是 | ✅ |
| `isPinned` | `boolean` | `Boolean` | 是 | ✅ |
| `isLocked` | `boolean` | `Boolean` | 是 | ✅ |
| `isFlagged` | `boolean` | `Boolean` | 是 | ✅ |
| `flaggedReason` | `string` | `String` | 是（条件展示） | ✅ |
| `flaggedAt` | `string` | `LocalDateTime` | 是（条件展示） | ✅ |
| `isDeleted` | `boolean` | `Boolean` | 是 | ✅ |
| `deletedAt` | `string` | `LocalDateTime` | 是（条件展示） | ✅ |
| `createdAt` | `string` | `LocalDateTime` | 是 | ✅ |
| `updatedAt` | `string` | `LocalDateTime` | 是 | ✅ |
| `author` | `{ id, username, avatar }` | 扁平 `username/avatar` | 是 | ⚠️ 结构差异，见差异 #2 |
| `community` | `{ id, name, slug }` | 扁平 `communityName/communitySlug` | 是 | ⚠️ 结构差异，见差异 #2 |

### 3.5 管理端查询参数 (`ForumPostQueryParams` / `AdminForumPostQueryDTO`)

| 字段 | 前端类型 | 后端类型 | 状态 |
|---|---|---|---|
| `page` | `number` | `Integer` | ✅ |
| `limit` | `number` | `Integer` | ✅ |
| `search` | `string` | `String` | ✅ |
| `communityId` | `string` | `String` | ✅ |
| `authorId` | `string` | `String` | ✅ |
| `isFlagged` | `boolean` | `Boolean` | ✅ |
| `isPinned` | `boolean` | `Boolean` | ✅ |
| `isLocked` | `boolean` | `Boolean` | ✅ |
| `sortBy` | `string` | `String` | ✅ |
| `sortOrder` | `'asc' \| 'desc'` | `String` | ✅ |
| `isDeleted` | **无** | `Boolean` | ⚠️ 前端缺少，见差异 #8 |

### 3.6 分页结构 (`PageResult<T>`)

前后端 `PageResult<T>` 字段完全一致：

```
items: T[]
total: number
page: number
pageSize: number
totalPages: number
```

**状态: ✅ 完全对齐**

---

## 四、颗粒度差异清单

### 差异 #1: 用户端嵌套对象 vs 后端扁平字段（中风险）

- **前端位置**: `console/src/types/forum.ts:151-187` (ForumPost), `console/src/api/forum.ts:12-33` (fetchForumPosts)
- **前端定义**: `author: { id, username, avatar }`, `community: { id, name, slug }`, `flair: { type, text }`
- **后端位置**: `ForumPostVO.java:39-53`
- **后端返回**: 扁平字段 `userId`, `authorUsername`, `authorAvatar`, `communityId`, `communityName`, `communitySlug`, `flairType`, `flairLabel`
- **影响**: 前端 API 层已手动转换（`fetchForumPosts`, `fetchCommunityPosts`, `fetchForumThread` 均有 map 逻辑），运行正常但类型定义与实际响应不匹配，维护成本高。
- **修复建议**: 方案 A：后端改为返回嵌套对象；方案 B：前端类型改为与后端一致的扁平结构，转换逻辑保留。推荐方案 B，因为后端 VO 被多个接口复用，改动影响面大。

### 差异 #2: 管理端嵌套对象 vs 后端扁平字段（中风险）

- **前端位置**: `management/src/api/admin/forum.ts:20-47` (ForumPost)
- **前端定义**: `author: { id, username, avatar }`, `community: { id, name, slug }`
- **后端位置**: `AdminForumPostVO.java:39-64`
- **后端返回**: 扁平字段 `username`, `avatar`, `communityName`, `communitySlug`
- **影响**: 前端 `columns.ts` 通过 `post.author?.username` 和 `post.community?.name` 访问，若后端未返回嵌套对象则访问为 `undefined`，显示 fallback 文本。ForumPostDetailDrawer 同样依赖嵌套结构。
- **修复建议**: 前端 API 层添加转换逻辑（类似用户端），或修改 AdminForumPostVO 返回嵌套对象。推荐前端添加转换，因为后端 Admin VO 可能被其他消费方使用。

### 差异 #3: 管理端 flag/unflag 端点缺失（高风险 — 功能缺失）

- **前端位置**: `management/src/api/admin/forum.ts:151-157`
- **前端调用**: `POST /admin/forum/posts/{id}/flag` 和 `POST /admin/forum/posts/{id}/unflag`
- **后端位置**: `AdminForumController.java`
- **后端情况**: 控制器中不存在 `flagPost` 和 `unflagPost` 方法。前端 `flagPost` 传入 `{ reason }` body。
- **影响**: 管理端"标记帖子"和"取消标记"功能完全不可用，调用会返回 404。
- **修复建议**: 后端 `AdminForumController` 添加：
  ```java
  @PostMapping("/posts/{id}/flag")
  public Result<Void> flagPost(@PathVariable String id, @RequestBody Map<String, String> body) { ... }

  @PostMapping("/posts/{id}/unflag")
  public Result<Void> unflagPost(@PathVariable String id) { ... }
  ```

### 差异 #4: 创建/更新 Post DTO 字段缺失（中风险）

- **前端位置**: `console/src/api/forum.ts:154-178`
- **前端 `createForumPost` 参数**: `title, excerpt, communityId, tags?, flairType?, flairLabel?`
- **前端 `updateForumPost` 参数**: `title?, excerpt?, tags?, flairType?, flairLabel?, isPinned?, isLocked?`
- **后端位置**: `CreatePostDTO.java:16-47`, `UpdatePostDTO.java:14-46`
- **后端字段**: `title, excerpt, body, communityId, tags, flairType, flairLabel, media`（Create 还多 `isPinned/isLocked` 仅 Update 有）
- **影响**: 用户端创建/编辑帖子时无法提交 `body`（正文内容）和 `media`（媒体附件），功能不完整。虽然当前 UI 可能只支持 excerpt，但未来扩展受限。
- **修复建议**: 前端 `createForumPost` 和 `updateForumPost` 添加 `body?: string` 和 `media?: unknown[]` 参数；同时更新 ForumEditorView 的表单提交逻辑。

### 差异 #5: 用户端 join/leave community API 缺失（中风险 — 功能缺失）

- **前端位置**: `console/src/api/forum.ts`（全局搜索无 join/leave 函数）
- **后端位置**: `ForumController.java:347-375`
- **后端端点**: `POST /forum/communities/{id}/join` 和 `POST /forum/communities/{id}/leave`
- **影响**: 用户端无法实现加入/离开社区功能。`ForumCommunity.isMember` 和 `userRole` 字段已在前端类型中定义，但无 API 支持状态变更。
- **修复建议**: 前端 `console/src/api/forum.ts` 添加：
  ```ts
  export async function joinForumCommunity(id: string): Promise<void> { ... }
  export async function leaveForumCommunity(id: string): Promise<void> { ... }
  ```

### 差异 #6: 用户端 `likes/dislikes` 字段来源不明确（低风险）

- **前端位置**: `console/src/types/forum.ts:169-170`, `console/src/views/forum/components/ForumPostCard.vue:125-126`
- **前端定义**: `likes?: number`, `dislikes?: number`（ForumPost 直接属性）
- **后端位置**: `ForumPostVO.java:114`
- **后端情况**: 后端无直接 `likes/dislikes` 字段，仅有 `stats: Object`。前端 `ForumPostCard` 通过 `resolveVoteCounts` 从 `post.likes`, `post.dislikes`, `localStats` 三处聚合。
- **影响**: 运行时依赖后端 `stats` 对象的结构约定，若后端 `stats` 内字段名变更会导致投票数显示异常。
- **修复建议**: 后端 `ForumPostVO` 显式定义 `stats` 为嵌套对象（如 `ForumPostStatsVO`），包含 `likes`, `dislikes`, `comments`, `views`, `saves`, `shares`, `score` 等字段；前端移除 `likes/dislikes` 直接属性，统一从 `stats` 读取。

### 差异 #7: 用户端 Comment 类型与后端 VO 字段不匹配（中风险）

- **前端位置**: `console/src/types/forum.ts:189-207`
- **前端 ForumComment**: 有 `upvotes`, `likes`, `dislikes`, `userVote`，无 `markdown`, `editedAt`, `isFlagged`, `replyCount`
- **后端位置**: `ForumCommentVO.java:14-105`
- **后端 ForumCommentVO**: 有 `markdown`, `editedAt`, `isFlagged`, `flaggedReason`, `flaggedAt`, `isAuthor`, `replyCount`，无 `upvotes`, `likes`, `dislikes`, `userVote`
- **影响**: 评论投票功能（upvotes/likes/dislikes/userVote）在前端类型中有定义但后端无对应字段，实际运行依赖后端是否在 `stats` 或其他地方返回。后端丰富的评论元数据（编辑时间、标记状态、回复数）前端未消费。
- **修复建议**: (a) 后端补充 `likes`, `dislikes`, `userVote` 字段；或 (b) 前端将评论投票字段标记为可选/待实现，并补充消费 `replyCount` 和 `editedAt` 以展示评论编辑状态。

### 差异 #8: 管理端查询参数缺少 `isDeleted`（低风险）

- **前端位置**: `management/src/api/admin/forum.ts:49-60`
- **前端 `ForumPostQueryParams`**: 无 `isDeleted` 字段
- **后端位置**: `AdminForumPostQueryDTO.java:44-45`
- **后端**: 支持 `isDeleted` 过滤
- **影响**: 管理端无法按"已删除"状态筛选帖子，但当前 UI 工具栏中未设计该筛选器，不影响现有功能。
- **修复建议**: 前端 `ForumPostQueryParams` 添加 `isDeleted?: boolean`，并在 `ForumPostsListView.vue` 工具栏添加对应筛选器（如需要）。

---

## 五、相关文件索引

### 前端（用户端）
- `console/src/api/forum.ts` — API 封装 + 类型定义
- `console/src/types/forum.ts` — Forum 领域类型
- `console/src/views/forum/ForumFeedView.vue` — 帖子列表页
- `console/src/views/forum/ForumThreadView.vue` — 帖子详情/评论页
- `console/src/views/forum/ForumEditorView.vue` — 创建/编辑帖子页
- `console/src/views/forum/components/ForumPostCard.vue` — 帖子卡片组件

### 前端（管理端）
- `management/src/api/admin/forum.ts` — Admin API 封装 + 类型定义
- `management/src/stores/admin/forum.ts` — Pinia store
- `management/src/views/forum/ForumPostsListView.vue` — 帖子列表页
- `management/src/views/forum/ForumPostDetailDrawer.vue` — 帖子详情 Drawer
- `management/src/views/forum/columns.ts` — 表格列定义
- `management/src/lib/entities/forum.ts` — 状态徽章辅助函数

### 后端（用户端）
- `backend-spring/src/main/java/com/ulticode/modules/forum/controller/ForumController.java` — 用户端控制器
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/ForumPostVO.java` — 帖子 VO
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/ForumCommentVO.java` — 评论 VO
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/ForumCommunityVO.java` — 社区 VO
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/ForumCommunityDetailVO.java` — 社区详情 VO
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/ForumPostThreadVO.java` — 帖子线程 VO
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/CreatePostDTO.java` — 创建帖子 DTO
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/UpdatePostDTO.java` — 更新帖子 DTO
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/CreateCommentDTO.java` — 创建评论 DTO
- `backend-spring/src/main/java/com/ulticode/modules/forum/dto/UpdateCommentDTO.java` — 更新评论 DTO

### 后端（管理端）
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminForumController.java` — 管理端控制器
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminForumPostVO.java` — 管理端帖子 VO
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminForumPostQueryDTO.java` — 管理端查询 DTO
