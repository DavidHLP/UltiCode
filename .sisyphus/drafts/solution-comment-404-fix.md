# Draft: Solution Comment 404 错误分析

## 问题描述
前端请求 `GET /admin/solutions/comment-005` 返回 404 (Not Found)

## 错误链
1. `SolutionDetailView.vue:49` - `fetchSolution(solutionId.value)`
2. `stores/admin/solutions.ts:62` - `solutionsApi.getSolution(id)`
3. `api/admin/solutions.ts:92` - `apiGet(`/admin/solutions/${id}`)`
4. 后端 `AdminSolutionController.getSolution(@PathVariable String id)` - 找不到 ID 为 `comment-005` 的 solution

## 根因分析
问题出在 `ModerationQueueView.vue` 第 69-78 行的 `viewEntity` 函数：

```typescript
const routes: Record<ModeratableEntityType, string> = {
  forum_post: `/forum/posts/${item.entityId}`,
  forum_comment: `/forum/comments/${item.entityId}`,
  solution: `/solutions/${item.entityId}`,
  solution_comment: `/solutions/${item.entityId}`,  // ❌ BUG: 把评论ID当成题解ID
  problem: `/problems/${item.entityId}`,
}
router.push(routes[item.entityType])
```

当 entityType 是 `solution_comment` 时，系统把评论ID（如 `comment-005`）直接当作 solution ID 使用，
跳转到了 `/solutions/comment-005`，但后端 `/admin/solutions/{id}` 只接受 solution ID（如 `sol-001`）。

从数据库迁移文件 V9 可知：
- `comment-005` 是 `solution_comments` 表的记录
- 它所属的 solution 是 `sol-002`
- 后端 AdminSolutionController 只查询 `solutions` 表，不查 `solution_comments` 表

## 修复方案

### 方案1（推荐）：后端返回 parentEntityId
1. 修改后端 `ModerationQueueItem` VO，添加 `parentEntityId` 字段
2. 对于 `solution_comment` 类型，返回其所属的 `solution_id`
3. 前端修改路由映射，使用 `parentEntityId` 跳转

### 方案2：前端调用API获取评论详情
1. 前端在点击时，先调用 `/solutions/comments/{id}` 获取评论详情
2. 从详情中提取 `solution_id`
3. 再跳转到 `/solutions/{solution_id}`

### 方案3：禁用 solution_comment 的查看链接
1. 对于 `solution_comment` 类型，不显示"查看实体"按钮或禁用
2. 添加提示说明需要从题解列表查看

## 相关文件
- `management/src/views/moderation/ModerationQueueView.vue` - 路由映射
- `management/src/api/admin/moderation.ts` - ModerationQueueItem 类型定义
- `management/src/views/solutions/SolutionDetailView.vue` - 详情页
- `management/src/api/admin/solutions.ts` - API 调用
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSolutionController.java` - 后端 Controller
