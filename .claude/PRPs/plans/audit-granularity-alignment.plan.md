# Plan: Audit 模块前后端颗粒度对齐

## Summary
修复 audit 模块 8 个前后端不对齐问题：分页响应字段名错误、导出 API 不存在、查询参数缺失、统计响应结构化、路由断裂、枚举不同步、Action 过滤不一致、Forum 类型重复。策略：前端对齐后端已有的 PageResult 规范，后端补齐缺失的 search/action 查询参数和 export 端点。

## User Story
As a 管理员, I want audit 模块前后端完全对齐, so that 所有过滤、分页、导出、统计功能正常工作。

## Problem → Solution
[分页数据取不到、导出 404、过滤不生效、统计字段 null、报表页不可访问] → [前后端契约完全对齐，所有功能可用]

## Metadata
- **Complexity**: Large
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 15

---

## Mandatory Reading

| Priority | File | Why |
|----------|------|-----|
| P0 | `management/src/api/admin/audit.ts` | 前端 API 层 - 需重写响应类型 |
| P0 | `backend-spring/.../dto/AuditLogQueryDTO.java` | 后端查询 DTO - 需新增字段 |
| P0 | `backend-spring/.../dto/AuditStatsVO.java` | 后端统计 VO - 需结构化 |
| P1 | `management/src/stores/admin/audit.ts` | Pinia store - 需适配新类型 |
| P1 | `backend-spring/.../service/impl/AuditServiceImpl.java` | 后端 service - 需补查询逻辑 |
| P1 | `management/src/views/audit/AuditLogsView.vue` | 主列表视图 |
| P1 | `management/src/views/audit/AuditReportView.vue` | 统计报表视图 |
| P1 | `management/src/components/audit/AuditLogViewer.vue` | 可嵌入审计查看器 |
| P2 | `management/src/api/admin/contests.ts:85-89` | PageResult 正确范例 |
| P2 | `management/src/api/admin/forum.ts:98-112` | AuditEntry 定义（需替换） |

---

## Patterns to Mirror

### PAGERESULT_PATTERN (前端)
// SOURCE: `management/src/api/admin/contests.ts:85-89`
```typescript
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}
```

### PAGERESULT_PATTERN (后端)
// SOURCE: `backend-spring/.../common/response/PageResult.java:44-49`
```java
private PageResult(List<T> items, Long total, Integer page, Integer pageSize, Integer totalPages)
```

### SERVICE_PATTERN
// SOURCE: `backend-spring/.../service/impl/AuditServiceImpl.java:49-71`
```java
LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
if (query.getXxx() != null) {
    wrapper.eq(AuditLog::getXxx, query.getXxx());
}
```

### MAPPER_SQL_PATTERN
// SOURCE: `backend-spring/.../mapper/AuditLogMapper.java:15-28`
```java
@Select("<script>"
    + "SELECT entity_type as entityType, COUNT(*) as count "
    + "FROM audit_logs "
    + "<where>"
    + "  <if test='startDate != null'> AND created_at &gt;= #{startDate}</if>"
    + "</where>"
    + "GROUP BY entity_type ORDER BY count DESC LIMIT 10"
    + "</script>")
```

---

## Files to Change

