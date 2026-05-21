# Solutions 管理页面前后端 API 颗粒度对齐分析报告

> 分析范围：`management/src/views/solutions/` ↔ `backend-spring/src/main/java/com/ulticode/modules/admin/solution/`
> 分析日期：2026-05-21

---

## 1. 执行摘要

Solutions 管理页面的前后端接口在**路径定义和字段映射上基本对齐**，但存在**列表页过度获取（Over-fetching）**和**软删除过滤逻辑未实现**两个显著的颗粒度不对齐问题。

| 维度 | 状态 | 说明 |
|------|------|------|
| API 路径 | 对齐 | 7 个端点路径完全匹配 |
| 查询参数 | 基本对齐 | 字段名一致，`problemId` 类型有差异（TS `number` vs Java `Long`） |
| 分页结构 | 对齐 | `PageResult` 字段完全匹配 |
| 响应字段 | 基本对齐 | 字段一一对应，但列表页包含未使用的大字段 |
| 业务逻辑 | 不对齐 | `isDeleted` 过滤条件后端未正确实现 |

---

## 2. 接口路径对齐详情

| 前端 API (`solutionsApi`) | 后端 Controller | 方法 | 状态 |
|---------------------------|-----------------|------|------|
| `GET /admin/solutions` | `@GetMapping` | `getSolutions` | 对齐 |
| `GET /admin/solutions/flagged` | `@GetMapping("/flagged")` | `getFlaggedSolutions` | 对齐 |
| `GET /admin/solutions/:id` | `@GetMapping("/{id}")` | `getSolution` | 对齐 |
| `POST /admin/solutions/:id/flag` | `@PostMapping("/{id}/flag")` | `flagSolution` | 对齐 |
| `POST /admin/solutions/:id/unflag` | `@PostMapping("/{id}/unflag")` | `unflagSolution` | 对齐 |
| `DELETE /admin/solutions/:id` | `@DeleteMapping("/{id}")` | `deleteSolution` | 对齐 |
| `POST /admin/solutions/bulk` | `@PostMapping("/bulk")` | `bulkAction` | 对齐 |

---

## 3. 查询参数对齐详情

### 前端 `SolutionQueryParams`
```typescript
interface SolutionQueryParams {
  search?: string
  problemId?: number
  userId?: string
  isFlagged?: boolean
  isPublished?: boolean
  isDeleted?: boolean
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}
```

### 后端 `AdminSolutionQueryDTO`
```java
public class AdminSolutionQueryDTO {
    private String search;
    private Long problemId;        // 类型差异：Long vs number
    private String userId;
    private Boolean isFlagged;
    private Boolean isPublished;
    private Boolean isDeleted;     // 未正确实现过滤逻辑
    private Integer page = 1;
    private Integer limit = 10;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
}
```

### 问题点
- `problemId`：前端为 `number`，后端为 `Long`。JSON 序列化后无问题，但类型语义不一致。
- `isDeleted`：后端 `getSolutions()` 中对该参数的处理逻辑不完整（见第 5 节）。

---

## 4. 响应数据结构对齐详情

### 分页结构（完全对齐）

| 字段 | 前端 `PageResult<T>` | 后端 `PageResult<T>` | 状态 |
|------|----------------------|----------------------|------|
| 数据列表 | `items: T[]` | `List<T> items` | 对齐 |
| 总数 | `total: number` | `Long total` | 对齐（JSON 中均为数字） |
| 当前页 | `page: number` | `Integer page` | 对齐 |
| 每页条数 | `pageSize: number` | `Integer pageSize` | 对齐 |
| 总页数 | `totalPages: number` | `Integer totalPages` | 对齐 |

### 数据字段（基本对齐）

| 字段 | 前端 `Solution` | 后端 `AdminSolutionVO` | 列表页使用 | 详情页使用 |
|------|-----------------|------------------------|------------|------------|
| `id` | `string` | `String` | 是 | 是 |
| `problemId` | `number` | `Long` | 否 | 否 |
| `userId` | `string` | `String` | 否 | 否 |
| `title` | `string` | `String` | 是 | 是 |
| `content` | `string` | `String` | **否** | 是 |
| `summary` | `string?` | `String` | 否 | 否 |
| `language` | `string` | `String` | 否 | 否 |
| `tags` | `string?` | `String` | 否 | 否 |
| `views` | `number` | `Integer` | 是 | 是 |
| `isPublished` | `boolean` | `Boolean` | 是（状态徽章） | 是 |
| `publishedAt` | `string?` | `LocalDateTime` | 否 | 否 |
| `publishedBy` | `string?` | `String` | 否 | 否 |
| `isFlagged` | `boolean` | `Boolean` | 是（状态徽章） | 是 |
| `flaggedReason` | `string?` | `String` | 否 | 否 |
| `flaggedAt` | `string?` | `LocalDateTime` | 否 | 否 |
| `isDeleted` | `boolean` | `Boolean` | 是（状态徽章） | 是 |
| `deletedAt` | `string?` | `LocalDateTime` | 否 | 否 |
| `deletedBy` | `string?` | `String` | 否 | 否 |
| `createdAt` | `string` | `LocalDateTime` | 是 | 是 |
| `updatedAt` | `string` | `LocalDateTime` | 否 | 否 |
| `author` | `{id,username,name,email?}` | `AuthorInfo` | 是（仅 username） | 是 |
| `problem` | `{id,slug,title,difficulty}` | `ProblemInfo` | 是（仅 title） | 是 |

