# Plan: Comments 前后端 API 颗粒度对齐

## Summary
对齐 Comments 模块前后端 API 的字段名、返回类型和过滤能力，修复 4 处高风险差异（Admin VO 字段名/结构不一致、flag/unflag 返回类型不一致）和 4 处中低风险差异（Solution 评论字段名、CommentsTab 缺 postId 过滤、CommentDetailView 硬编码 type、Solution 评论缺 @CheckBan）。

## User Story
As a 管理员, I want 评论管理页面能正确展示和操作所有类型的评论, so that 我可以高效审核 forum 和 solution 评论而不遇到字段缺失或类型错误。

## Problem → Solution
**Current state**: AdminCommentVO 的 `parentCommentId`/`parentId`/`username`/`avatar` 与前端 `Comment` 类型的 `parentId`/`author` 结构不匹配，导致管理端列表和详情页字段读取为 undefined；flag/unflag 返回 Void 导致本地状态不更新；CommentsTab 显示所有 forum 评论而非当前帖子的；CommentDetailView 不支持 solution 评论。
**Desired state**: 后端 AdminCommentVO 字段名与前端类型完全一致，flag/unflag 返回更新后对象，CommentsTab 支持按 postId 过滤，CommentDetailView 支持两种类型，Solution 评论有 @CheckBan 保护。

## Metadata
- **Complexity**: Large
- **Source PRD**: docs/comments-api-granularity-analysis.md
- **PRD Phase**: N/A (standalone alignment task)
- **Estimated Files**: 18-22

---

## UX Design

### Before
```
┌─────────────────────────────────────────┐
│ 管理端评论列表                           │
│ ┌──────────┬──────────┬────────┐        │
│ │ comment  │ author   │ status │        │
│ │ "great!" │ undefined│ ACTIVE │        │  ← author 为 undefined
│ └──────────┴──────────┴────────┘        │
│                                         │
│ 帖子详情页 CommentsTab:                  │
│ 显示所有论坛评论(包括其他帖子的)          │  ← 无效过滤
│                                         │
│ Solution 评论详情: 404                  │  ← 路由不存在
└─────────────────────────────────────────┘
```