| File | Action | Justification |
|------|--------|---------------|
| `management/src/api/admin/audit.ts` | UPDATE | 修复 AuditLogsResponse、AuditStats 类型，移除 export API |
| `management/src/stores/admin/audit.ts` | UPDATE | 适配新响应类型，移除 exportLogs |
| `management/src/views/audit/AuditLogsView.vue` | UPDATE | 适配新类型字段名 |
| `management/src/views/audit/AuditReportView.vue` | UPDATE | 适配 AuditStats 新结构，移除 export 按钮 |
| `management/src/components/audit/AuditLogViewer.vue` | UPDATE | 适配新类型，统一 action 过滤选项 |
| `management/src/router/index.ts` | UPDATE | 添加 audit-report 路由 |
| `management/src/api/admin/forum.ts` | UPDATE | 用 AuditLog 替换 AuditEntry |
| `management/src/stores/admin/forum.ts` | UPDATE | 适配 AuditEntry → AuditLog |
| `backend-spring/.../dto/AuditLogQueryDTO.java` | UPDATE | 新增 search、action 字段 |
| `backend-spring/.../dto/AuditStatsVO.java` | UPDATE | Map → 结构化 DTO |
| `backend-spring/.../dto/EntityTypeStat.java` | CREATE | 统计子 DTO |
| `backend-spring/.../dto/PerformerStat.java` | CREATE | 统计子 DTO |
| `backend-spring/.../service/impl/AuditServiceImpl.java` | UPDATE | 新增 search/action 过滤，stats 结构化映射 |
| `backend-spring/.../mapper/AuditLogMapper.java` | UPDATE | 新增 search/action 条件到统计 SQL |
| `backend-spring/.../controller/AuditController.java` | UPDATE | 新增 export 端点 |

## NOT Building
- sortBy/sortOrder 后端支持（保持 createdAt DESC 固定排序，前端移除这两个参数）
- 后端 action/entityType 枚举约束（保持自由字符串，前端枚举仅用于 UI 展示）
- 数据库 schema 变更（无需新迁移）

---

## Step-by-Step Tasks

### Task 1: 修复前端分页响应类型 [CRITICAL #1]
- **ACTION**: 将 `AuditLogsResponse` 改为使用 `PageResult<AuditLog>` 对齐后端
- **IMPLEMENT**:
  1. 在 `audit.ts` 中新增 `PageResult<T>` 接口（或从 contests.ts 导入），替换 `AuditLogsResponse`
  2. 将 `logs` → `items`，`limit` → `pageSize`
  3. 更新 `getAuditLogs` 返回类型为 `PageResult<AuditLog>`
- **MIRROR**: `contests.ts:85-89` 的 `PageResult<T>` 定义
- **IMPORTS**: 无需新增（本地定义或共享导入）
- **GOTCHA**: 其他 admin API 各自定义了 `PageResult`，保持同样的本地定义模式
- **VALIDATE**: `pnpm type-check` 通过

### Task 2: 修复 Store 适配新分页类型 [CRITICAL #1 续]
- **ACTION**: 更新 `stores/admin/audit.ts` 中的 `fetchLogs` 使用 `data.items`
- **IMPLEMENT**:
  ```typescript
  // 修改前
  logs.value = data.logs ?? []
  // 修改后
  logs.value = data.items ?? []
  ```
- **MIRROR**: `stores/admin/forum.ts:36-37` 的 `response.items` 用法
- **GOTCHA**: `total` 字段名不变，无需修改
- **VALIDATE**: `pnpm type-check` 通过

### Task 3: 修复 AuditLogViewer 适配新分页类型 [CRITICAL #1 续]
- **ACTION**: 更新 `AuditLogViewer.vue` 中 `response.logs` → `response.items`
- **IMPLEMENT**: 第 73 行 `auditLogs.value = response.logs || []` → `auditLogs.value = response.items || []`
- **VALIDATE**: `pnpm type-check` 通过

### Task 4: 后端新增 search 和 action 查询参数 [HIGH #3]
- **ACTION**: 在 `AuditLogQueryDTO.java` 新增 `search` 和 `action` 字段
- **IMPLEMENT**:
  ```java
  // 新增字段
  private String search;  // 全局搜索（匹配 action, entityType, entityId）
  private String action;  // 按动作类型过滤
  ```
- **MIRROR**: 现有字段的命名风格（camelCase, String type）
- **GOTCHA**: 不加 `sortBy`/`sortOrder`，这些在 Task 6 中从前端移除
- **VALIDATE**: `./mvnw compile -DskipTests` 通过

