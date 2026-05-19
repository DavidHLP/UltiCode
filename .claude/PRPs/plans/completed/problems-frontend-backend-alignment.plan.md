# Plan: Problems 前后端颗粒度对齐修复

## Summary
修复 `/problems` 管理模块中 10 个前后端对齐问题，涵盖 6 个缺失后端端点、排序/过滤功能失效、分页参数差异、批量操作路径不匹配等。按 P0→P3 优先级分 4 个阶段实施。

## User Story
作为管理员，我希望题目管理页面的所有筛选、排序、批量操作、标记/审核功能都能正常工作，以便高效管理平台题目。

## Problem → Solution
当前：排序/标签过滤/状态过滤/软删除过滤全部失效，6个后端端点缺失，批量操作404，提交数永远为0
→ 目标：所有前后端功能对齐，过滤器/排序/批量操作/标记审核/导入全部可用

## Metadata
- **Complexity**: XL
- **Source PRD**: `docs/alignment/problems-frontend-backend-alignment.md`
- **PRD Phase**: N/A
- **Estimated Files**: 25-30

---

## UX Design

### Before
```
┌─ Problems List ──────────────────────────────┐
│ Filters: [status▼] [difficulty▼] [tag▼]      │
│ Sort: [sortBy▼] [asc/desc]                   │
│ ┌──────────────────────────────────────────┐  │
│ │ ID  Title  Diff  Status  Subs  Flagged  │  │
│ │ 1   Foo    Easy  todo    0     —         │  │
│ │ 2   Bar    Hard  todo    0     —         │  │
│ └──────────────────────────────────────────┘  │
│ • 排序无效(始终按ID)                          │
│ • 标签/状态/已删除过滤无效                    │
│ • 提交数永远为0                               │
│ • 批量操作404                                 │
│ • 标记/审核按钮报错                           │
└──────────────────────────────────────────────┘
```

### After
```
┌─ Problems List ──────────────────────────────┐
│ Filters: [publishStatus▼] [difficulty▼] [tag▼]│
│ Sort: [sortBy▼] [asc/desc]  ← 实际生效       │
│ ┌──────────────────────────────────────────┐  │
│ │ ID  Title  Diff  PubStatus  Subs  Flag  │  │
│ │ 1   Foo    Easy  PUBLISHED  142   —      │  │
│ │ 2   Bar    Hard  DRAFT      38    🚩     │  │
│ └──────────────────────────────────────────┘  │
│ • 排序按选定字段生效                          │
│ • 标签/发布状态/已删除过滤全部可用             │
│ • 提交数从submission表查询                    │
│ • 批量操作正常工作                            │
│ • 标记/审核/批量审核可用                      │
└──────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Status 过滤器 | 发送 DRAFT/PUBLISHED/ARCHIVED (与后端不匹配) | 改为 publishStatus 过滤器，发送 isPublished 布尔值 | §4.5 |
| 排序下拉 | 无实际效果 | 按 title/difficulty/createdAt/updatedAt/submissionCount 排序 | §4.2 |
| 标签过滤 | 不工作 | 通过 JOIN problem_tag_relations 过滤 | §4.3 |
| 批量操作 | 404 | 前端路径改为 /admin/problems/bulk | §4.7 |
| Flag 按钮 | 后端 404 | 新增 flag/moderate/flagged/batch-moderate 端点 | §4.6 |
| 已删除过滤 | 不工作 | Admin 端点绕过 @TableLogic | §4.4 |
| 提交数列 | 始终 0 | 从 submissions 表 COUNT | §4.8 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/.../admin/controller/AdminProblemController.java` | all | 需要新增端点的控制器 |
| P0 | `backend-spring/.../problem/service/impl/ProblemServiceImpl.java` | 130-168 | buildProblemQueryWrapper 需修复 |
| P0 | `backend-spring/.../problem/dto/ProblemQueryDTO.java` | all | 需增加 publishStatus 字段 |
| P1 | `backend-spring/.../problem/entity/Problem.java` | all | @TableLogic 和 flag 字段 |
| P1 | `management/src/api/admin/problems.ts` | all | 前端 API 层需修改路径和类型 |
| P1 | `management/src/views/problems/composables/useProblemFilters.ts` | all | 过滤器逻辑需重写 |
| P2 | `management/src/views/problems/ProblemsListView.vue` | all | 状态过滤器 UI 需改 |
| P2 | `backend-spring/.../problem/mapper/ProblemMapper.java` | all | 需新增查询方法 |
| P2 | `backend-spring/.../admin/service/impl/AdminProblemServiceImpl.java` | all | 需新增 flag/modify/import 逻辑 |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| MyBatis-Plus @InterceptorIgnore | baomidou.com | `@InterceptorIgnore(tenantLine = "true")` 可跳过特定拦截器，但 @TableLogic 不受此控制；需用原生 SQL 或 `ISqlParser` 绕过 |
| MyBatis-Plus 逻辑删除绕过 | baomidou.com | 使用 `@InterceptorIgnore` 不适用于 @TableLogic；需在 Mapper 中写原生 SQL 查询 `is_deleted` 字段 |

