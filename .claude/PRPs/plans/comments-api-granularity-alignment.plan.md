# Plan: Comments 前后端 API 颗粒度对齐（剩余差异修复）

## Summary
修复 Comments 模块剩余的前后端 API 颗粒度差异：P0 — 后端不支持 parentEntityId 过滤导致 CommentsTab 显示所有评论而非当前帖子的；P1 — AdminCommentQueryDTO 缺少 sortBy/sortOrder；P2 — Solution 评论字段名不一致和缺 @CheckBan；P3 — 内容长度限制不一致。

**已修复差异（4/8）**：AuthorInfo 嵌套结构、flag/unflag 返回类型、路由 :type/:id 动态化、deletedFilter。

## User Story
As a 管理员, I want 帖子详情页的 CommentsTab 仅显示当前帖子的评论而非所有论坛评论, so that 我可以聚焦审核特定帖子的评论内容。

## Problem → Solution
**Current state**: CommentsTab 传入 parentEntityId 但后端 AdminCommentQueryDTO 不接收此参数，查询不追加过滤条件，导致显示所有论坛评论。前端 sortBy/sortOrder 被后端静默忽略。Solution 评论字段名不一致需要前端 fallback 映射。
**Desired state**: 后端 AdminCommentQueryDTO 新增 parentEntityId/sortBy/sortOrder 字段，查询方法追加对应过滤和排序条件。Solution 评论字段名统一或前端映射消除歧义。Solution createComment 添加 @CheckBan。

## Metadata
- **Complexity**: Medium
- **Source PRD**: docs/comments-api-granularity-analysis.md
- **Estimated Files**: 8-12

---

## UX Design

### Before
```
┌─────────────────────────────────────────┐
│ 帖子详情页 CommentsTab:                  │
│ 显示所有论坛评论(包括其他帖子的)          │  ← 无效过滤
│                                         │
│ 评论列表排序: 固定顺序                    │  ← sortBy 被忽略
└─────────────────────────────────────────┘
```

### After
```
┌─────────────────────────────────────────┐
│ 帖子详情页 CommentsTab:                  │
│ 仅显示当前帖子的评论                      │  ← 按 postId 过滤
│                                         │
│ 评论列表排序: 按创建时间/更新时间         │  ← sortBy/sortOrder 生效
└─────────────────────────────────────────┘
```

---

## Mandatory Reading

| Priority | File | Why |
|---|---|---|
| P0 | `backend-spring/.../admin/dto/AdminCommentQueryDTO.java` | 需新增 3 个字段 |
| P0 | `backend-spring/.../admin/service/impl/AdminCommentServiceImpl.java` | 查询方法需追加过滤和排序 |
| P1 | `management/src/api/admin/comments.ts` | CommentQueryParams 已定义 sortBy/sortOrder/parentEntityId |
| P2 | `backend-spring/.../solution/service/impl/SolutionServiceImpl.java` | createComment 需加 @CheckBan |

---

## Patterns to Mirror

### QUERY_DTO_PATTERN
// SOURCE: AdminCommentQueryDTO.java
```java
@Data
public class AdminCommentQueryDTO {
    private String search;
    private String type;
    private Boolean isFlagged;
    private Boolean isDeleted;
    private Integer page = 1;
    private Integer limit = 10;
}
```

### QUERY_BUILD_PATTERN
// SOURCE: AdminCommentServiceImpl.java — LambdaQueryWrapper 链式调用
```java
// 现有过滤模式
wrapper.eq(ForumComment::getIsDeleted, false);
if (query.getIsFlagged() != null) {
    wrapper.eq(ForumComment::getIsFlagged, query.getIsFlagged());
}
```

