# Plan: Forum 前后端 API 颗粒度对齐

## Summary
本计划旨在修复 UltiCode 项目 Forum 模块前后端 API 的颗粒度差异，涵盖用户端（console）和管理端（management）共 8 处差异。修复工作包括前端类型定义调整、API 层转换逻辑补充、缺失 API 函数实现，以及后端缺失端点的补全。

## User Story
As a 平台开发者，
I want Forum 模块前后端 API 类型和端点完全对齐，
So that 消除运行时类型不匹配、404 错误和功能缺失，降低维护成本。

## Problem → Solution
**Current state**: 用户端存在 2 个前端缺失 API（join/leave community）、管理端存在 2 个后端缺失端点（flag/unflag），多处嵌套/扁平结构不一致导致类型定义与实际响应不匹配，DTO 字段缺失限制功能扩展。

**Desired state**: 所有 8 处差异修复完毕，前后端类型定义与实际响应一致，无 404 调用，所有功能可用。

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/forum-api-granularity-analysis.md`
- **PRD Phase**: standalone
- **Estimated Files**: 15+ files

---

## UX Design

### Before
```
┌─────────────────────────────┐
│ 用户端：                      │
  - 帖子创建/编辑无法提交 body  │
  - 社区加入/离开按钮无 API 支持 │
  - 投票数据依赖 stats 对象约定  │
  - 评论投票字段后端无对应数据   │
│ 管理端：                      │
  - 标记帖子按钮返回 404        │
  - 帖子列表 author/community   │
    显示为 fallback 文本        │
  - 无法按已删除状态筛选帖子    │
└─────────────────────────────┘
```

### After
```
┌─────────────────────────────┐
│ 用户端：                      │
  - 帖子支持 body 和 media 提交│
  - 社区加入/离开功能完整可用   │
  - 投票数据有明确来源和类型    │
  - 评论显示编辑时间和回复数    │
│ 管理端：                      │
  - 标记/取消标记帖子正常工作   │
  - 帖子列表正确显示作者/社区名 │
  - 可按已删除状态筛选帖子      │
└─────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| 用户端帖子编辑器 | 只能提交 title/excerpt/tags/flair | 额外支持 body 和 media | 需更新表单提交逻辑 |
| 用户端社区卡片 | 加入/离开按钮点击无反应 | 调用 API 并更新 isMember 状态 | 需添加 API 函数和状态更新 |
| 管理端帖子列表 | author/community 显示 fallback | 正确显示用户名和社区名 | 前端添加转换逻辑 |
| 管理端标记功能 | 返回 404 | 正常标记并记录 reason | 后端添加端点 |
| 管理端筛选器 | 无已删除筛选 | 新增 isDeleted 筛选选项 | 前端添加筛选器 |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `console/src/types/forum.ts` | 1-214 | 用户端 Forum 领域类型定义 |
| P0 (critical) | `console/src/api/forum.ts` | 1-190 | 用户端 API 封装和转换逻辑 |
| P0 (critical) | `management/src/api/admin/forum.ts` | 1-159 | 管理端 API 封装和类型定义 |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/forum/dto/ForumPostVO.java` | 1-156 | 后端帖子 VO 结构（扁平字段） |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/forum/dto/ForumCommentVO.java` | 1-106 | 后端评论 VO 结构 |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminForumPostVO.java` | 1-131 | 管理端帖子 VO 结构 |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/forum/controller/ForumController.java` | 1-400 | 用户端 Controller 端点定义 |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminForumController.java` | 1-148 | 管理端 Controller 端点定义 |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/forum/dto/CreatePostDTO.java` | 1-48 | 创建帖子 DTO |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/forum/dto/UpdatePostDTO.java` | 1-47 | 更新帖子 DTO |
| P2 (reference) | `docs/forum-api-granularity-analysis.md` | all | 完整差异分析 |

---

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Spring Boot Controller | Spring Docs | `@PostMapping`, `@PathVariable`, `@RequestBody` 用法 |
| Vue 3 TypeScript | Vue Docs | 类型定义和 API 封装模式 |

**No external research needed** — feature uses established internal patterns.

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: `console/src/api/forum.ts:12`
```typescript
export async function fetchForumPosts(): Promise<ForumPost[]>
```
// Frontend API functions: `camelCase` with `fetch`/`create`/`update`/`delete` prefix

### ERROR_HANDLING
// SOURCE: `console/src/api/forum.ts:144-148`
```typescript
export async function recordForumView(postId: string) {
  return apiPost(`/forum/posts/${postId}/view`, {}, { skipErrorHandler: true });
}
```
// Non-critical analytics skip global error handler

### LOGGING_PATTERN
// SOURCE: Not explicitly logged in frontend API layer
// Backend uses standard SLF4J logging via Lombok `@Slf4j`

### REPOSITORY_PATTERN
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminForumController.java:38`
```java
public Result<PageResult<AdminForumPostVO>> getPosts(AdminForumPostQueryDTO query) {
    return Result.success(adminForumService.getPosts(query));
}
```
// Controller delegates to Service, returns `Result<T>` envelope