---

## Patterns to Mirror

### CONTROLLER_PATTERN
// SOURCE: AdminProblemController.java:46-50
```java
@GetMapping
public Result<PageResult<ProblemVO>> getProblems(ProblemQueryDTO query) {
    return Result.success(problemService.listProblems(query));
}
```

### SERVICE_PATTERN
// SOURCE: ProblemServiceImpl.java:89-95
```java
int currentPage = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
int currentPageSize = (query.getPageSize() != null && query.getPageSize() > 0) ? query.getPageSize() : 20;
currentPageSize = Math.min(currentPageSize, 100);
Page<Problem> problemPage = new Page<>(currentPage, currentPageSize);
```

### DTO_PATTERN
// SOURCE: ProblemQueryDTO.java
```java
@Data
@Schema(description = "Problem query parameters")
public class ProblemQueryDTO {
    @Schema(description = "Page number (1-based)")
    private Integer page;
    @Schema(description = "Items per page")
    private Integer pageSize;
    // ...
}
```

### MAPPER_CUSTOM_QUERY_PATTERN
// SOURCE: ProblemMapper.java
```java
@Select("<script>" +
        "SELECT ptr.problem_id, pt.label as tag_name " +
        "FROM problem_tag_relations ptr " +
        "LEFT JOIN problem_tags pt ON ptr.tag_id = ptr.id " +
        "WHERE ptr.problem_id IN " +
        "<foreach collection='problemIds' item='id' open='(' separator=',' close=')'>" +
        "#{id}</foreach>" +
        "</script>")
List<ProblemTagDTO> selectTagsByProblemIds(@Param("problemIds") List<Long> problemIds);
```

### VO_FROM_PATTERN
// SOURCE: ProblemVO.java:183-215
```java
public static ProblemVO from(Problem problem) {
    ProblemVO vo = new ProblemVO();
    // ... map all fields ...
    vo.setSubmissionCount(0L);
    vo.setSolutionCount(0L);
    vo.setTags(List.of());
    return vo;
}
```

### FRONTEND_API_PATTERN
// SOURCE: management/src/api/admin/problems.ts:313-317
```typescript
export const problemsApi = {
  async getProblems(params: ProblemQueryParams): Promise<PageResult<Problem>> {
    return apiGet<PageResult<Problem>>('/admin/problems', { params })
  },
}
```

### FRONTEND_FILTER_PATTERN
// SOURCE: useProblemFilters.ts:98-114
```typescript
function buildFilterParams(tablePagination: PaginationState) {
  return {
    difficulty: difficultyFilter.value === 'all' ? undefined : (difficultyFilter.value as Difficulty),
    status: statusFilter.value === 'all' ? undefined : (statusFilter.value as Problem['status']),
    isPublished: publishedFilter.value === 'all' ? undefined : publishedFilter.value === 'published' ? true : false,
    sortBy: sortBy.value === 'default' ? undefined : sortBy.value,
    sortOrder: sortOrder.value || undefined,
    page: tablePagination.pageIndex,
    limit: tablePagination.pageSize,
  }
}
```

### FRONTEND_STORE_ACTION_PATTERN
// SOURCE: management/src/stores/admin/problems.ts:330-343
```typescript
async function bulkAction(data: BulkProblemActionDto) {
  loading.value = true
  error.value = null
  try {
    await problemsApi.bulkAction(data)
    await fetchProblems()
  } catch (err: unknown) {
    error.value = extractErrorMessage(err)
    throw err
  } finally {
    loading.value = false
  }
}
```

---