### FRONTEND_QUERY_PARAMS
// SOURCE: management/src/api/admin/comments.ts
```typescript
export interface CommentQueryParams {
  page?: number
  limit?: number
  search?: string
  type?: CommentType
  isFlagged?: boolean
  isDeleted?: boolean
  parentEntityId?: string   // ← 后端不接收
  sortBy?: string           // ← 后端不接收
  sortOrder?: 'asc' | 'desc' // ← 后端不接收
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend/.../admin/dto/AdminCommentQueryDTO.java` | UPDATE | 新增 parentEntityId、sortBy、sortOrder 字段 |
| `backend/.../admin/service/impl/AdminCommentServiceImpl.java` | UPDATE | 追加 parentEntityId 过滤和 sortBy/sortOrder 排序 |
| `backend/.../solution/service/impl/SolutionServiceImpl.java` | UPDATE | createComment 添加 @CheckBan |
| `management/src/views/forum/components/CommentsTab.vue` | UPDATE | 验证 parentEntityId 传递（已存在，需验证后端接收） |
| `console/src/components/comments/comment-tree-builder.ts` | UPDATE | 统一 content/body 映射 |

---

## NOT Building

- 评论分页（差异 #7，长期规划，单独排期）
- ForumComment 类型扁平+嵌套并存（差异 #1，设计权衡，当前不修复）
- 内容长度限制统一（差异 #3，低优先级 UX 优化）
- 统一 Forum/Solution 评论 Schema（长期重构）

---

## Step-by-Step Tasks

---

### TASK-001: [Backend] AdminCommentQueryDTO 新增 parentEntityId/sortBy/sortOrder

- **ACTION**: 在 `AdminCommentQueryDTO` 中新增 3 个可选字段
- **IMPLEMENT**:
  1. 新增 `private String parentEntityId;` — 用于按父实体（postId 或 solutionId）过滤
  2. 新增 `private String sortBy;` — 排序字段，默认 "createdAt"，可选值：createdAt、updatedAt
  3. 新增 `private String sortOrder;` — 排序方向，默认 "desc"，可选值：asc、desc
- **GOTCHA**: 字段均为可选，null 时不过滤/不排序，保持向后兼容
- **VALIDATE**: `./mvnw compile` 通过；前端传 parentEntityId=xxx 时后端接收该参数

---

### TASK-002: [Backend] AdminCommentServiceImpl 追加 parentEntityId 过滤和排序

- **ACTION**: 在查询方法中追加 parentEntityId 条件和 sortBy/sortOrder 排序
- **IMPLEMENT**:
  1. `getForumComments()` 方法：当 `query.getParentEntityId() != null` 时追加 `.eq(ForumComment::getPostId, query.getParentEntityId())`
  2. `getSolutionComments()` 方法：当 `query.getParentEntityId() != null` 时追加 `.eq(SolutionComment::getSolutionId, query.getParentEntityId())`
  3. `getForumCommentsAsFallback()` 方法：同样追加 parentEntityId 条件
  4. 所有查询方法追加排序：`wrapper.orderBy(true, isAsc, sortColumn)` 其中 sortColumn 和 isAsc 由 sortBy/sortOrder 决定
  5. sortBy 默认值 "createdAt" → 对应 ForumComment::getCreatedAt / SolutionComment::getCreatedAt
  6. sortOrder 默认值 "desc" → isAsc = false
- **GOTCHA**: `getForumCommentsAsFallback()` 是 type 未指定时的 fallback，必须也追加 parentEntityId 条件
- **VALIDATE**: `GET /admin/comments?type=forum&parentEntityId=post123` 仅返回 postId=post123 的评论

---

### TASK-003: [Backend] SolutionServiceImpl.createComment 添加 @CheckBan

- **ACTION**: 在 `SolutionServiceImpl.createComment()` 方法上添加 `@CheckBan` 注解
- **IMPLEMENT**:
  1. 在 createComment 方法签名上方添加 `@CheckBan` 注解
  2. 确认 import `com.ulticode.common.annotation.CheckBan`