---

## 5. 颗粒度不对齐问题（按严重程度排序）

### 问题 1：列表页过度获取（Over-fetching）— HIGH

**现象**：
- `SolutionsListView.vue`（列表页）调用 `GET /admin/solutions`，后端返回 `AdminSolutionVO` 数组。
- 列表页 `columns.ts` 实际渲染的字段仅有：`id`, `title`, `author.username`, `problem.title`, `isFlagged/isDeleted/isPublished`（状态徽章）、`views`, `createdAt`。
- 但后端返回的 VO 包含完整的 **`content`**（题解正文，markdown 格式，通常很长）。

**影响**：
- 网络传输量显著增加，尤其是当单条 `content` 较大（如包含代码块、详细解释）时。
- 后端数据库查询也拉取了不必要的 `content` 字段。

**建议**：
- 为列表页创建精简 VO（如 `AdminSolutionListItemVO`），排除 `content`、`summary`、`tags` 等大字段。
- 详情页继续使用完整的 `AdminSolutionVO`。

```java
// 建议新增
@Data
public class AdminSolutionListItemVO {
    private String id;
    private String title;
    private String language;
    private Integer views;
    private Boolean isPublished;
    private Boolean isFlagged;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private AuthorInfo author;
    private ProblemInfo problem;
    // 不包含 content, summary, tags, publishedAt, flaggedReason 等
}
```

---

### 问题 2：`isDeleted` 过滤条件后端未正确实现 — MEDIUM

**现象**：
- 前端 `SolutionQueryParams` 支持 `isDeleted?: boolean` 过滤。
- 后端 `AdminSolutionServiceImpl.getSolutions()` 中对 `isDeleted` 的处理：
  ```java
  if (query.getIsDeleted() != null && query.getIsDeleted()) {
      // Include soft-deleted - need to use native query or disable logic delete
      // For now, we'll just not filter and let MyBatis-Plus handle it
  }
  ```
- 由于 `Solution` entity 使用了 `@TableLogic`，MyBatis-Plus 默认会排除 `is_deleted = true` 的记录，导致即使前端传 `isDeleted=true`，也无法查询到已软删除的记录。

**影响**：
- 前端过滤功能不完整，用户无法查看已删除的题解。

**建议**：
- 当 `isDeleted=true` 时，使用 MyBatis-Plus 的 `wrapper.eq(Solution::getIsDeleted, true)` 配合 `@TableLogic` 的自定义查询逻辑，或直接使用原生 SQL/ XML mapper 查询包含软删除记录的数据。

---

### 问题 3：`deletedAt` / `deletedBy` 字段前端未展示 — LOW

**现象**：
- 后端 `AdminSolutionVO` 返回 `deletedAt` 和 `deletedBy`。
- 前端列表页和详情页均未展示这两个字段（详情页 `SolutionDetailView.vue` 只展示了 status、author、views）。

**影响**：
- 数据颗粒度大于 UI 展示颗粒度，但无性能影响（因为问题 1 的 `content` 才是主要开销）。

**建议**：
- 在详情页的 Status Ticker 或信息卡片中补充 `deletedAt` / `deletedBy` 的展示（仅在 `isDeleted=true` 时）。
- 或者在精简列表 VO 中直接排除这两个字段。

---

### 问题 4：`getFlaggedSolutions` 端点冗余 — LOW

**现象**：
- 前端有独立的 `getFlaggedSolutions()` 方法，调用 `/admin/solutions/flagged`。
- 后端该端点本质上是强制 `isFlagged=true` 后调用 `getSolutions()`。
- 前端完全可以通过 `getSolutions({ isFlagged: true })` 实现相同功能。

**影响**：
- 维护成本略增（多一个端点需要维护）。

**建议**：
- 保留现状（不影响功能），或考虑未来合并为一个端点。

---

## 6. 正向实践

以下做法值得保持：

1. **批量加载避免 N+1**：`AdminSolutionServiceImpl.getSolutions()` 使用 `selectBatchIds` 批量查询 `user` 和 `problem`，避免了 N+1 查询问题。
2. **嵌套对象结构**：`author` 和 `problem` 使用嵌套对象返回，前端可以直接使用，无需二次查询。
3. **分页结构统一**：前后端使用一致的 `PageResult` 结构，包含 `items/total/page/pageSize/totalPages`。
4. **软删除支持**：Entity 使用 `@TableLogic` 实现软删除，VO 返回 `isDeleted` 标志供前端展示状态。

---

## 7. 修复优先级建议

| 优先级 | 问题 | 工作量 | 修复方案 |
|--------|------|--------|----------|
| P1 | 列表页过度获取 | 中 | 新增 `AdminSolutionListItemVO`，列表 API 返回精简结构 |
| P2 | `isDeleted` 过滤未实现 | 低 | 修复 `AdminSolutionServiceImpl` 中的 `isDeleted` 查询逻辑 |
| P3 | 详情页补充删除信息 | 低 | 在 `SolutionDetailView.vue` 中展示 `deletedAt`/`deletedBy` |
| P4 | `getFlaggedSolutions` 冗余 | 低 | 可选：合并到 `getSolutions` |