## Files to Change

### Backend

| File | Action | Justification |
|---|---|---|
| `ProblemQueryDTO.java` | UPDATE | 新增 `publishStatus` 字段，重命名 `status` 含义 |
| `ProblemServiceImpl.java` | UPDATE | 修复 buildProblemQueryWrapper：排序、标签、isDeleted 过滤 |
| `AdminProblemController.java` | UPDATE | 新增 6 个端点 (flag/moderate/flagged/batch-moderate/import/submissions) |
| `AdminProblemServiceImpl.java` | UPDATE | 新增 flag/moderate/import/submissions 逻辑 |
| `ProblemMapper.java` | UPDATE | 新增 countSubmissionsByProblemIds、selectDeletedProblems 等查询 |
| `ProblemVO.java` | UPDATE | submissionCount/solutionCount 从查询结果填充 |
| `AdminProblemListQueryDTO.java` | DELETE | 孤立 DTO 删除 |
| `BulkProblemRequestDTO.java` | UPDATE | 增加 `restore` 枚举值 |
| 新增 `FlagProblemRequestDTO.java` | CREATE | flag 请求 DTO |
| 新增 `ModerateProblemRequestDTO.java` | CREATE | moderate 请求 DTO |
| 新增 `ImportProblemsRequestDTO.java` | CREATE | import 请求 DTO |
| 新增 `ProblemSubmissionsVO.java` | CREATE | submissions 响应 VO |

### Frontend

| File | Action | Justification |
|---|---|---|
| `management/src/api/admin/problems.ts` | UPDATE | 修复 bulkAction 路径，修改 status 类型，增加 publishStatus |
| `management/src/views/problems/composables/useProblemFilters.ts` | UPDATE | 修复 status 过滤器值域映射 |
| `management/src/views/problems/ProblemsListView.vue` | UPDATE | 状态过滤器 UI 改为发布状态 |
| `management/src/views/problems/composables/useProblemColumns.ts` | UPDATE | status 列改为显示发布状态 |

## NOT Building
- 新的数据库迁移（所有字段已存在于表中）
- 前端 Design System 变更
- 推荐系统相关修改
- Console 前端（仅修改 Management 前端）

---

## Step-by-Step Tasks

### Phase 1: P0 — 批量操作路径修复 (§4.7)

#### Task 1.1: 修复前端批量操作路径
- **ACTION**: 修改 `problemsApi.bulkAction()` 的请求路径
- **IMPLEMENT**: 将 `/admin/bulk/problems/publish` 改为 `/admin/problems/bulk`
- **MIRROR**: FRONTEND_API_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: `bulkEdit` 也使用了 `/admin/bulk/problems/edit`，需一并检查
- **VALIDATE**: 在浏览器中选中多行题目，点击批量发布/取消发布，确认不再 404

#### Task 1.2: 后端 BulkProblemRequestDTO 增加 restore
- **ACTION**: 在 `BulkAction` 枚举中添加 `restore` 值
- **IMPLEMENT**:
```java
public enum BulkAction {
    publish,
    unpublish,
    delete,
    restore,  // 新增
    edit
}
```
- **MIRROR**: DTO_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 需同步在 `AdminProblemServiceImpl.bulkAction()` 中添加 restore case
- **VALIDATE**: 后端编译通过

#### Task 1.3: AdminProblemServiceImpl 支持 restore 操作
- **ACTION**: 在 bulkAction switch 中添加 restore case
- **IMPLEMENT**: restore 逻辑 = 将 `is_deleted` 设为 false
- **MIRROR**: SERVICE_PATTERN
- **IMPORTS**: ProblemMapper
- **GOTCHA**: MyBatis-Plus 的 `updateById` 在 @TableLogic 作用下会自动追加 `is_deleted=0` 条件，需使用原生 SQL 更新
- **VALIDATE**: 单元测试验证 restore 操作

---

### Phase 2: P1 — 排序、状态过滤、软删除过滤 (§4.2, §4.5, §4.4)