### Task 5: 后端 Service 新增 search/action 过滤逻辑 [HIGH #3 续]
- **ACTION**: 在 `AuditServiceImpl.getAuditLogs()` 中添加 search 和 action 条件
- **IMPLEMENT**:
  ```java
  // 在 wrapper 构建中新增
  if (query.getAction() != null) {
      wrapper.eq(AuditLog::getAction, query.getAction());
  }
  if (query.getSearch() != null && !query.getSearch().isBlank()) {
      wrapper.and(w -> w
          .like(AuditLog::getAction, query.getSearch())
          .or().like(AuditLog::getEntityType, query.getSearch())
          .or().like(AuditLog::getEntityId, query.getSearch())
      );
  }
  ```
- **MIRROR**: `AuditServiceImpl.java:52-69` 的条件构建模式
- **GOTCHA**: `search` 用 `and + or` 组合，确保搜索条件作为一个整体与其他条件 AND 连接
- **VALIDATE**: `./mvnw compile -DskipTests` 通过

### Task 6: 前端移除 sortBy/sortOrder 并统一查询参数 [HIGH #3 续]
- **ACTION**: 从 `AuditLogQueryParams` 移除 `sortBy` 和 `sortOrder`
- **IMPLEMENT**:
  1. `audit.ts`: 移除 `sortBy` 和 `sortOrder` 字段
  2. `AuditLogViewer.vue`: 移除 `sortBy` 和 `sortOrder` ref 及传参
- **GOTCHA**: `AuditLogViewer` 在第 50-51 行定义了这两个 ref，需一并移除
- **VALIDATE**: `pnpm type-check` 通过

### Task 7: 后端结构化统计响应 [HIGH #4]
- **ACTION**: 将 `AuditStatsVO` 中的 `Map<String, Object>` 替换为结构化 DTO
- **IMPLEMENT**:
  1. 创建 `EntityTypeStat.java`:
     ```java
     @Data
     public class EntityTypeStat {
         private String entityType;
         private Long count;
     }
     ```
  2. 创建 `PerformerStat.java`:
     ```java
     @Data
     public class PerformerStat {
         private String performerId;
         private String username;
         private String name;
         private String role;
         private Long count;
     }
     ```
  3. 修改 `AuditStatsVO.java`:
     ```java
     @Data
     public class AuditStatsVO {
         private Long totalActions;
         private List<EntityTypeStat> actionsByEntity;
         private List<PerformerStat> topPerformers;
     }
     ```
     移除 `actionsByPerformer` 字段
- **MIRROR**: `AuditLogVO.PerformerInfo` 的嵌套结构模式
- **GOTCHA**: `actionsByPerformer` 在前端 `AuditReportView` 第 60 行有使用，需同步修改前端
- **VALIDATE**: `./mvnw compile -DskipTests` 通过

### Task 8: 后端 Service 映射统计结果为结构化 DTO [HIGH #4 续]
- **ACTION**: 在 `AuditServiceImpl.getAuditStats()` 中将 Map 映射为 DTO
- **IMPLEMENT**:
  ```java
  // EntityTypeStat 映射
  List<EntityTypeStat> entityStats = auditLogMapper.selectStatsByEntityType(...)
      .stream().map(m -> {
          EntityTypeStat stat = new EntityTypeStat();
          stat.setEntityType((String) m.get("entityType"));
          stat.setCount(((Number) m.get("count")).longValue());
          return stat;
      }).collect(Collectors.toList());
  stats.setActionsByEntity(entityStats);

  // PerformerStat 映射 - 批量查用户信息
  List<Map<String, Object>> performerMaps = auditLogMapper.selectStatsByPerformer(...);
  Set<String> performerIds = performerMaps.stream()
      .map(m -> (String) m.get("performerId"))
      .collect(Collectors.toSet());
  Map<String, User> userMap = performerIds.isEmpty() ? Collections.emptyMap()
      : userMapper.selectBatchIds(performerIds).stream()
          .collect(Collectors.toMap(User::getId, u -> u));

  List<PerformerStat> topPerformers = performerMaps.stream().map(m -> {
      PerformerStat stat = new PerformerStat();
      stat.setPerformerId((String) m.get("performerId"));
      stat.setCount(((Number) m.get("count")).longValue());
      User user = userMap.get(stat.getPerformerId());
      if (user != null) {
          stat.setUsername(user.getUsername());
          stat.setName(user.getName());
          stat.setRole(user.getRole());
      }
      return stat;
  }).collect(Collectors.toList());
  stats.setTopPerformers(topPerformers);
  ```
