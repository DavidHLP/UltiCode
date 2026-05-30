# Plan: Problem Audit Log Feature

## Summary
为 Management 前端的题目管理页面（ProblemsListView）添加审计日志功能：管理员可查看任意题目的操作历史。后端新增 `GET /admin/problems/{id}/audit` 端点，前端在每行操作列增加审计按钮，点击弹出抽屉展示该题目的审计记录。

## User Story
As an admin,
I want to view the audit history of any problem,
So that I can track who changed what and when (publish, flag, moderate, update, delete, etc.).

## Problem → Solution
当前 `AdminProblemController` 没有审计日志查询端点，Forum 模块已有完整实现（`GET /posts/{id}/audit`）。复刻该模式，为 Problem 模块添加等效功能。

## Metadata
- **Complexity**: Small
- **Source PRD**: N/A
- **PRD Phase**: N/A (standalone feature)
- **Estimated Files**: 5 files (2 backend, 3 frontend)
- **Confidence Score**: 10/10 — 完全复刻已有模式

---

## UX Design

### Before
```
ProblemsListView 表格行 actions 列：
[查看] [编辑] [删除] [发布] [标记] ...

无审计按钮，无审计入口。
```

### After
```
ProblemsListView 表格行 actions 列：
[查看] [编辑] [删除] [发布] [标记] [审计]  ← 新增审计按钮

点击 [审计] → 右侧抽屉弹出
┌──────────────────────────────────────┐
│  problem-1  audit history    [X]     │
├──────────────────────────────────────┤
│  时间            操作      操作人    │
│  2026-05-29 14:00  PUBLISH  admin    │
│  2026-05-28 10:30  FLAG     mod1    │
│  2026-05-27 09:00  CREATE   admin    │
└──────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| ProblemsListView 行 actions 列 | 无审计入口 | 新增审计图标按钮 | 使用现有的 IconInfoCircle |
| 审计抽屉 | N/A | 新建 ProblemAuditDrawer.vue | 复用 AuditLogDetailDrawer 样式 |
| 后端 API | 无 | `GET /admin/problems/{id}/audit` | 返回 `List<AuditLogVO>` |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `AdminForumController.java` | 52-59 | 审计端点模式 |
| P0 (critical) | `AdminForumServiceImpl.java` | 303-311 | `getPostAuditHistory()` 实现 |
| P0 (critical) | `AdminProblemController.java` | 全部 | 现有端点模式，需要保持一致 |
| P1 (important) | `AuditActionUtil.java` | 全部 | 已有 `CREATE_PROBLEM` 等常量 |
| P1 (important) | `AuditService.java` | 全部 | 现有接口方法 |
| P2 (reference) | `AuditLogsView.vue` | 全部 | 前端审计日志 UI |
| P2 (reference) | `AuditLogDetailDrawer.vue` | 全部 | 前端抽屉组件模式 |

---

## Patterns to Mirror

### BACKEND_CONTROLLER_ENDPOINT
// SOURCE: `AdminForumController.java:52-59`
```java
    @Operation(summary = "Get post audit history", description = "Get audit history for a forum post")
    @GetMapping("/posts/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AuditLogVO>> getPostAuditHistory(
            @Parameter(description = "Post ID")
            @PathVariable String id) {
        return Result.success(adminForumService.getPostAuditHistory(id));
    }
```

### BACKEND_SERVICE_METHOD
// SOURCE: `AdminForumServiceImpl.java:303-311`
```java
    @Override
    public List<AuditLogVO> getPostAuditHistory(String id) {
        AuditLogQueryDTO query = new AuditLogQueryDTO();
        query.setEntityType("FORUM_POST");
        query.setEntityId(id);
        query.setPage(1);
        query.setLimit(100);
        return auditService.getAuditLogs(query).getItems();
    }
```

### BACKEND_SERVICE_INTERFACE
// SOURCE: `AdminForumService.java:92`
```java
    List<AuditLogVO> getPostAuditHistory(String id);
```

### ENTITY_TYPE_CONSTANT
// SOURCE: `AuditActionUtil.java:78`
```java
    public static final String ENTITY_PROBLEM = "PROBLEM";