#### Task 2.1: 修复后端排序逻辑 (§4.2)
- **ACTION**: 在 `buildProblemQueryWrapper` 中根据 `sortBy` 动态构建 orderBy 子句
- **IMPLEMENT**:
```java
// 替换 queryWrapper.orderByAsc(Problem::getId);
String sortBy = query.getSortBy();
String sortOrder = query.getSortOrder();
if (sortBy != null && !sortBy.isBlank()) {
    boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
    // 映射前端字段名到后端字段名
    switch (sortBy) {
        case "title" -> queryWrapper.orderBy(true, isAsc, Problem::getTitle);
        case "difficulty" -> queryWrapper.orderBy(true, isAsc, Problem::getDifficulty);
        case "createdAt", "created_at" -> queryWrapper.orderBy(true, isAsc, Problem::getCreatedAt);
        case "updatedAt", "updated_at" -> queryWrapper.orderBy(true, isAsc, Problem::getUpdatedAt);
        default -> queryWrapper.orderByAsc(Problem::getId);
    }
    // submissionCount 排序需特殊处理（不在 problems 表中），暂用 ID 排序
} else {
    queryWrapper.orderByDesc(Problem::getCreatedAt);
}
```
- **MIRROR**: SERVICE_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: `submissionCount` 不在 problems 表中，无法直接排序；后续 Phase 3 填充真实数据后可考虑子查询排序
- **VALIDATE**: 前端选择不同排序方式，观察后端 SQL 日志确认 ORDER BY 子句变化

#### Task 2.2: 修复 status 过滤器值域不一致 (§4.5)
- **ACTION**: 在前端区分"发布状态"和"解题状态"，使用 `isPublished` 过滤发布状态
- **IMPLEMENT**:
  1. 在 `ProblemQueryDTO` 中新增 `publishStatus` 字段（值为 DRAFT/PUBLISHED/ARCHIVED）
  2. 在 `buildProblemQueryWrapper` 中处理 `publishStatus`:
     - `DRAFT` → `is_published = false AND is_deleted = false`
     - `PUBLISHED` → `is_published = true AND is_deleted = false`
     - `ARCHIVED` → `is_deleted = true`（需绕过 @TableLogic）
  3. 前端 `useProblemFilters.ts` 中 `buildFilterParams` 将 `statusFilter` 的值映射到 `publishStatus` 参数（而非 `status`）
  4. 前端 `ProblemsListView.vue` 中过滤器下拉选项保持 DRAFT/PUBLISHED/ARCHIVED 不变，但发送参数名改为 `publishStatus`
- **MIRROR**: FRONTEND_FILTER_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 后端 `status` 字段仍然保留（解题状态），只是 admin 列表不再用它做过滤
- **VALIDATE**: 前端选择 DRAFT/PUBLISHED/ARCHIVED，后端 SQL 日志显示正确的 WHERE 条件

#### Task 2.3: 修复 isDeleted 过滤器 + 绕过 @TableLogic (§4.4)
- **ACTION**: Admin 端点查询时支持查看已软删除的题目
- **IMPLEMENT**:
  1. 在 `ProblemMapper` 中新增原生 SQL 查询方法：
```java
@Select("<script>" +
        "SELECT * FROM problems WHERE is_deleted = true " +
        "<if test='search != null and search != \"\"'>" +
        "AND (id = #{search} OR title LIKE CONCAT('%',#{search},'%'))" +
        "</if>" +
        " ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}" +
        "</script>")
List<Problem> selectDeletedProblems(@Param("search") String search, @Param("limit") int limit, @Param("offset") int offset);
```
  2. 在 `buildProblemQueryWrapper` 中，当 `isDeleted=true` 时，使用原生 SQL 查询而非 MyBatis-Plus 的 LambdaQueryWrapper
  3. 当 `publishStatus=ARCHIVED` 时，自动走已删除查询路径
- **MIRROR**: MAPPER_CUSTOM_QUERY_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: @TableLogic 会自动追加 `WHERE is_deleted = 0`，所有通过 MyBatis-Plus `selectList/selectPage` 的查询都会过滤已删除记录。必须使用原生 SQL `@Select` 绕过
- **VALIDATE**: 前端选择"已归档"过滤器，能看到已软删除的题目

#### Task 2.4: 前端去重排序选项
- **ACTION**: 移除 `ProblemsListView.vue` 中重复的 sortBy 选项
- **IMPLEMENT**: 只保留 `createdAt`/`updatedAt`（camelCase），移除 `created_at`/`updated_at`（snake_case）
- **MIRROR**: FRONTEND_FILTER_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 后端需要同时处理两种格式，见 Task 2.1 的 switch 中已包含两种映射
- **VALIDATE**: 排序下拉中无重复选项

