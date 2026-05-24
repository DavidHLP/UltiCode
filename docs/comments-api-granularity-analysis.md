# Comments 前后端 API 颗粒度对齐分析（更新版）

> 生成日期: 2026-05-23

## 关键变化（与旧分析文档对比）

1. **差异 #3 已修复** — AdminCommentVO 已使用嵌套 AuthorInfo record：
   ```java
   public record AdminCommentVO(
       ...
       AuthorInfo author,   // ✅ 已改为嵌套结构
       ...
   ) {
       public record AuthorInfo(String id, String username, String avatar) {}
   }
   ```
   前端 Comment interface 也已对齐为 `author: { id, username, avatar }`。作者信息结构差异已解决。

2. **差异 #4 已修复** — 后端 flagComment 和 unflagComment 现在返回 `Result<AdminCommentVO>` 而非 `Result<Void>`：
   ```java
   // AdminCommentController.java:55
   public Result<AdminCommentVO> flagComment(...)   // ✅ 返回完整 VO
   public Result<AdminCommentVO> unflagComment(...) // ✅ 返回完整 VO
   ```
   前端 `commentsApi.flagComment` 和 `unflagComment` 声明返回 `Promise<Comment>`，与后端一致。flag/unflag 返回类型差异已解决。

3. **差异 #6 已修复** — 路由已改为 `/comments/:type/:id`：
   ```typescript
   // router/index.ts:205
   path: 'comments/:type/:id',
   name: 'comment-detail',
   ```
   CommentDetailView.vue 从路由参数获取 type：
   ```typescript
   const commentType = computed((): CommentType => {
     return (route.params.type as CommentType) || 'forum'
   })
   ```
   CommentDetailView 硬编码问题已解决（有 fallback 到 'forum'）。

4. **差异 #10 已修复** — CommentsListView.vue 已包含 deletedFilter：
   ```typescript
   // CommentsListView.vue:79
   deletedFilter: ref<string>('all')
   // toolbarFilters 包含删除状态选项
   ```

5. **差异 #5 仍存在** — CommentsTab.vue 传入 `parentEntityId: props.postId`，但后端 AdminCommentQueryDTO **没有 parentEntityId 字段**，且 mapper 查询不支持按 postId 过滤。CommentsTab 仍然显示所有论坛评论而非当前帖子的评论。

6. **新发现差异** — AdminCommentQueryDTO 缺少 `sortBy` 和 `sortOrder` 字段，但前端 CommentQueryParams 定义包含这两个字段。后端查询不支持排序。

---

## 当前剩余差异汇总

| 优先级 | 差异 | 描述 | 状态 |
|--------|------|------|------|
| P0 | #5 | CommentsTab 传入 parentEntityId 但后端不支持按 postId/solutionId 过滤 | 未修复 |
| P1 | #2 | Solution 评论字段名不一致（content vs body） | 未修复（console 端通过 comment-tree-builder 映射） |
| P1 | 新 | AdminCommentQueryDTO 缺少 sortBy/sortOrder，前端定义了但无法传递 | 新发现 |
| P2 | #1 | ForumComment 类型扁平+嵌套并存 | 未修复（设计权衡） |
| P2 | #7 | 评论无分页（用户端） | 未修复（低优先级） |
| P2 | #8 | Solution 评论缺 @CheckBan | 未修复 |
| P3 | #9 | 内容长度限制不一致 | 未修复 |

---

## Pages/接口 颗粒度对照表

| 页面 | 前端组件 | 前端 API | 后端端点 | 颗粒度对齐 |
|------|----------|----------|----------|-----------|
| /comments | CommentsListView.vue | GET /admin/comments | AdminCommentController.getComments | ✅ DTO 已对齐 |
| /comments/:type/:id | CommentDetailView.vue | GET /admin/comments/{type}/{id} | AdminCommentController.getComment | ✅ DTO 已对齐 |
| /comments (flag) | CommentsListView.vue | PATCH /admin/comments/{type}/{id}/flag | AdminCommentController.flagComment | ✅ 返回 AdminCommentVO |
| /comments (unflag) | CommentsListView.vue | PATCH /admin/comments/{type}/{id}/unflag | AdminCommentController.unflagComment | ✅ 返回 AdminCommentVO |
| /comments (delete) | CommentsListView.vue | DELETE /admin/comments/{type}/{id} | AdminCommentController.deleteComment | ✅ 返回 Result<Void> |
| /comments (bulk) | CommentsListView.vue | POST /admin/comments/bulk | AdminCommentController.bulkAction | ✅ |
| Forum Post 详情页 | CommentsTab.vue | GET /admin/comments (with parentEntityId) | AdminCommentController.getComments | ❌ 后端不支持 parentEntityId 过滤 |

---

## 最关键的未修复问题

### P0: CommentsTab parentEntityId 过滤缺失

CommentsTab.vue:62-63 传入：
```typescript
await commentsStore.fetchComments({
  type: 'forum',
  parentEntityId: props.postId,  // ← 传了但后端忽略
  page: ...,
  limit: ...,
})
```

但 AdminCommentQueryDTO 没有 parentEntityId 字段，后端查询不追加 postId 过滤条件，结果是 CommentsTab 显示所有论坛评论而非当前帖子的评论。

### P1: AdminCommentQueryDTO 缺少 sortBy/sortOrder

前端 CommentQueryParams 定义：
```typescript
export interface CommentQueryParams {
  ...
  sortBy?: string       // ← 后端不支持
  sortOrder?: 'asc' | 'desc'  // ← 后端不支持
}
```

后端 AdminCommentQueryDTO 只有 search、type、isFlagged、isDeleted、page、limit。前端传 sortBy/sortOrder 时后端会忽略。

### P2: Solution 评论字段名不一致（content vs body）

ForumComment 使用 `body` 字段名，SolutionComment 使用 `content` 字段名。Console 端 comment-tree-builder.ts 使用 `input.body || input.content` fallback 映射，但类型定义仍存在歧义。