- **MIRROR**: `AuditServiceImpl.java:77-92` 的批量查用户模式
- **GOTCHA**: `selectStatsByPerformer` 只返回 `performerId` 和 `count`，需要额外查用户表获取 username/name/role
- **VALIDATE**: `./mvnw compile -DskipTests` 通过

### Task 9: 前端对齐统计类型 [HIGH #4 续]
- **ACTION**: 更新前端 `AuditStats` 接口匹配后端新结构
- **IMPLEMENT**:
  ```typescript
  export interface AuditStats {
    totalActions: number
    actionsByEntity: Array<{
      entityType: string
      count: number
    }>
    topPerformers: Array<{
      performerId: string
      username: string
      name: string
      role: string
      count: number
    }>
  }
  ```
  移除 `actionsByPerformer` 字段
- **GOTCHA**: `AuditReportView` 第 58-69 行的 `topPerformers` computed 使用了 `actionsByPerformer`，需重写
- **VALIDATE**: `pnpm type-check` 通过

### Task 10: 修复 AuditReportView 适配新统计结构 [HIGH #4 续]
- **ACTION**: 重写 `topPerformers` computed 和模板
- **IMPLEMENT**:
  ```typescript
  // 替换第 58-69 行
  const topPerformers = computed(() => {
    if (!stats.value) return []
    return stats.value.topPerformers.slice(0, 5)
  })
  ```
  模板中 `item.performer.name` → `item.name`，`item.performer.username` → `item.username`，`item.performer.role` → `item.role`，`item.performer.id` → `item.performerId`
- **VALIDATE**: `pnpm type-check` 通过

### Task 11: 注册 AuditReportView 路由 [HIGH #5]
- **ACTION**: 在 `router/index.ts` 中添加 audit-report 路由
- **IMPLEMENT**:
  ```typescript
  // 在 audit 路由后添加
  {
    path: 'audit/report',
    name: 'audit-report',
    component: () => import('@/views/audit/AuditReportView.vue'),
    meta: { titleKey: 'nav.auditReport', permission: PERM.SYSTEM_READ },
  },
  ```
- **MIRROR**: `router/index.ts:48-52` 的 audit 路由格式
- **GOTCHA**: 路由需在 audit 路由之后，否则 `/audit/report` 可能被 `/audit` 匹配
- **VALIDATE**: `pnpm type-check` 通过

### Task 12: 后端新增导出端点 [CRITICAL #2]
- **ACTION**: 在 `AuditController` 添加 `/export` GET 端点
- **IMPLEMENT**:
  ```java
  @Operation(summary = "导出审计日志")
  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  public void exportAuditLogs(AuditLogQueryDTO query,
                               @RequestParam(defaultValue = "csv") String format,
                               HttpServletResponse response) throws IOException {
      List<AuditLogVO> logs = auditService.getAuditLogsForExport(query);

      if ("json".equalsIgnoreCase(format)) {
          response.setContentType("application/json");
          response.setHeader("Content-Disposition",
              "attachment; filename=audit-logs.json");
          objectMapper.writeValue(response.getOutputStream(), logs);
      } else {
          response.setContentType("text/csv");
          response.setHeader("Content-Disposition",
              "attachment; filename=audit-logs.csv");
          // CSV 写入
          PrintWriter writer = response.getWriter();
          writer.println("id,action,entityType,entityId,performer,ipAddress,createdAt");
          for (AuditLogVO log : logs) {
              writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                  log.getId(), log.getAction(), log.getEntityType(),
                  log.getEntityId(),
                  log.getPerformer() != null ? log.getPerformer().getUsername() : "",
                  log.getIpAddress() != null ? log.getIpAddress() : "",
                  log.getCreatedAt());
          }
          writer.flush();
      }
  }
  ```