---

### Phase 3: P2 — 标签过滤、difficulty 大小写、分页、提交数 (§4.3, §4.1, §4.9, §4.8)

#### Task 3.1: 修复 tag 过滤器 (§4.3)
- **ACTION**: 在 `buildProblemQueryWrapper` 中通过子查询实现标签过滤
- **IMPLEMENT**:
```java
if (query.getTag() != null && !query.getTag().isBlank()) {
    queryWrapper.inSql(Problem::getId,
        "SELECT problem_id FROM problem_tag_relations ptr " +
        "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
        "WHERE pt.label = '" + query.getTag().replace("'", "''") + "' " +
        "OR pt.slug = '" + query.getTag().replace("'", "''") + "'");
}
```
  或更安全地使用 `apply`:
```java
if (query.getTag() != null && !query.getTag().isBlank()) {
    queryWrapper.apply("id IN (SELECT ptr.problem_id FROM problem_tag_relations ptr " +
            "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
            "WHERE pt.label = {0} OR pt.slug = {0})", query.getTag());
}
```
- **MIRROR**: MAPPER_CUSTOM_QUERY_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 使用参数化查询 `{0}` 避免 SQL 注入
- **VALIDATE**: 前端选择标签过滤，后端 SQL 日志显示子查询

#### Task 3.2: 修复 difficulty 大小写 (§4.1)
- **ACTION**: 后端在 `buildProblemQueryWrapper` 中对 difficulty 做大小写规范化
- **IMPLEMENT**:
```java
if (query.getDifficulty() != null && !query.getDifficulty().isBlank()) {
    queryWrapper.apply("UPPER(difficulty) = UPPER({0})", query.getDifficulty());
}
```
- **MIRROR**: SERVICE_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 当前 MySQL 使用 `utf8mb4_general_ci`（大小写不敏感），此修改是防御性的，确保切换 collation 后仍可工作
- **VALIDATE**: 前端发送 `EASY`，后端 SQL 使用 `UPPER()` 比较

#### Task 3.3: 修复分页参数差异 (§4.9)
- **ACTION**: 前端统一使用 1-based 分页
- **IMPLEMENT**:
  1. 在 `useProblemFilters.ts` 的 `buildFilterParams` 中，将 `page: tablePagination.pageIndex` 改为 `page: tablePagination.pageIndex + 1`
  2. 后端默认 `pageSize` 改为 10（匹配前端默认值），或在 `ProblemQueryDTO` 中设置 `pageSize` 默认值为 10
- **MIRROR**: FRONTEND_FILTER_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: `useDataTable.ts` 中已经做了 `page: tablePagination.value.pageIndex + 1` 的转换，但 `useProblemFilters.ts` 的 `buildFilterParams` 直接传 `pageIndex` 而非 +1。需确认哪个调用路径被实际使用
- **VALIDATE**: 前端第一页请求发送 `page=1`（而非 `page=0`），后端正确返回第一页数据

#### Task 3.4: 填充 submissionCount / solutionCount (§4.8)
- **ACTION**: 从 submissions 和 solutions 表查询真实计数
- **IMPLEMENT**:
  1. 在 `ProblemMapper` 中新增：
```java
@Select("<script>" +
        "SELECT problem_id, COUNT(*) as count FROM submissions " +
        "WHERE problem_id IN " +
        "<foreach collection='problemIds' item='id' open='(' separator=',' close=')'>" +
        "#{id}</foreach>" +
        " GROUP BY problem_id" +
        "</script>")
List<ProblemCountDTO> countSubmissionsByProblemIds(@Param("problemIds") List<Long> problemIds);

@Select("<script>" +
        "SELECT problem_id, COUNT(*) as count FROM solutions " +
        "WHERE problem_id IN " +
        "<foreach collection='problemIds' item='id' open='(' separator=',' close=')'>" +
        "#{id}</foreach>" +
        " AND is_deleted = false" +
        " GROUP BY problem_id" +
        "</script>")
List<ProblemCountDTO> countSolutionsByProblemIds(@Param("problemIds") List<Long> problemIds);
```
  2. 新增 `ProblemCountDTO` record: `record ProblemCountDTO(Long problemId, Long count) {}`
  3. 在 `ProblemServiceImpl.listProblems()` 中，获取分页结果后批量查询计数：