### After
```
┌─────────────────────────────────────────┐
│ 管理端评论列表                           │
│ ┌──────────┬──────────┬────────┐        │
│ │ comment  │ author   │ status │        │
│ │ "great!" │ Alice    │ ACTIVE │        │  ← author 正确显示
│ └──────────┴──────────┴────────┘        │
│                                         │
│ 帖子详情页 CommentsTab:                  │
│ 仅显示当前帖子的评论                      │  ← 按 postId 过滤
│                                         │
│ /comments/forum/abc → 论坛评论详情       │
│ /comments/solution/xyz → 题解评论详情    │  ← 双类型支持
└─────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| 管理端评论列表 author 列 | 显示 undefined | 显示用户名 | AdminCommentVO 结构对齐 |
| Flag/Unflag 操作 | 需手动刷新列表 | 本地即时更新状态 | 返回更新后对象 |
| 帖子详情 CommentsTab | 显示所有 forum 评论 | 仅显示当前帖子评论 | 后端新增 postId 过滤 |
| Solution 评论详情路由 | 404 | 正常展示 | 新增路由+动态 type |
| 管理端 isDeleted 过滤器 | 不可用 | UI 可筛选 | 新增过滤选项 |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `backend-spring/.../admin/dto/AdminCommentVO.java` | all | 需重构字段名/结构 |
| P0 (critical) | `backend-spring/.../admin/service/impl/AdminCommentServiceImpl.java` | 366-510 | VO 映射逻辑需重写 |
| P0 (critical) | `backend-spring/.../admin/controller/AdminCommentController.java` | 53-75 | flag/unflag 返回类型需改 |
| P0 (critical) | `management/src/api/admin/comments.ts` | all | 前端类型需对齐 |
| P1 (important) | `backend-spring/.../admin/dto/AdminCommentQueryDTO.java` | all | 需新增 postId/solutionId |
| P1 (important) | `management/src/stores/admin/comments.ts` | all | store 逻辑需适配 |
| P1 (important) | `management/src/views/comments/CommentDetailView.vue` | all | 硬编码 type 需动态化 |
| P1 (important) | `management/src/router/index.ts` | 194-213 | 需新增路由 |
| P1 (important) | `management/src/views/forum/components/CommentsTab.vue` | all | 需传 postId |
| P2 (reference) | `backend-spring/.../solution/dto/SolutionCommentVO.java` | all | 参考 Solution 字段 |
| P2 (reference) | `backend-spring/.../solution/service/impl/SolutionServiceImpl.java` | 95-186 | @CheckBan 位置参考 |
| P2 (reference) | `console/src/types/comment.ts` | all | Console 类型参考 |
| P2 (reference) | `console/src/components/comments/comment-tree-builder.ts` | 30-54 | mapToComment 映射逻辑 |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| MyBatis-Plus LambdaQueryWrapper | Internal codebase patterns | 使用 `eq()`, `like()`, `orderBy()` 链式调用 |
| Jackson @JsonInclude | Internal codebase patterns | `@JsonInclude(JsonInclude.Include.NON_NULL)` 已在 VO 上使用 |
| Vue Router dynamic params | Internal codebase patterns | `:type` 路由参数 + `route.params.type as CommentType` |

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: management/src/api/admin/comments.ts:5-30
```typescript
export interface Comment {
  id: string
  content: string
  createdAt: string
  updatedAt: string
  authorId: string
  parentId?: string
  type: CommentType
  parentTitle?: string
  isFlagged: boolean
  flaggedReason?: string
  flaggedAt?: string
  isDeleted: boolean
  deletedAt?: string
  deletedBy?: string
  author: {
    id: string
    username: string
    avatar?: string
  }
}
```

### ERROR_HANDLING
// SOURCE: management/src/stores/admin/comments.ts:25-31
```typescript
error.value =
  (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
  'Failed to fetch comments'
console.error('Failed to fetch comments:', err)
```

### SERVICE_PATTERN
// SOURCE: backend-spring/.../admin/service/impl/AdminCommentServiceImpl.java:366-400
```java
private AdminCommentVO forumToAdminVO(ForumComment comment, Map<String, User> userMap,
                                      Map<String, ForumPost> postMap) {
    AdminCommentVO vo = new AdminCommentVO();
    vo.setId(comment.getId());
    vo.setContent(comment.getBody());
    // ... manual field mapping
    User user = userMap.get(comment.getAuthorId());
    if (user != null) {
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
    }
    return vo;
}
```

### TEST_STRUCTURE
// SOURCE: backend-spring/src/test/java/com/ulticode/ (project convention)
Tests use JUnit 5 + AssertJ. Test files mirror src/main package structure.

### ADMINVO_MAPPING_ASYMMETRY
// SOURCE: AdminCommentServiceImpl.java:366-510
Key mappings that need fixing:
- ForumComment.body -> AdminCommentVO.content (renamed in VO)
- ForumComment.editedAt -> AdminCommentVO.updatedAt (renamed in VO)
- ForumComment.authorId -> AdminCommentVO.authorId (same)
- ForumComment.parentId -> AdminCommentVO.parentCommentId (renamed, conflicts with frontend's parentId)
- ForumComment.postId -> AdminCommentVO.parentId (semantic mismatch!)
- SolutionComment.userId -> AdminCommentVO.authorId (renamed)
- SolutionComment.content -> AdminCommentVO.content (same)
- User.getUsername() -> AdminCommentVO.username (flat, frontend expects nested author)

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend/.../admin/dto/AdminCommentVO.java` | UPDATE | 重构字段名和嵌套结构 |
| `backend/.../admin/dto/AdminCommentQueryDTO.java` | UPDATE | 新增 parentId/solutionId 过滤字段 |
| `backend/.../admin/controller/AdminCommentController.java` | UPDATE | flag/unflag 返回 AdminCommentVO |
| `backend/.../admin/service/AdminCommentService.java` | UPDATE | flag/unflag 返回 AdminCommentVO |
| `backend/.../admin/service/impl/AdminCommentServiceImpl.java` | UPDATE | 重写映射逻辑，新增过滤 |
| `management/src/api/admin/comments.ts` | UPDATE | 类型对齐后端新结构 |
| `management/src/stores/admin/comments.ts` | UPDATE | 适配新返回类型 |
| `management/src/views/comments/CommentsListView.vue` | UPDATE | 新增 isDeleted 过滤器 |
| `management/src/views/comments/CommentDetailView.vue` | UPDATE | 动态 type + 双类型权限 |
| `management/src/views/comments/columns.ts` | UPDATE | 适配新字段 |
| `management/src/views/forum/components/CommentsTab.vue` | UPDATE | 传入 postId |
| `management/src/router/index.ts` | UPDATE | 新增 solution 评论详情路由 |
| `backend/.../solution/service/impl/SolutionServiceImpl.java` | UPDATE | createComment 添加 @CheckBan |
| `console/src/types/comment.ts` | UPDATE | 移除扁平 authorId/authorUsername/authorAvatar |
| `console/src/api/solution.ts` | UPDATE | SolutionComment 独立类型定义 |

## NOT Building

- 评论分页（差异 #7，长期规划，单独排期）
- CommentForm 字数限制（差异 #9，低优先级 UX 优化）
- 评论投票 N+1 问题（跨模块优化，需独立设计）
- 统一 Forum/Solution 评论 Schema（长期重构，当前仅管理端对齐）

---

## Step-by-Step Tasks

---

### TASK-001: [Backend] 重构 AdminCommentVO 字段名与嵌套结构

- **ACTION**: 修改 `AdminCommentVO.java`，将 `parentCommentId` 重命名为 `parentId`（父评论ID），将 `parentId` 重命名为 `parentEntityId`（父实体 post/solution ID），将 `username`/`avatar` 重构为嵌套 `AuthorInfo` 内部类
- **IMPLEMENT**:
  1. 在 `AdminCommentVO` 中新增 `AuthorInfo` 静态内部类，包含 `id`、`username`、`avatar` 字段
  2. 将 `username` 和 `avatar` 字段替换为 `author` 字段（类型 `AuthorInfo`）
  3. 将 `parentCommentId` 字段重命名为 `parentId`（语义：父评论ID）
  4. 将 `parentId` 字段重命名为 `parentEntityId`（语义：父实体 post/solution ID）
  5. 保留 `parentTitle` 字段不变
- **MIRROR**: 参考 `ForumCommentVO.java` 的 `authorId`/`authorUsername`/`authorAvatar` 扁平字段模式，但管理端统一使用嵌套结构
- **IMPORTS**: 无新增依赖
- **GOTCHA**: `@JsonInclude(JsonInclude.Include.NON_NULL)` 已存在于 VO 上，嵌套 author 为 null 时不会序列化
- **VALIDATE**: 编译通过 + AdminCommentVO JSON 序列化输出字段名与前端 `Comment` 类型一致

---

### TASK-002: [Backend] 重写 AdminCommentServiceImpl 映射逻辑适配新 VO

- **ACTION**: 修改 `AdminCommentServiceImpl.java` 中的 4 个 `forumToAdminVO`/`solutionToAdminVO` 私有方法，适配新 VO 字段名
- **IMPLEMENT**:
  1. `forumToAdminVO(comment, userMap, postMap)`: 设置 `vo.setParentId(comment.getParentId())`（父评论），`vo.setParentEntityId(comment.getPostId())`（父实体），`vo.setAuthor(new AuthorInfo(user.getId(), user.getUsername(), user.getAvatar()))`
  2. `forumToAdminVO(comment)`: 单实体版本同上
  3. `solutionToAdminVO(comment, userMap, solutionMap)`: 设置 `vo.setParentId(comment.getParentId())`，`vo.setParentEntityId(comment.getSolutionId())`，`vo.setAuthor(new AuthorInfo(user.getId(), user.getUsername(), user.getAvatar()))`
  4. `solutionToAdminVO(comment)`: 单实体版本同上
  5. 所有方法中移除 `vo.setUsername()`/`vo.setAvatar()` 调用
  6. 所有方法中移除 `vo.setParentCommentId()` 调用
- **MIRROR**: 参考 `AdminCommentServiceImpl.java:366-400` 现有映射模式
- **IMPORTS**: 无新增依赖
- **GOTCHA**: User 对象可能为 null（已删除用户），需 null 检查后设置 author；AuthorInfo 内部类定义在 AdminCommentVO 中
- **VALIDATE**: 单元测试验证映射正确性：ForumComment -> AdminCommentVO 字段名与值正确

---

### TASK-003: [Backend] 修改 flagComment/unflagComment 返回 AdminCommentVO

- **ACTION**: 将 `AdminCommentService.flagComment` 和 `unflagComment` 返回类型从 `void` 改为 `AdminCommentVO`，Controller 层同步修改返回类型
- **IMPLEMENT**:
  1. `AdminCommentService.java`: `AdminCommentVO flagComment(String id, String type, String reason)` 和 `AdminCommentVO unflagComment(String id, String type)`
  2. `AdminCommentServiceImpl.java`: flagComment 方法末尾添加 `return getComment(id, type);`（利用已有的单实体查询方法）
  3. `AdminCommentServiceImpl.java`: unflagComment 方法末尾同上
  4. `AdminCommentController.java`: flagComment 返回 `Result<AdminCommentVO>`，unflagComment 返回 `Result<AdminCommentVO>`
- **MIRROR**: 参考 `AdminCommentController.getComment` 返回 `Result<AdminCommentVO>` 的模式
- **IMPORTS**: 无新增依赖
- **GOTCHA**: `getComment()` 内部会根据 type 调用不同的 `selectById`，需要确保 flag/unflag 修改已提交到数据库后再调用（当前已在 `updateById()` 之后）
- **VALIDATE**: curl 测试 `PATCH /admin/comments/forum/{id}/flag` 返回 `Result<AdminCommentVO>` 且 `isFlagged=true`

---

### TASK-004: [Backend] AdminCommentQueryDTO 新增 parentEntityId 过滤字段

- **ACTION**: 在 `AdminCommentQueryDTO` 中新增 `parentEntityId` 可选字段，用于按父实体（postId 或 solutionId）过滤
- **IMPLEMENT**:
  1. 在 `AdminCommentQueryDTO.java` 中新增 `private String parentEntityId;` 字段（可选，默认 null）
  2. 在 `AdminCommentServiceImpl.getForumComments()` 中：当 `query.getParentEntityId() != null` 时追加 `.eq(ForumComment::getPostId, query.getParentEntityId())`
  3. 在 `AdminCommentServiceImpl.getSolutionComments()` 中：当 `query.getParentEntityId() != null` 时追加 `.eq(SolutionComment::getSolutionId, query.getParentEntityId())`
- **MIRROR**: 参考 `AdminCommentServiceImpl.java:70-130` 现有的 `eq()` 条件构建模式
- **IMPORTS**: 无新增依赖
- **GOTCHA**: `getForumCommentsAsFallback()` 也需同步添加此过滤条件，否则 type 未指定时过滤不生效
- **VALIDATE**: 测试 `GET /admin/comments?type=forum&parentEntityId=xxx` 仅返回指定帖子的评论

---

### TASK-005: [Frontend-Admin] 更新 comments.ts 类型定义适配新 VO

- **ACTION**: 重写 `management/src/api/admin/comments.ts` 中的 `Comment` 接口，对齐后端新 `AdminCommentVO` 结构
- **IMPLEMENT**:
  1. 将 `parentId?: string` 保持不变（现在后端 JSON key 就是 `parentId`，语义=父评论ID）
  2. 新增 `parentEntityId?: string` 字段
  3. 将 `author: { id: string; username: string; avatar?: string }` 保持不变（后端已返回嵌套 author）
  4. 移除 `CommentQueryParams` 中已有的 `isDeleted` 字段保持（不需要修改）
  5. 更新 `flagComment` 返回类型为 `Promise<Comment>`
  6. 更新 `unflagComment` 返回类型为 `Promise<Comment>`
- **MIRROR**: 参考 `management/src/api/admin/comments.ts:5-30` 现有类型
- **IMPORTS**: 无新增
- **GOTCHA**: `parentId` 在旧后端 JSON 中 key 是 `parentCommentId`，重构后变为 `parentId`，前端不需要额外适配
- **VALIDATE**: TypeScript 编译无错误

---

### TASK-006: [Frontend-Admin] 更新 comments store 适配新返回类型

- **ACTION**: 修改 `management/src/stores/admin/comments.ts`，利用 flag/unflag 返回的 Comment 对象更新本地状态
- **IMPLEMENT**:
  1. `flagComment` action：用 `updatedComment = await commentsApi.flagComment(...)` 替换，然后用 `updatedComment` 更新 `comments.value[index]`
  2. `unflagComment` action：同上
  3. `bulkAction` action：传入当前过滤参数到 `fetchComments` 以保留视图状态（修复 `fetchComments()` 无参数导致过滤/分页重置的问题）
- **MIRROR**: 参考 `management/src/stores/admin/comments.ts:53-93` 现有 store 模式
- **IMPORTS**: 无新增
- **GOTCHA**: `bulkAction` 当前调用 `fetchComments()` 无参数，需保存当前 params 引用或从调用方传入
- **VALIDATE**: Flag 操作后本地列表中对应评论的 `isFlagged` 立即变为 true，无需刷新

---

### TASK-007: [Frontend-Admin] 更新 columns.ts 适配新字段

- **ACTION**: 修改 `management/src/views/comments/columns.ts`，使用新的 `author` 嵌套对象和 `parentEntityId`
- **IMPLEMENT**:
  1. `author` 列（约 line 111-129）：访问 `row.original.author?.username` 改为 `row.original.author?.username`（已是嵌套，无需修改，但验证不再为 undefined）
  2. `content` 列（约 line 94-108）：保持 `comment.parentTitle` 引用（后端 JSON key `parentTitle` 不变）
  3. 确认 `parentId` 引用已移除（因为语义已变），如 columns 中有使用 `parentId` 显示父评论的场景需改用新的字段
- **MIRROR**: 参考 `management/src/views/comments/columns.ts:94-129`
- **IMPORTS**: 无新增
- **GOTCHA**: `comment.parentTitle` 在 JSON 中是 `parentTitle`（不变），但 `comment.parentId` 在前端类型中语义已变为父评论ID
- **VALIDATE**: TypeScript 编译无错误 + 管理端列表 author 列正确显示用户名

---

### TASK-008: [Frontend-Admin] CommentDetailView 支持双类型路由

- **ACTION**: 修改 `CommentDetailView.vue` 和路由配置，支持 `/comments/:type/:id` 动态路由
- **IMPLEMENT**:
  1. `management/src/router/index.ts`：将路由 `path: 'forum/comments/:id'` 改为 `path: 'comments/:type/:id'`，`name` 改为 `'comment-detail'`
  2. `CommentDetailView.vue`：将 `commentType = computed(() => 'forum')` 改为 `commentType = computed(() => route.params.type as CommentType)`
  3. `CommentDetailView.vue`：权限检查改为动态 — `canModerate = authStore.hasPermission('MODERATE', commentType === 'forum' ? 'FORUM_COMMENT' : 'SOLUTION_COMMENT')`
  4. 更新所有引用 `forum-comment-detail` 路由名的地方（如 ReportsView、ModerationQueueView 中的 "View Entity" 链接）
- **MIRROR**: 参考 `management/src/router/index.ts:204-213` 现有路由模式
- **IMPORTS**: `type { CommentType }` from `@/api/admin/comments`
- **GOTCHA**: 需全局搜索 `forum-comment-detail` 和 `forum/comments/` 引用并更新
- **VALIDATE**: 访问 `/comments/forum/abc` 和 `/comments/solution/xyz` 均能正确加载评论详情

---

### TASK-009: [Frontend-Admin] CommentsTab 传入 parentEntityId 过滤

- **ACTION**: 修改 `CommentsTab.vue`，在 API 请求中传入 `parentEntityId` 参数以过滤当前帖子的评论
- **IMPLEMENT**:
  1. `CommentsTab.vue` 中 `loadComments()` 函数：在 `commentsStore.fetchComments(...)` 调用中添加 `parentEntityId: props.postId` 参数
  2. 同时移除代码注释 `// Note: The comments API doesn't currently support filtering by post_id`
- **MIRROR**: 参考 `management/src/views/forum/components/CommentsTab.vue:61-68` 现有请求模式
- **IMPORTS**: 无新增
- **GOTCHA**: 后端 TASK-004 必须先完成，否则 `parentEntityId` 参数会被后端忽略
- **VALIDATE**: 在帖子 A 详情页的 CommentsTab 中仅显示帖子 A 的评论，不显示帖子 B 的

---

### TASK-010: [Frontend-Admin] CommentsListView 新增 isDeleted 过滤器

- **ACTION**: 在 `CommentsListView.vue` 的 `toolbarFilters` 中添加删除状态过滤器
- **IMPLEMENT**:
  1. 新增 `deletedFilter = ref<string>('all')` ref
  2. 在 `toolbarFilters` 数组中添加第三个 filter 对象，options: `all / deleted / active`
  3. 在 `transformParams` 中将 `deletedFilter` 映射为 `isDeleted: filters.deletedFilter === 'all' ? undefined : filters.deletedFilter === 'deleted'`
  4. 添加对应 i18n key: `comments.filters.deletedStatus`, `comments.filters.allStatus`, `comments.filters.deleted`, `comments.filters.active`
- **MIRROR**: 参考 `CommentsListView.vue:56-76` 现有 filter 模式
- **IMPORTS**: 无新增
- **GOTCHA**: `toolbarFilters` 是 computed，需要在 `filters()` 函数中也添加 `deletedFilter` 的返回值
- **VALIDATE**: UI 中可选择"已删除/活跃/全部"过滤评论

---

### TASK-011: [Backend] SolutionServiceImpl.createComment 添加 @CheckBan

- **ACTION**: 在 `SolutionServiceImpl.createComment()` 方法上添加 `@CheckBan` 注解
- **IMPLEMENT**:
  1. 在 `SolutionServiceImpl.java` 约 line 148 的 `createComment` 方法签名上方添加 `@CheckBan` 注解
  2. 确认 `@CheckBan` 注解已在项目其他位置使用（ForumCommentServiceImpl.createComment 已有该注解）
- **MIRROR**: 参考 `ForumCommentServiceImpl.java:44` 的 `@CheckBan` 使用模式
- **IMPORTS**: `import com.ulticode.common.annotation.CheckBan;`
- **GOTCHA**: `@CheckBan` 是 AOP 注解，需要确保切面类 `CheckBanAspect` 已配置，且 solution 模块也有用户上下文
- **VALIDATE**: 被封禁用户调用 `POST /api/solutions/{id}/comments` 返回 403

---

### TASK-012: [Frontend-Console] 独立 SolutionComment 类型定义

- **ACTION**: 在 `console/src/types/` 中新增 `solution-comment.ts`，定义独立的 SolutionComment 接口，不复用 ForumComment
- **IMPLEMENT**:
  1. 创建 `console/src/types/solution-comment.ts`，定义：
     ```typescript
     export interface SolutionComment {
       id: string
       solutionId: string
       parentId?: string
       userId: string
       authorId: string
       authorUsername: string
       authorAvatar?: string
       content: string
       createdAt: string
       updatedAt: string
       isFlagged?: boolean
     }
     ```
  2. 修改 `console/src/api/solution.ts` 中 `fetchSolutionComments` 返回类型为 `SolutionComment[]`
  3. 修改 `console/src/api/solution.ts` 中 `createSolutionComment` 返回类型为 `SolutionComment`
  4. 修改 `console/src/api/solution.ts` 中 `updateSolutionComment` 返回类型为 `SolutionComment`
  5. 修改 `console/src/components/comments/comment-tree-builder.ts` 中的 `mapToComment` 函数：将 `input.content` 映射为 `Comment.content`（替代原来依赖 `input.body || input.content` 的 fallback）
- **MIRROR**: 参考 `console/src/types/forum.ts:186-210` 的 ForumComment 接口
- **IMPORTS**: `import type { SolutionComment } from '@/types/solution-comment'`
- **GOTCHA**: `comment-tree-builder.ts` 当前使用 `input.body || input.content` 作为 fallback，新增 SolutionComment 后需确保两种类型都能正确映射
- **VALIDATE**: TypeScript 编译无错误 + 题解评论正常显示内容

---

### TASK-013: [Frontend-Console] 清理 ForumComment 类型冗余字段

- **ACTION**: 从 `console/src/types/forum.ts` 的 `ForumComment` 接口中移除扁平的 `authorId`/`authorUsername`/`authorAvatar` 字段，统一使用嵌套 `author?: ForumUser`
- **IMPLEMENT**:
  1. 移除 `ForumComment` 中的 `authorId?: string`、`authorUsername?: string`、`authorAvatar?: string` 字段
  2. 保留 `author?: ForumUser` 嵌套字段
  3. 更新 `comment-tree-builder.ts` 中的 `mapToComment` 函数：移除对扁平字段的引用，统一使用 `input.author?.username`、`input.author?.id`、`input.author?.avatar`
  4. 移除 `ForumComment` 中 solution 评论不支持的 `isPinned`/`isLocked`/`markdown` 字段（保留在 ForumComment 中，因为它是论坛专用类型）
- **MIRROR**: 参考 `console/src/api/forum.ts:120-145` 的 fetchForumThread transform 模式
- **IMPORTS**: 无新增
- **GOTCHA**: `fetchForumThread` 已在 transform 中将扁平字段转为嵌套 author，移除扁平字段不会影响该流程
- **VALIDATE**: 论坛评论正常显示 + TypeScript 编译无错误

---

### TASK-014: [i18n] 新增管理端评论 isDeleted 过滤器翻译

- **ACTION**: 在管理端 i18n 文件中新增删除状态过滤器的翻译键值
- **IMPLEMENT**:
  1. `management/src/i18n/locales/en-US/modules/comments.ts`: 新增 `filters.deletedStatus`、`filters.allStatus`、`filters.deleted`、`filters.active`
  2. `management/src/i18n/locales/zh-CN/modules/comments.ts`: 新增对应中文翻译
- **MIRROR**: 参考 `management/src/i18n/locales/en-US/modules/comments.ts` 现有的 `filters.flagStatus`、`filters.all`、`filters.flagged`、`filters.clean` 模式
- **IMPORTS**: 无新增
- **GOTCHA**: 确保 key 命名风格与现有一致（camelCase）
- **VALIDATE**: 管理端 UI 中删除状态过滤器正确显示中英文标签

---

### TASK-015: [Frontend-Admin] 更新报告和审核页面的 View Entity 链接

- **ACTION**: 更新 `ReportsView` 和 `ModerationQueueView` 中"View Entity"链接，使其根据 entityType 动态路由到正确的评论详情页
- **IMPLEMENT**:
  1. 在 `ModerationQueueView` 和 `ReportsView` 中，找到 "View Entity" 链接逻辑
  2. 将硬编码的 `/forum/comments/${entityId}` 替换为动态路由：当 entityType 包含 `solution_comment` 时路由到 `/comments/solution/${entityId}`，当包含 `forum_comment` 时路由到 `/comments/forum/${entityId}`
  3. 确保两种 entity type 的路由名称均使用 `'comment-detail'`
- **MIRROR**: 参考 `management/src/views/moderation/columns.ts` 和 `management/src/views/moderation/reports-columns.ts` 中的链接模式
- **IMPORTS**: `useRouter` from `vue-router`
- **GOTCHA**: ModerationQueueView 和 ReportsView 可能在 columns 定义中使用 router link，需确认具体引用位置
- **VALIDATE**: 在举报列表中点击 forum_comment 实体的"View Entity"跳转到 `/comments/forum/{id}`，solution_comment 跳转到 `/comments/solution/{id}`

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| AdminCommentVO serialization | parentId="abc", parentEntityId="post123", author={id:"u1", username:"alice"} | JSON: `{"parentId":"abc","parentEntityId":"post123","author":{"id":"u1","username":"alice"}}` | No |
| ForumComment -> AdminCommentVO mapping | ForumComment with body="test", parentId="p1", postId="post1" | AdminCommentVO with content="test", parentId="p1", parentEntityId="post1" | No |
| SolutionComment -> AdminCommentVO mapping | SolutionComment with content="test", parentId="p1", solutionId="sol1" | AdminCommentVO with content="test", parentId="p1", parentEntityId="sol1" | No |
| flagComment returns AdminCommentVO | Valid id/type with reason | AdminCommentVO with isFlagged=true | No |
| unflagComment returns AdminCommentVO | Valid id/type | AdminCommentVO with isFlagged=false | No |
| getComments with parentEntityId filter | type=forum, parentEntityId="post1" | Only comments with postId="post1" | No |
| @CheckBan on solution createComment | Banned user | BusinessException thrown | Yes |
| Null user in mapping | ForumComment with non-existent authorId | AdminCommentVO with author=null | Yes |

### Edge Cases Checklist
- [x] Null author (deleted user)
- [x] parentEntityId filter with invalid UUID
- [x] Solution comment detail route for forum-only user permissions
- [x] Bulk action preserving filter state
- [x] CommentType in URL is invalid string

---

## Validation Commands

### Static Analysis
```bash
cd backend-spring && ./mvnw compile -q
```
EXPECT: Zero compile errors

```bash
cd management && pnpm type-check
```
EXPECT: Zero type errors

```bash
cd console && pnpm type-check
```
EXPECT: Zero type errors

### Lint
```bash
cd management && pnpm lint
```
EXPECT: Zero lint errors

```bash
cd console && pnpm lint
```
EXPECT: Zero lint errors

### Full Test Suite
```bash
cd backend-spring && ./mvnw test -q
```
EXPECT: All tests pass

### Browser Validation
```bash
# 确保后端和管理前端运行
pm2 status
```
然后手动验证:
- 管理端评论列表 author 列显示用户名（非 undefined）
- Flag/Unflag 操作后状态即时更新
- 帖子详情页 CommentsTab 仅显示当前帖子评论
- `/comments/forum/{id}` 和 `/comments/solution/{id}` 均可访问
- 删除状态过滤器可用

---

## Acceptance Criteria
- [ ] AdminCommentVO JSON 输出字段名与前端 Comment 类型完全一致
- [ ] flagComment/unflagComment 返回 AdminCommentVO 对象
- [ ] CommentsTab 按 postId 过滤评论
- [ ] CommentDetailView 支持 forum 和 solution 两种类型
- [ ] Solution createComment 有 @CheckBan 保护
- [ ] 管理端列表 author 列正确显示用户名
- [ ] isDeleted 过滤器在 UI 中可用
- [ ] 所有 TypeScript 编译无错误
- [ ] 所有 lint 检查无错误
- [ ] 后端编译通过

## Completion Checklist
- [ ] Code follows discovered patterns
- [ ] Error handling matches codebase style
- [ ] No hardcoded values
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| AdminCommentVO 重构破坏现有管理端列表渲染 | Medium | High | 逐字段对齐前端类型，先改后端再改前端，同步部署 |
| flag/unflag 返回类型变更导致前端 store 逻辑需重写 | Low | Medium | 返回 AdminCommentVO 与 getComment 相同，store 逻辑简化 |
| @CheckBan AOP 切面在 solution 模块未生效 | Low | Medium | 验证 CheckBanAspect 切点表达式是否覆盖 solution 包路径 |
| router 路由变更导致现有链接失效 | Medium | High | 搜索所有 `forum-comment-detail` 引用并更新，添加 route redirect 兼容旧路径 |

## Notes
- 优先修复后端（TASK-001~004, TASK-011），再修复前端（TASK-005~010, TASK-012~015）
- TASK-004 必须在 TASK-009 之前完成
- TASK-001 和 TASK-002 必须同时完成，否则编译失败
- TASK-005 必须在 TASK-006/007 之后或同时完成