- **MIRROR**: 其他 controller 的端点风格
- **IMPORTS**: `java.io.IOException`, `java.io.PrintWriter`, `jakarta.servlet.http.HttpServletResponse`, `com.fasterxml.jackson.databind.ObjectMapper`
- **GOTCHA**: 导出限制最大 10000 条防止内存溢出；需注入 `ObjectMapper`
- **VALIDATE**: `./mvnw compile -DskipTests` 通过

### Task 13: 后端 Service 新增导出查询方法 [CRITICAL #2 续]
- **ACTION**: 在 `AuditService` 接口和 `AuditServiceImpl` 中新增 `getAuditLogsForExport`
- **IMPLEMENT**:
  ```java
  // AuditService 接口
  List<AuditLogVO> getAuditLogsForExport(AuditLogQueryDTO query);

  // AuditServiceImpl - 复用 getAuditLogs 的 wrapper 逻辑，但无分页、限制 10000 条
  @Override
  public List<AuditLogVO> getAuditLogsForExport(AuditLogQueryDTO query) {
      // 复用 getAuditLogs 的 wrapper 构建逻辑
      LambdaQueryWrapper<AuditLog> wrapper = buildQueryWrapper(query);
      wrapper.orderByDesc(AuditLog::getCreatedAt);
      wrapper.last("LIMIT 10000");

      List<AuditLog> logs = auditLogMapper.selectList(wrapper);
      // 批量查用户... (复用 toVO 逻辑)
  }
  ```
- **GOTCHA**: 提取 `buildQueryWrapper` 为私有方法避免重复
- **VALIDATE**: `./mvnw compile -DskipTests` 通过

### Task 14: 前端修复导出 API 调用 [CRITICAL #2 续]
- **ACTION**: 修复 `auditApi.exportAuditLogs` 使用正确的请求方式
- **IMPLEMENT**:
  ```typescript
  async exportAuditLogs(params: AuditExportParams = {}): Promise<void> {
    const { format = 'csv', ...queryParams } = params
    // 使用 window.open 触发浏览器下载，避免 blob 处理
    const searchParams = new URLSearchParams()
    Object.entries(queryParams).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        searchParams.append(key, String(value))
      }
    })
    searchParams.append('format', format)
    // 需要带上 auth token - 使用 axios 获取 baseURL
    const baseUrl = '/api/admin/audit/export'
    const token = localStorage.getItem('token') // 或从 auth store 获取
    const url = `${baseUrl}?${searchParams.toString()}&token=${token}`
    window.open(url, '_blank')
  }
  ```
- **GOTCHA**: 直接 `window.open` 无法带 Bearer token header。改为用 axios + blob 下载方式更可靠
- **VALIDATE**: `pnpm type-check` 通过

### Task 15: 统一 Action 过滤选项 [MEDIUM #7]
- **ACTION**: `AuditLogViewer` 的 action 选项与 `AuditLogsView` 统一
- **IMPLEMENT**:
  1. 在 `AuditLogViewer.vue` 第 181-187 行，将 action 过滤选项改为与 AuditLogsView 一致：
     ```
     CREATE_USER, UPDATE_USER, DELETE_USER, BAN_USER, UNBAN_USER, GRANT_PERMISSION, REVOKE_PERMISSION
     ```
  2. 或更好的方式：从 `utils.ts` 导出统一的 action 列表常量