```java
List<Long> problemIds = problems.stream().map(Problem::getId).toList();
Map<Long, Long> submissionCounts = problemMapper.countSubmissionsByProblemIds(problemIds)
    .stream().collect(Collectors.toMap(ProblemCountDTO::problemId, ProblemCountDTO::count));
Map<Long, Long> solutionCounts = problemMapper.countSolutionsByProblemIds(problemIds)
    .stream().collect(Collectors.toMap(ProblemCountDTO::problemId, ProblemCountDTO::count));
// 在 VO 构建时填充
vo.setSubmissionCount(submissionCounts.getOrDefault(problem.getId(), 0L));
vo.setSolutionCount(solutionCounts.getOrDefault(problem.getId(), 0L));
```
- **MIRROR**: MAPPER_CUSTOM_QUERY_PATTERN, VO_FROM_PATTERN
- **IMPORTS**: `java.util.stream.Collectors`
- **GOTCHA**: 需确认 `solutions` 表是否有 `problem_id` 字段；如果 solutions 不直接关联 problem_id，需通过 submissions 表 JOIN
- **VALIDATE**: 前端题目列表的"提交数"列显示非零值

---

### Phase 4: P0 — 缺失后端端点 (§4.6)

#### Task 4.1: 新增 Flag 端点 `POST /admin/problems/{id}/flag`
- **ACTION**: 在 `AdminProblemController` 中新增端点，在 `AdminProblemServiceImpl` 中实现逻辑
- **IMPLEMENT**:
  1. 创建 `FlagProblemRequestDTO`:
```java
@Data
public class FlagProblemRequestDTO {
    @NotBlank
    private String reason;
}
```
  2. Controller:
```java
@PostMapping("/{id}/flag")
public Result<ProblemVO> flagProblem(@PathVariable Long id, @RequestBody @Valid FlagProblemRequestDTO request) {
    return Result.success(adminProblemService.flagProblem(id, request.getReason()));
}
```
  3. Service: 使用 `ProblemMapper.updateFlagStatus(id, true, reason)` 更新标记状态，同时设置 `flagged_at = NOW()`, `flag_status = 'PENDING'`
- **MIRROR**: CONTROLLER_PATTERN
- **IMPORTS**: `jakarta.validation.constraints.NotBlank`
- **GOTCHA**: `ProblemMapper.updateFlagStatus` 已存在，但只更新 `is_flagged` 和 `flagged_reason`，还需更新 `flag_status` = 'PENDING'
- **VALIDATE**: 前端点击 Flag 按钮，题目被标记

#### Task 4.2: 新增 Moderate 端点 `POST /admin/problems/{id}/moderate`
- **ACTION**: 审核标记的题目
- **IMPLEMENT**:
  1. 创建 `ModerateProblemRequestDTO`:
```java
@Data
public class ModerateProblemRequestDTO {
    @NotNull
    private String status; // PENDING, REVIEWED, RESOLVED, DISMISSED
    private String notes;
}
```
  2. Controller + Service: 更新 `flag_status`, `flag_reviewed_by`, `flag_reviewed_at`, `flag_notes`；如果 status=DISMISSED，同时设 `is_flagged=false`
- **MIRROR**: CONTROLLER_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 需要获取当前登录用户 ID 作为 `flag_reviewed_by`
- **VALIDATE**: 前端审核标记题目，状态变为 REVIEWED/RESOLVED/DISMISSED

#### Task 4.3: 新增 Flagged 端点 `GET /admin/problems/flagged`
- **ACTION**: 获取已标记题目列表
- **IMPLEMENT**:
```java
@GetMapping("/flagged")
public Result<PageResult<ProblemVO>> getFlaggedProblems(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer limit) {
    return Result.success(adminProblemService.getFlaggedProblems(status, page, limit));
}
```
  Service: 查询 `is_flagged = true` 的题目，按 `flag_status` 过滤
- **MIRROR**: CONTROLLER_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 需注意路由注册顺序，`/flagged` 不能与 `/{id}` 冲突。Spring Boot 中字面路径优先于路径变量，所以 `/flagged` 在 `/{id}` 之前注册即可
- **VALIDATE**: GET /admin/problems/flagged 返回已标记题目