### SERVICE_PATTERN
// SOURCE: AdminForumController uses constructor injection with `@RequiredArgsConstructor`
```java
private final AdminForumService adminForumService;
```

### TEST_STRUCTURE
// SOURCE: Project uses JUnit 5 + Mockito + AssertJ for backend, Vitest for frontend
// Backend: `@ExtendWith(MockitoExtension.class)`, `when(...).thenReturn(...)`
// Frontend: `test('description', () => { expect(...).toBe(...) })`

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `console/src/types/forum.ts` | UPDATE | 调整 ForumPost/ForumComment 类型以匹配后端实际响应 |
| `console/src/api/forum.ts` | UPDATE | 添加 body/media 参数、join/leave community API、完善转换逻辑 |
| `management/src/api/admin/forum.ts` | UPDATE | 添加 author/community 转换逻辑、isDeleted 查询参数 |
| `management/src/views/forum/columns.ts` | UPDATE | 适配 AdminForumPostVO 扁平字段 |
| `management/src/views/forum/ForumPostDetailDrawer.vue` | UPDATE | 适配 AdminForumPostVO 扁平字段 |
| `management/src/views/forum/ForumPostsListView.vue` | UPDATE | 添加 isDeleted 筛选器 |
| `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminForumController.java` | UPDATE | 添加 flagPost/unflagPost 端点 |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminForumService.java` | UPDATE | 添加 flag/unflag 业务逻辑 |
| `backend-spring/src/main/java/com/ulticode/modules/admin/dto/FlagPostRequest.java` | CREATE | 标记帖子请求 DTO |
| `console/src/views/forum/components/ForumPostCard.vue` | UPDATE | 适配投票字段从 stats 读取 |
| `console/src/views/forum/ForumThreadView.vue` | UPDATE | 适配评论字段变更 |
| `console/src/views/forum/ForumEditorView.vue` | UPDATE | 支持 body 和 media 提交 |

## NOT Building
- 不修改后端 ForumPostVO/ForumCommentVO/AdminForumPostVO 的扁平结构（保持后端稳定，前端适配）
- 不新增数据库表或 Flyway 迁移（使用现有 schema）
- 不实现评论投票后端逻辑（仅调整前端类型标记为可选）
- 不重构整个 Forum 模块架构（仅修复对齐差异）
- 不修改用户端 UI 设计（仅修复数据流）

---

## Step-by-Step Tasks

### Task 1: 用户端 ForumPost 类型与 API 转换对齐
- **ACTION**: 修改 `console/src/types/forum.ts` 中 `ForumPost` 接口，移除假设性的 `likes`/`dislikes` 直接属性，明确 `stats` 对象结构；修改 `console/src/api/forum.ts` 中 `fetchForumPosts`/`fetchCommunityPosts`/`fetchForumThread` 的转换逻辑以匹配后端实际响应。
- **IMPLEMENT**:
  - `ForumPost` 接口：移除 `likes?: number`, `dislikes?: number`（统一从 `stats` 读取）
  - `ForumPost` 接口：将 `author` 改为可选或同时支持 `authorId`/`authorUsername`/`authorAvatar` 扁平字段
  - `fetchForumPosts` 转换：保留现有扁平字段到嵌套对象的 map 逻辑，但移除对 `likes`/`dislikes` 的直接处理
  - `fetchForumThread` 转换：同上
- **MIRROR**: NAMING_CONVENTION (camelCase API functions), ERROR_HANDLING (skipErrorHandler for non-critical)
- **IMPORTS**: No new imports needed
- **GOTCHA**: 前端多个组件可能直接访问 `post.likes` 或 `post.dislikes`，需同步修改引用点
- **VALIDATE**: 在 `ForumPostCard.vue` 中确认投票显示正常，通过 `stats.likes`/`stats.dislikes` 读取

### Task 2: 用户端创建/更新帖子支持 body 和 media
- **ACTION**: 扩展前端 `createForumPost` 和 `updateForumPost` 函数签名，添加 `body` 和 `media` 参数；同步更新 `ForumEditorView.vue` 表单提交逻辑。
- **IMPLEMENT**:
  - `createForumPost(input: { title, excerpt, communityId, tags?, flairType?, flairLabel?, body?, media? })`
  - `updateForumPost(postId, input: { title?, excerpt?, tags?, flairType?, flairLabel?, isPinned?, isLocked?, body?, media? })`
  - 更新 `ForumEditorView.vue` 表单 state 和 submit handler
- **MIRROR**: NAMING_CONVENTION (optional params with `?`)
- **IMPORTS**: `ForumPostMedia` from `@/types/forum` if used
- **GOTCHA**: 当前 UI 可能只支持 excerpt，添加 body 输入框需确认 UI 设计是否支持；media 上传可能依赖未实现的文件上传组件
- **VALIDATE**: 创建帖子时 body 字段正确提交到后端，后端 `CreatePostDTO` 接收并保存

### Task 3: 用户端添加 join/leave community API
- **ACTION**: 在 `console/src/api/forum.ts` 添加 `joinForumCommunity` 和 `leaveForumCommunity` 函数。
- **IMPLEMENT**:
  ```typescript
  export async function joinForumCommunity(id: string): Promise<void> {
    await apiPost(`/forum/communities/${id}/join`)
  }
  export async function leaveForumCommunity(id: string): Promise<void> {
    await apiPost(`/forum/communities/${id}/leave`)
  }
  ```
- **MIRROR**: NAMING_CONVENTION, ERROR_HANDLING
- **IMPORTS**: No new imports
- **GOTCHA**: 后端端点已存在（`ForumController.java:347-375`），只需前端调用
- **VALIDATE**: 在社区详情页点击加入/离开按钮，网络请求返回 200，无 404

### Task 4: 用户端评论类型对齐与字段消费
- **ACTION**: 修改 `console/src/types/forum.ts` 中 `ForumComment` 接口，将投票相关字段标记为可选（后端暂无数据），添加后端已有但前端未消费的字段；更新 `ForumThreadView.vue` 展示编辑时间和回复数。
- **IMPLEMENT**:
  - `ForumComment`：保留 `upvotes`/`likes`/`dislikes`/`userVote` 但改为可选
  - `ForumComment`：添加 `markdown?: string`, `editedAt?: string`, `isFlagged?: boolean`, `replyCount?: number`
  - 更新 `ForumThreadView.vue` 或评论组件，展示 `editedAt`（如"已编辑"标签）和 `replyCount`
- **MIRROR**: TypeScript optional field pattern (`?:`)
- **IMPORTS**: No new imports
- **GOTCHA**: `replyCount` 与 `replies?.length` 可能同时存在，UI 需决定显示哪个
- **VALIDATE**: 评论列表渲染正常，无 TypeScript 类型错误

### Task 5: 管理端 AdminForumPostVO 扁平字段转换
- **ACTION**: 在 `management/src/api/admin/forum.ts` 的 `getPosts` 和 `getPostDetail` 中添加响应转换逻辑，将后端扁平字段转换为前端嵌套对象。
- **IMPLEMENT**:
  ```typescript
  // In getPosts response mapping
  return {
    ...response,
    items: response.items.map(post => ({
      ...post,
      author: {
        id: post.userId,
        username: post.username ?? post.userId,
        avatar: post.avatar
      },
      community: {
        id: post.communityId,
        name: post.communityName ?? 'Unknown',
        slug: post.communitySlug ?? ''
      }
    }))
  }
  ```
- **MIRROR**: 用户端 `fetchForumPosts` 转换模式
- **IMPORTS**: No new imports
- **GOTCHA**: `columns.ts` 和 `ForumPostDetailDrawer.vue` 当前使用 `post.author?.username` 和 `post.community?.name`，转换后正常可用，但需确认无其他直接访问扁平字段的代码
- **VALIDATE**: 管理端帖子列表正确显示作者名和社区名，无 undefined fallback

### Task 6: 管理端添加 flag/unflag 后端端点
- **ACTION**: 在 `AdminForumController.java` 添加 `flagPost` 和 `unflagPost` 端点；在 `AdminForumService.java` 添加业务逻辑；创建 `FlagPostRequest.java` DTO。
- **IMPLEMENT**:
  ```java
  @PostMapping("/posts/{id}/flag")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  public Result<Void> flagPost(@PathVariable String id, @RequestBody FlagPostRequest request) {
      adminForumService.flagPost(id, request.getReason());
      return Result.success();
  }

  @PostMapping("/posts/{id}/unflag")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  public Result<Void> unflagPost(@PathVariable String id) {
      adminForumService.unflagPost(id);
      return Result.success();
  }
  ```
  ```java
  @Data
  public class FlagPostRequest {
      private String reason;
  }
  ```
- **MIRROR**: REPOSITORY_PATTERN (controller → service), SERVICE_PATTERN
- **IMPORTS**: `FlagPostRequest` import
- **GOTCHA**: 需确认 `AdminForumService` 是否有现有的 flag 逻辑或需要新增；数据库表 `forum_post` 需已有 `is_flagged`, `flagged_reason`, `flagged_at` 字段（分析文档显示 VO 有这些字段）
- **VALIDATE**: 前端调用 flag/unflag 返回 200，不再返回 404

### Task 7: 管理端添加 isDeleted 查询参数和筛选器
- **ACTION**: 在 `management/src/api/admin/forum.ts` 的 `ForumPostQueryParams` 添加 `isDeleted`；在 `ForumPostsListView.vue` 工具栏添加对应筛选器。
- **IMPLEMENT**:
  ```typescript
  export interface ForumPostQueryParams {
    // ... existing fields ...
    isDeleted?: boolean
  }
  ```
  - 在帖子列表查询表单添加 `isDeleted` switch/checkbox
  - 传给 `forumApi.getPosts(params)`
- **MIRROR**: Existing query params pattern in `management/src/api/admin/forum.ts`
- **IMPORTS**: No new imports
- **GOTCHA**: 后端 `AdminForumPostQueryDTO` 已支持 `isDeleted`，只需前端传参
- **VALIDATE**: 筛选"已删除"只显示软删除的帖子，筛选"未删除"只显示正常帖子

### Task 8: 管理端帖子列表 columns 和 Drawer 适配
- **ACTION**: 检查并修复 `management/src/views/forum/columns.ts` 和 `ForumPostDetailDrawer.vue` 中对 `author`/`community` 嵌套对象的访问，确保与 Task 5 的转换逻辑兼容。
- **IMPLEMENT**:
  - `columns.ts`: 确认使用 `post.author?.username` 和 `post.community?.name`
  - `ForumPostDetailDrawer.vue`: 确认使用嵌套对象访问
  - 如有直接访问扁平字段的地方，改为嵌套对象访问
- **MIRROR**: Existing column definition pattern
- **IMPORTS**: No new imports
- **GOTCHA**: 某些地方可能直接访问 `post.username` 而非 `post.author?.username`，需统一
- **VALIDATE**: 帖子列表和详情 Drawer 正确显示作者和社区信息

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| fetchForumPosts 转换 | 后端返回扁平字段 | 前端得到嵌套 author/community 对象 | 是（字段缺失时 fallback） |
| joinForumCommunity | communityId | POST /forum/communities/{id}/join 被调用 | 否 |
| flagPost | postId, reason | POST /admin/forum/posts/{id}/flag 被调用 | 否 |
| AdminForumPostVO 转换 | 后端返回扁平字段 | 前端得到嵌套 author/community 对象 | 是（null 值处理） |

### Edge Cases Checklist
- [ ] 后端返回的 authorUsername 为 null → fallback 到 userId
- [ ] 后端返回的 communityName 为 null → fallback 到 "Unknown"
- [ ] 用户未登录时调用 join/leave → 后端返回 401/403
- [ ] flagPost 传入空 reason → 后端允许或拒绝（需确认业务逻辑）
- [ ] stats 对象字段缺失 → 前端显示 0 或隐藏
- [ ] media 参数为空数组 → 后端正确处理

---

## Validation Commands

### Static Analysis
```bash
# Frontend type check (console)
cd console && pnpm type-check
```
EXPECT: Zero type errors

```bash
# Frontend type check (management)
cd management && pnpm type-check
```
EXPECT: Zero type errors

### Backend Compilation
```bash
cd backend-spring && ./mvnw compile -DskipTests
```
EXPECT: Build success

### Unit Tests
```bash
# Frontend tests (console)
cd console && pnpm test