- **MIRROR**: 参考 `ForumCommentServiceImpl.java` 的 `@CheckBan` 使用模式
- **GOTCHA**: `@CheckBan` 是 AOP 注解，需确认 CheckBanAspect 切点表达式覆盖 solution 包路径
- **VALIDATE**: 被封禁用户调用 `POST /api/solutions/{id}/comments` 返回 403

---

### TASK-004: [Frontend-Console] 统一 comment-tree-builder content/body 映射

- **ACTION**: 确保 comment-tree-builder.ts 中两种评论类型的字段映射一致
- **IMPLEMENT**:
  1. 检查 `mapToComment` 函数中的 `input.body || input.content` fallback
  2. 如果 ForumComment 类型中 body 字段已统一改为 content，移除 fallback
  3. 如果两种类型仍使用不同字段名，保留 fallback 并添加类型注释说明
- **GOTCHA**: 不改变后端 ForumComment 的 body 字段名（这涉及更大范围重构），仅在前端映射层确保正确
- **VALIDATE**: 论坛评论和题解评论内容均正确显示

---

### TASK-005: [Frontend-Admin] 验证 CommentsTab parentEntityId 传递生效

- **ACTION**: 验证 CommentsTab 传入的 parentEntityId 参数现在被后端正确接收和过滤
- **IMPLEMENT**:
  1. 确认 CommentsTab.vue 中 `commentsStore.fetchComments({ type: 'forum', parentEntityId: props.postId })` 调用不变
  2. 确认 comments store 的 fetchComments 方法正确传递 parentEntityId 到 API
  3. 确认 commentsApi.getComments 的 params 中包含 parentEntityId
- **GOTCHA**: TASK-001 和 TASK-002 必须先完成，否则此验证会失败
- **VALIDATE**: 帖子 A 详情页的 CommentsTab 仅显示帖子 A 的评论

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output |
|---|---|---|
| getComments with parentEntityId=forum | type=forum, parentEntityId="post1" | 仅返回 postId="post1" 的评论 |
| getComments with parentEntityId=solution | type=solution, parentEntityId="sol1" | 仅返回 solutionId="sol1" 的评论 |
| getComments with sortBy=createdAt, sortOrder=asc | 排序参数 | 评论按 createdAt 升序排列 |
| getComments with null parentEntityId | 无过滤参数 | 返回所有评论（向后兼容） |
| @CheckBan on solution createComment | 被封禁用户 | BusinessException thrown |

### Edge Cases
- parentEntityId 为空字符串 → 不追加过滤条件
- sortBy 为非法值 → fallback 到默认 createdAt
- sortOrder 为非法值 → fallback 到默认 desc

---

## Validation Commands

```bash
# 后端编译
cd backend-spring && ./mvnw compile -q

# 前端类型检查
cd management && pnpm type-check
cd console && pnpm type-check

# 测试
cd backend-spring && ./mvnw test -q
```

浏览器验证:
- 帖子详情页 CommentsTab 仅显示当前帖子评论
- 评论列表排序功能正常

---

## Acceptance Criteria
- [ ] `GET /admin/comments?type=forum&parentEntityId=xxx` 仅返回指定帖子的评论
- [ ] `GET /admin/comments?sortBy=createdAt&sortOrder=asc` 返回按创建时间升序排列的评论
- [ ] 被封禁用户无法创建 Solution 评论（返回 403）
- [ ] 后端编译通过，前端类型检查通过
- [ ] CommentsTab 仅显示当前帖子评论

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| parentEntityId 过滤导致 fallback 查询行为变化 | Low | Medium | 仅在 parentEntityId 非 null 时追加条件，null 时行为不变 |
| sortBy 非法值导致 SQL 异常 | Low | High | 校验 sortBy 仅允许 createdAt/updatedAt，非法值 fallback |
| @CheckBan AOP 未覆盖 solution 包 | Low | Medium | 验证 CheckBanAspect 切点表达式 |