#### Task 4.4: 新增 Batch Moderate 端点 `POST /admin/problems/flagged/batch-moderate`
- **ACTION**: 批量审核标记题目
- **IMPLEMENT**:
```java
@PostMapping("/flagged/batch-moderate")
public Result<List<BulkProblemResultDTO>> batchModerateProblems(@RequestBody @Valid BatchModerateRequestDTO request) {
    return Result.success(adminProblemService.batchModerateProblems(request));
}
```
  创建 `BatchModerateRequestDTO`:
```java
@Data
public class BatchModerateRequestDTO {
    @NotEmpty
    private List<String> ids;
    @NotNull
    private String status; // REVIEWED, RESOLVED, DISMISSED
    private String notes;
}
```
- **MIRROR**: CONTROLLER_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 路由需在 `/{id}` 之前注册
- **VALIDATE**: 批量选择标记题目，批量审核成功

#### Task 4.5: 新增 Submissions 端点 `GET /admin/problems/{id}/submissions`
- **ACTION**: 获取题目的提交记录
- **IMPLEMENT**:
```java
@GetMapping("/{id}/submissions")
public Result<PageResult<SubmissionVO>> getProblemSubmissions(
        @PathVariable Long id,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer limit) {
    return Result.success(adminProblemService.getProblemSubmissions(id, page, limit));
}
```
  Service: 查询 `submissions` 表中 `problem_id = id` 的记录
- **MIRROR**: CONTROLLER_PATTERN
- **IMPORTS**: SubmissionMapper, SubmissionVO
- **GOTCHA**: 需确认 `SubmissionVO` 是否存在；如不存在需创建
- **VALIDATE**: GET /admin/problems/{id}/submissions 返回提交记录

#### Task 4.6: 新增 Import 端点 `POST /admin/problems/import`
- **ACTION**: 批量导入题目
- **IMPLEMENT**:
  1. 创建 `ImportProblemsRequestDTO`:
```java
@Data
public class ImportProblemsRequestDTO {
    @NotEmpty
    private List<ImportProblemItemDTO> problems;
    @Schema(description = "Conflict resolution strategy", allowableValues = {"skip", "update", "create_new"})
    private String onConflict = "skip";
}
```
  2. Controller + Service: 遍历 problems 列表，按 slug 查重，根据 onConflict 策略处理
  3. 返回 `ImportProblemsResponse` (total, created, updated, skipped, failed, results)
- **MIRROR**: CONTROLLER_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 导入逻辑需处理事务（部分成功部分失败），建议整体事务 + 逐条 try-catch
- **VALIDATE**: 前端导入 JSON，题目成功创建

---

### Phase 5: P3 — 清理 (§4.10)

#### Task 5.1: 删除孤立 AdminProblemListQueryDTO
- **ACTION**: 删除 `AdminProblemListQueryDTO.java` 文件
- **IMPLEMENT**: 直接删除文件，确认无其他引用
- **MIRROR**: N/A
- **IMPORTS**: N/A
- **GOTCHA**: 先 grep 确认无引用
- **VALIDATE**: 后端编译通过

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `buildProblemQueryWrapper` with sortBy=title, sortOrder=asc | Query with sortBy=title | ORDER BY title ASC | No |
| `buildProblemQueryWrapper` with sortBy=createdAt | Query with sortBy=createdAt | ORDER BY created_at ASC/DESC | No |
| `buildProblemQueryWrapper` with tag="DP" | Query with tag=DP | WHERE id IN (subquery) | Yes: tag不存在 |
| `buildProblemQueryWrapper` with difficulty="EASY" | Query with difficulty=EASY | WHERE UPPER(difficulty) = UPPER('EASY') | No |
| `buildProblemQueryWrapper` with isDeleted=true | Query with isDeleted=true | 原生SQL查询 is_deleted=1 | Yes: @TableLogic绕过 |
| `buildProblemQueryWrapper` with publishStatus=DRAFT | Query with publishStatus=DRAFT | WHERE is_published=false | No |
| `flagProblem` service | problemId, reason | problem.isFlagged=true, flagStatus=PENDING | No |
| `moderateProblem` service | problemId, status=DISMISSED | isFlagged=false, flagStatus=DISMISSED | No |
| `bulkAction` with restore | ids, action=restore | is_deleted=false for all ids | Yes: @TableLogic绕过 |
| `importProblems` with onConflict=skip | duplicate slug | skipped, not created | Yes: 重复slug |
| `countSubmissionsByProblemIds` | [1,2,3] | Map with counts | Yes: 无提交的题目 |