# Frontend tests (management)
cd management && pnpm test

# Backend tests
./mvnw test
```
EXPECT: All tests pass

### Manual Validation
- [ ] 用户端帖子列表加载正常，author/community 信息正确
- [ ] 用户端创建帖子支持提交 body 字段
- [ ] 用户端社区详情页 join/leave 按钮正常工作
- [ ] 用户端评论列表显示编辑时间和回复数
- [ ] 管理端帖子列表正确显示作者名和社区名
- [ ] 管理端标记帖子功能正常（无 404）
- [ ] 管理端可按已删除状态筛选帖子
- [ ] 管理端帖子详情 Drawer 正确展示信息

---

## Acceptance Criteria
- [ ] 所有 8 处差异修复完毕
- [ ] 用户端和管理端前端 type-check 通过
- [ ] 后端编译通过
- [ ] 所有相关单元测试通过
- [ ] 无新增 lint 错误
- [ ] 手动验证清单全部完成
- [ ] 管理端 flag/unflag 不再返回 404
- [ ] 用户端 join/leave community API 可用

## Completion Checklist
- [ ] Code follows discovered patterns
- [ ] Error handling matches codebase style
- [ ] Logging follows codebase conventions
- [ ] Tests follow test patterns
- [ ] No hardcoded values
- [ ] Documentation updated (analysis doc 可标记为已修复)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 前端组件直接访问已移除的 `post.likes` | 高 | UI 报错 | 全局搜索 `\.likes` 和 `\.dislikes` 引用，同步修改 |
| AdminForumService flag 逻辑依赖缺失的数据库字段 | 中 | 编译/运行错误 | 先检查实体类是否有 isFlagged/flaggedReason/flaggedAt 字段 |
| body/media 添加后前端 UI 未设计对应输入 | 中 | 功能不可用 | 与产品确认 UI 设计，或先添加参数支持但隐藏输入 |
| 多处文件修改引入回归 bug | 中 | 功能异常 | 逐任务验证，每完成一个任务运行对应测试 |

## Notes
- 分析文档推荐"方案 B"（前端适配后端扁平结构），本计划遵循此建议，不修改后端 VO
- 后端 `ForumPostVO.stats` 为 `Object` 类型，建议后端未来显式定义为 `ForumPostStatsVO`，但不在本计划范围内
- 管理端 `ForumPostQueryParams` 当前无 `isDeleted`，添加后需同步更新 UI 筛选器
- 评论投票字段（upvotes/likes/dislikes/userVote）后端暂无对应数据，前端仅调整为可选类型，不实现后端逻辑