```
注意：`AuditActionUtil` 已有 `ENTITY_PROBLEM = "PROBLEM"` 常量，无需新增。

### FRONTEND_DRAWER_PATTERN
// SOURCE: `AuditLogDetailDrawer.vue` (使用现有的审计抽屉组件，仅改变数据源)

---

## Files to Change

### Backend (2 files)

| File | Action | Justification |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminProblemService.java` | UPDATE | 新增 `getProblemAuditHistory(Long id)` 方法签名 |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemServiceImpl.java` | UPDATE | 实现 `getProblemAuditHistory()` |
| `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminProblemController.java` | UPDATE | 新增 `GET /{id}/audit` 端点 |

### Frontend (3 files)

| File | Action | Justification |
|---|---|---|
| `management/src/views/problems/ProblemsListView.vue` | UPDATE | 在 actions 列新增审计按钮 |
| `management/src/api/admin/audit.ts` | UPDATE | 新增 `getProblemAuditLogs(id, params)` API 函数 |
| `management/src/views/problems/components/ProblemAuditDrawer.vue` | CREATE | 审计历史抽屉组件 |

---

## NOT Building

- **审计日志自动记录**：不在此 PR 中实现题目操作的自动审计记录（AOP `@Audited` 或手动 `auditHelper.log()`）。仅实现**查询**已有审计日志的能力。
- **i18n 新增翻译键**：审计相关的 UI 文案复用现有的 `audit.*` i18n 键（`audit.columns.createdAt`、`audit.columns.action` 等），无需新增翻译。
- **审计统计页面**：仅为单个题目提供历史记录，暂不提供题目级别的统计面板。
- **导出功能**：不实现题目的审计日志导出。

---

## Step-by-Step Tasks

### Task 1: Backend — AdminProblemService interface
- **ACTION**: 新增 `getProblemAuditHistory(Long id)` 方法签名
- **IMPLEMENT**:
```java
// AdminProblemService.java 新增方法签名
import com.ulticode.modules.admin.dto.AuditLogVO;
import java.util.List;
List<AuditLogVO> getProblemAuditHistory(Long id);
```
- **MIRROR**: `AdminForumService.java:92` 的 `getPostAuditHistory` 方法签名
- **IMPORTS**: `com.ulticode.modules.admin.dto.AuditLogVO`, `java.util.List`
- **GOTCHA**: 接口参数用 `Long id`（与 Forum 的 `String id` 不同，Problem 的 ID 是 Long 类型）
- **VALIDATE**: 接口编译通过，方法签名与实现类匹配

### Task 2: Backend — AdminProblemServiceImpl implementation
- **ACTION**: 实现 `getProblemAuditHistory(Long id)` 方法
- **IMPLEMENT**:
```java
@Override
public List<AuditLogVO> getProblemAuditHistory(Long id) {
    AuditLogQueryDTO query = new AuditLogQueryDTO();
    query.setEntityType(AuditActionUtil.ENTITY_PROBLEM); // "PROBLEM"
    query.setEntityId(String.valueOf(id));
    query.setPage(1);
    query.setLimit(100);
    return auditService.getAuditLogs(query).getItems();
}
```
- **MIRROR**: `AdminForumServiceImpl.java:303-311` 的 `getPostAuditHistory`
- **IMPORTS**: `AuditLogQueryDTO`, `AuditLogVO`, `AuditService`, `AuditActionUtil`
- **GOTCHA**: `AuditActionUtil.ENTITY_PROBLEM` 已经是 `"PROBLEM"` 常量，直接使用
- **VALIDATE**: Service 编译通过

### Task 3: Backend — AdminProblemController endpoint
- **ACTION**: 新增 `GET /{id}/audit` 端点
- **IMPLEMENT**:
```java
@Operation(summary = "Get problem audit history", description = "Get audit history for a problem")
@GetMapping("/{id}/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<List<AuditLogVO>> getProblemAuditHistory(
        @Parameter(description = "Problem ID")
        @PathVariable Long id) {
    return Result.success(adminProblemService.getProblemAuditHistory(id));
}
```
- **MIRROR**: `AdminForumController.java:52-59` 的 `getPostAuditHistory` 端点
- **IMPORTS**: `io.swagger.v3.oas.annotations.Operation`, `io.swagger.v3.oas.annotations.security.SecurityRequirement`, `io.swagger.v3.oas.annotations.tags.Tag`, `io.swagger.v3.oas.annotations.Parameter`, `org.springframework.security.access.prepost.PreAuthorize`, `org.springframework.web.bind.annotation.*`, `com.ulticode.common.response.Result`
- **GOTCHA**: 放在现有端点之后（建议在 `getProblemCases` 之后，line 265 附近）
- **VALIDATE**: Swagger 文档出现新端点

### Task 4: Frontend — audit.ts API function
- **ACTION**: 新增 `getProblemAuditLogs` API 函数
- **IMPLEMENT**:
```typescript
async getProblemAuditLogs(id: string | number, params: AuditLogQueryParams = {}): Promise<PageResult<AuditLog>> {
  return apiGet<PageResult<AuditLog>>(`/admin/problems/${id}/audit`)
},
```
- **MIRROR**: 复用 `auditApi.getAuditLogs` 的模式，使用 `apiGet`
- **IMPORTS**: `apiGet` from `@/utils/request`, `AuditLog`, `PageResult`, `AuditLogQueryParams`
- **GOTCHA**: 后端返回 `List<AuditLogVO>`，即 `PageResult<AuditLogVO>` 的 `items` 列表；前端 `getProblemAuditLogs` 返回 `PageResult<AuditLog>` 但实际只取 `items`
- **VALIDATE**: TypeScript 编译通过

### Task 5: Frontend — ProblemAuditDrawer.vue
- **ACTION**: 创建审计历史抽屉组件
- **IMPLEMENT**: 参照 `AuditLogsView.vue` 的列定义 + `AuditLogDetailDrawer.vue` 的抽屉结构，创建 `ProblemAuditDrawer.vue`
  - 接收 `problemId` prop
  - 调用 `auditApi.getProblemAuditLogs(problemId)`
  - 显示：createdAt、action、performer、oldValues/newValues diff
  - 使用与 `AuditLogDetailDrawer` 相同的抽屉 UI 模式
- **MIRROR**: `AuditLogDetailDrawer.vue` 的抽屉布局和 `AuditLogsView.vue` 的列定义
- **IMPORTS**: `auditApi`, `AuditLog`, `IconInfoCircle` 等
- **GOTCHA**: 数据较少（最多100条），不需要分页，简单列表展示即可
- **VALIDATE**: 组件渲染正常，数据正确

### Task 6: Frontend — ProblemsListView.vue audit button
- **ACTION**: 在操作列新增审计按钮
- **IMPLEMENT**:
  - 从 `useProblemColumns` 传来的 actions 选项中，在现有按钮后新增审计按钮（图标用 `IconInfoCircle`）
  - 点击打开 `ProblemAuditDrawer`，传入当前行对应的 problemId
- **MIRROR**: 参照 `useProblemColumns` 中现有的 flag/unflag/publish/unpublish 按钮
- **IMPORTS**: `IconInfoCircle` from `@tabler/icons-vue`, `ProblemAuditDrawer`
- **GOTCHA**: 审计按钮建议对所有有权限的管理员显示，不需要额外的权限控制
- **VALIDATE**: 按钮出现，点击弹出抽屉

---

## Testing Strategy

### Backend
| Test | Description | Edge Case |
|---|---|---|
| `getProblemAuditHistory_existingProblem` | ID 存在时返回审计记录列表 | 问题无审计记录时返回空列表 |
| `getProblemAuditHistory_nonexistentProblem` | ID 不存在时返回空列表（不报错） | 无审计记录返回空 `List` |
| `getProblemAuditHistory_noAuth` | 未认证请求返回 401 | — |

### Frontend
| Test | Description | Edge Case |
|---|---|---|
| Audit button renders | 表格行显示审计按钮 | 无权限用户不显示 |
| Drawer opens with data | 点击按钮弹出抽屉，显示审计记录 | 无记录时显示空状态 |
| Drawer closes | 点击 X 或外部关闭抽屉 | — |

---

## Validation Commands

### Backend Compile
```bash
cd backend-spring && ./mvnw compile -q
```
EXPECT: 编译成功，无错误

### Frontend Type Check
```bash
cd management && pnpm type-check
```
EXPECT: 零类型错误

### Frontend Build
```bash
cd management && pnpm build
```
EXPECT: 构建成功

### Manual Validation
- [ ] 启动后端：`pm2 restart ulticode-9001`
- [ ] 启动前端：`pm2 restart ulticode-9003`
- [ ] 访问 `http://localhost:9003/problems`
- [ ] 找到任意题目行，点击最右侧「审计」图标
- [ ] 抽屉正确弹出，无报错
- [ ] 关闭抽屉，功能正常

---

## Acceptance Criteria
- [ ] `GET /admin/problems/{id}/audit` 端点可用（已认证管理员）
- [ ] 返回 `List<AuditLogVO>`，与 Forum 的 `/posts/{id}/audit` 格式一致
- [ ] ProblemsListView 每行操作列显示审计按钮
- [ ] 点击审计按钮弹出抽屉，正确显示该题目的审计历史
- [ ] 前端无 TypeScript 错误
- [ ] 后端编译通过
- [ ] 不影响现有功能

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 无审计数据可查 | 低 | 低 | 功能本身是查询已有记录，无记录时显示空抽屉 |
| 审计记录过多 | 低 | 低 | 查询限制 100 条，不做分页（题目的审计记录不会太多） |

## Notes
- Forum 模块的审计记录使用 `AuditHelper.logForUser()`，Problem 模块在后续 PR 中再添加自动审计记录。
- 当前 PR 只实现**读取**已有审计日志的能力，不实现写入。
- `ENTITY_PROBLEM = "PROBLEM"` 常量在 `AuditActionUtil` 中已存在，直接复用。