### Edge Cases Checklist
- [ ] 空标签过滤（标签不存在于数据库）
- [ ] 空排序字段（sortBy=null 或 default）
- [ ] isDeleted=true 但无已删除题目
- [ ] 发布状态 ARCHIVED 但无已归档题目
- [ ] 批量操作中包含已删除 ID 的 restore
- [ ] flag 同一题目两次（幂等性）
- [ ] moderate 时 status 值不在枚举中
- [ ] import 空 JSON 数组
- [ ] import 中 slug 冲突 + onConflict=create_new
- [ ] 分页 page=0 的处理（应回退到 page=1）

---

## Validation Commands

### Static Analysis
```bash
cd backend-spring && ./mvnw compile -DskipTests
```
EXPECT: 编译成功

### Backend Tests
```bash
cd backend-spring && ./mvnw test
```
EXPECT: 所有测试通过

### Frontend Type Check
```bash
cd management && pnpm type-check
```
EXPECT: 零类型错误

### Frontend Lint
```bash
cd management && pnpm lint
```
EXPECT: 无 lint 错误

### Frontend Build
```bash
cd management && pnpm build
```
EXPECT: 构建成功

### Manual Validation
- [ ] 打开 http://localhost:9003/problems
- [ ] 选择难度过滤 EASY → 列表只显示 Easy 题目
- [ ] 选择标签过滤 → 列表按标签过滤
- [ ] 选择排序"按标题" → 列表按标题排序
- [ ] 选择发布状态 DRAFT → 只显示未发布题目
- [ ] 选择已归档 → 显示已软删除题目
- [ ] 选中多行，批量发布 → 成功（非 404）
- [ ] 点击 Flag 按钮 → 题目被标记
- [ ] 点击审核按钮 → 标记状态变更
- [ ] 提交数列显示非零值
- [ ] 分页切换正常工作

---

## Acceptance Criteria
- [ ] 所有 10 个对齐问题已修复
- [ ] 后端编译通过，测试通过
- [ ] 前端类型检查、lint、构建通过
- [ ] 手动验证所有过滤器和排序功能
- [ ] 批量操作不再 404
- [ ] Flag/Moderate 功能可用
- [ ] 提交数显示真实数据

## Completion Checklist
- [ ] 代码遵循现有模式
- [ ] 错误处理匹配代码库风格
- [ ] 无硬编码值
- [ ] 无不必要的范围扩展
- [ ] 自包含——实施时无需额外搜索

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| @TableLogic 绕过方案不稳定 | Medium | High | 使用原生 SQL @Select 注解，避免 MyBatis-Plus 自动追加条件 |
| submissionCount 批量查询性能 | Low | Medium | 使用 IN 子查询批量获取，避免 N+1；限制每页 100 条 |
| 新增端点路由与 `/{id}` 冲突 | Medium | Low | `/flagged`、`/flagged/batch-moderate`、`/import` 等字面路径在 `/{id}` 之前注册 |
| import 大量数据时事务问题 | Medium | Medium | 逐条 try-catch，收集成功/失败结果，整体返回 |
| 前端 status 过滤器改动影响现有用户 | Low | Low | 从 DRAFT/PUBLISHED/ARCHIVED 映射到 isPublished 布尔值，行为更直观 |

## Notes
- 问题 §4.5 的核心混淆：前端将"发布状态"（DRAFT/PUBLISHED/ARCHIVED）错误地映射到了后端的"解题状态"（solved/attempted/todo）。修复方案是新增 `publishStatus` 参数，让前端发送正确的语义
- `ProblemMapper.updateFlagStatus` 已存在，但只更新 `is_flagged` 和 `flagged_reason`。需要扩展或新增方法来更新 `flag_status`、`flag_reviewed_by` 等字段
- solutions 表的 `problem_id` 关联方式需确认——可能需要通过 submissions 表间接关联
- 分页问题（§4.9）的核心在于 `useProblemFilters.ts` 的 `buildFilterParams` 直接传 `pageIndex`（0-based）而非 +1。`useDataTable.ts` 的 `loadEntities` 已做了 +1，但 `ProblemsListView` 使用的是 `useProblemFilters.buildFilterParams` 而非 `useDataTable.loadEntities`