- **MIRROR**: `AuditLogsView.vue:355-371` 的 action 选项
- **GOTCHA**: AuditLogViewer 是可嵌入组件，可能需要更通用的选项，但后端只支持精确匹配 action 字段值
- **VALIDATE**: `pnpm type-check` 通过

### Task 16: Forum Audit 类型统一 [MEDIUM #8]
- **ACTION**: 将 `forum.ts` 中的 `AuditEntry` 替换为 `audit.ts` 的 `AuditLog`
- **IMPLEMENT**:
  1. 在 `forum.ts` 中删除 `AuditEntry` 接口（第 98-112 行）
  2. 导入 `AuditLog` from `@/api/admin/audit`
  3. 将 `AuditEntry` 引用替换为 `AuditLog`
  4. `ForumPostDetail.moderationHistory` 类型改为 `AuditLog[]`
  5. `forumStore.auditHistory` 类型改为 `AuditLog[]`
- **GOTCHA**: `AuditEntry.performer` 只有 `{id, username}`，而 `AuditLog.performer` 有 `{id, username, name, role}`。前端 `AuditLog` 的 performer 更完整，后端返回的 `AuditLogVO` 也包含完整信息，所以是向上兼容
- **VALIDATE**: `pnpm type-check` 通过

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|------|-------|-----------------|------------|
| 后端 getAuditLogs + search | search="CREATE" | 只返回 action 包含 CREATE 的记录 | 否 |
| 后端 getAuditLogs + action | action="BAN_USER" | 只返回 action=BAN_USER 的记录 | 否 |
| 后端 getAuditStats 结构化 | 正常查询 | 返回 EntityTypeStat/PerformerStat 对象 | 否 |
| 后端 export 限制 | 查询结果 > 10000 | 最多返回 10000 条 | 是 |
| 前端 type-check | 全量 | 0 type errors | 否 |

### Edge Cases Checklist
- [x] search 为空字符串 - 不应过滤
- [x] action 不存在 - 返回空列表
- [x] export 超 10000 条 - 限制返回
- [x] topPerformers 中用户已删除 - username/name 为 null

---

## Validation Commands

### 后端编译
```bash
cd backend-spring && ./mvnw compile -DskipTests
```
EXPECT: BUILD SUCCESS

### 前端类型检查
```bash
cd management && pnpm type-check
```
EXPECT: Zero type errors

### 前端 lint
```bash
cd management && pnpm lint
```
EXPECT: Zero errors

---

## Acceptance Criteria
- [ ] 前端使用 `PageResult<AuditLog>` (items/pageSize) 与后端对齐
- [ ] `/admin/audit/export` 端点可用，返回 CSV/JSON
- [ ] 后端支持 `search` 和 `action` 查询参数
- [ ] `AuditStatsVO` 使用结构化 DTO (EntityTypeStat/PerformerStat)
- [ ] `AuditReportView` 有路由 `/audit/report`
- [ ] 两个组件的 action 过滤选项一致
- [ ] Forum 复用 `AuditLog` 类型
- [ ] 后端编译通过，前端 type-check 通过，无 lint 错误

## Risks
| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| export 端点大数据量 OOM | Medium | High | LIMIT 10000 + 流式写入 |
| 前端改字段名遗漏消费处 | Low | Medium | 全局搜索 `.logs` 和 `.limit` |
| 结构化 DTO 改动影响前端解析 | Low | Medium | 前后端同步修改 |

## Notes
- 前端 `AuditLogsView` 的 `searchQuery` 目前通过 `watchDebounced` 触发 `loadLogs()`，但 `loadLogs` 并未将 `searchQuery` 传给 `fetchLogs`。修复 Task 1 后需确认 search 参数是否正确传递
- `AuditLogViewer` 直接调用 `auditApi.getAuditLogs()` 而非通过 store，这是一个独立的数据流
