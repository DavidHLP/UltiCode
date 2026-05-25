# Plan: Audit Module Frontend-Backend Alignment

## Summary
修复审计模块前后端对齐问题：后端新增按操作类型的统计端点，前端修复 stats ticker 数据来源错误，补充缺失的过滤参数，清理僵尸代码。

## User Story
As a 管理员, 我希望在审计日志页面看到准确的统计数据（而非仅当前页的统计），并能够使用所有后端支持的过滤维度，以便高效定位和审计系统操作。

## Problem → Solution
**当前**: AuditLogsView 的 create/update/delete/other 统计来自当前页 50 条记录（total=10000 但 breakdown=50），userId/entityId 过滤参数未暴露给用户，countDailyActiveUsers 为僵尸代码
**目标**: 统计数据来自后端全量聚合，所有过滤参数可用，僵尸代码被清理

## Metadata
- **Complexity**: Medium
- **Source PRD**: `docs/audit-frontend-backend-alignment-analysis.md`
- **PRD Phase**: N/A (standalone)
- **Estimated Files**: 12

---

## UX Design

### Before
```
┌──────────────────────────────────────────────────────┐
│  Audit Logs                                          │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌───────┐        │
│  │Total   │ │Create  │ │Update  │ │Delete │ ...     │
│  │ 10,247 │ │  12    │ │  23    │ │  8    │ ← 仅当  │
│  └────────┘ └────────┘ └────────┘ └───────┘   前页! │
│  [Search ▼] [Action ▼] [Entity ▼]                  │
│  ┌──────────────────────────────────────────────┐    │
│  │ DataTable (50 rows per page)                 │    │
│  └──────────────────────────────────────────────┘    │
│  无法按 userId / entityId / 日期范围 筛选             │
└──────────────────────────────────────────────────────┘
```

### After
```
┌──────────────────────────────────────────────────────┐
│  Audit Logs                                          │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌───────┐        │
│  │Total   │ │Create  │ │Update  │ │Delete │ ...     │
│  │ 10,247 │ │ 4,521  │ │ 3,892  │ │ 1,834 │ ← 全量 │
│  └────────┘ └────────┘ └────────┘ └───────┘   聚合! │
│  [Search ▼] [Action ▼] [Entity ▼] [Date ▼]         │
│  [Performer ▼] [Target User ▼]                      │
│  ┌──────────────────────────────────────────────┐    │
│  │ DataTable (50 rows per page)                 │    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Stats ticker | 页级客户端过滤 | 后端聚合数据 | P0 修复 |
| 日期范围过滤 | 仅 ReportView 有 | LogsView 也有 | P1 修复 |
| Performer 过滤 | 仅 ReportView 有 | LogsView 也有 | P1 修复 |
| userId 过滤 | 不存在 | 新增输入框 | P1 修复 |
| entityId 过滤 | 不存在 | 新增输入框（高级筛选） | P1 修复 |
| countDailyActiveUsers | 僵尸代码 | 移除 | P1 清理 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/.../admin/service/impl/AuditServiceImpl.java` | all | 核心统计逻辑 |
| P0 | `management/src/views/audit/AuditLogsView.vue` | all | 主要修改目标 |
| P0 | `management/src/stores/admin/audit.ts` | all | Store 状态管理 |
| P1 | `backend-spring/.../admin/dto/AuditStatsVO.java` | all | Stats VO 结构 |
| P1 | `backend-spring/.../admin/mapper/AuditLogMapper.java` | all | 需新增查询方法 |
| P1 | `management/src/views/audit/utils.ts` | all | 常量和图标映射 |
| P2 | `backend-spring/.../admin/controller/AdminAuditLogController.java` | all | 端点定义 |

## External Documentation

No external research needed — feature uses established internal patterns.

---

## Patterns to Mirror

### SERVICE_PATTERN
// SOURCE: backend-spring/.../admin/service/impl/AuditServiceImpl.java
```java
@Override
public AuditStatsVO getAuditStats(AuditLogQueryDTO queryDTO) {
    AuditStatsVO stats = new AuditStatsVO();
    stats.setTotalActions(auditLogMapper.selectCount(
        new LambdaQueryWrapper<AuditLog>()
            .like(StringUtils.hasText(queryDTO.getSearch()), AuditLog::getAction, queryDTO.getSearch())
            .eq(StringUtils.hasText(queryDTO.getEntityType()), AuditLog::getEntityType, queryDTO.getEntityType())
            // ... more filters
    ));
    stats.setActionsByEntity(auditLogMapper.selectStatsByEntityType(queryDTO));
    stats.setTopPerformers(auditLogMapper.selectStatsByPerformer(queryDTO));
    return stats;
}
```

### MAPPER_STATS_PATTERN
// SOURCE: backend-spring/.../admin/mapper/AuditLogMapper.java
```java
@Select("<script>" +
    "SELECT entity_type AS entityType, COUNT(*) AS count " +
    "FROM audit_log " +
    "<where>" +
    "  <if test='startDate != null'>AND created_at &gt;= #{startDate}</if>" +
    "  <if test='endDate != null'>AND created_at &lt;= #{endDate}</if>" +
    "  <if test='performerId != null'>AND performer_id = #{performerId}</if>" +
    "  <if test='userId != null'>AND user_id = #{userId}</if>" +
    "  <if test='entityType != null'>AND entity_type = #{entityType}</if>" +
    "  <if test='action != null'>AND action = #{action}</if>" +
    "  <if test='search != null'>AND (action LIKE CONCAT('%',#{search},'%') OR entity_type LIKE CONCAT('%',#{search},'%'))</if>" +
    "</where>" +
    "GROUP BY entity_type ORDER BY count DESC LIMIT 10" +
    "</script>")
List<AuditStatsVO.EntityTypeStat> selectStatsByEntityType(AuditLogQueryDTO queryDTO);
```

### DTO_INNER_CLASS_PATTERN
// SOURCE: backend-spring/.../admin/dto/AuditStatsVO.java
```java
@Data
public class AuditStatsVO {
    private Long totalActions;
    private List<EntityTypeStat> actionsByEntity;
    private List<PerformerStat> topPerformers;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EntityTypeStat {
        private String entityType;
        private Long count;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PerformerStat {
        private String performerId;
        private String username;
        private String name;
        private String role;
        private Long count;
    }
}
```

### STORE_PATTERN
// SOURCE: management/src/stores/admin/audit.ts
```typescript
export const useAuditStore = defineStore('admin-audit', () => {
  const logs = ref<AuditLog[] | null>(null)
  const stats = ref<AuditStats | null>(null)
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchLogs(params: AuditLogQueryParams) {
    loading.value = true; error.value = null
    try {
      const normalizedParams = normalizeDateParams(params)
      const { data } = await auditApi.getAuditLogs(normalizedParams)
      logs.value = data?.records ?? null
      total.value = data?.total ?? 0
    } catch (e: any) { error.value = e.message } finally { loading.value = false }
  }
  // ...
})
```

### FILTER_DROPDOWN_PATTERN
// SOURCE: management/src/views/audit/AuditLogsView.vue
```vue
<Select v-model="actionFilter" @update:model-value="handleFilter">
  <SelectTrigger class="w-[180px]">
    <SelectTriggerContent :placeholder="t('audit.filters.action')" />
  </SelectTrigger>
  <SelectContent>
    <SelectItem value="ALL">{{ t('audit.filters.all') }}</SelectItem>
    <SelectGroup v-for="(actions, group) in AUDIT_ACTIONS_BY_ENTITY" :key="group">
      <SelectLabel>{{ t(`audit.entityGroups.${group}`) }}</SelectLabel>
      <SelectItem v-for="act in actions" :key="act" :value="act">
        {{ t(`audit.actionTypes.${act}`) }}
      </SelectItem>
    </SelectGroup>
  </SelectContent>
</Select>
```

### STATS_TICKER_PATTERN
// SOURCE: management/src/views/audit/AuditLogsView.vue
```vue
<div class="grid grid-cols-2 gap-4 md:grid-cols-5">
  <Card v-for="item in statsCards" :key="item.key">
    <CardContent class="p-4">
      <div class="text-sm text-muted-foreground">{{ item.label }}</div>
      <div class="text-2xl font-bold">{{ item.value.toLocaleString() }}</div>
    </CardContent>
  </Card>
</div>
```

### ERROR_HANDLING
// SOURCE: management/src/stores/admin/audit.ts
```typescript
catch (e: any) {
  error.value = e.response?.data?.message || e.message || 'Failed to fetch audit logs'
} finally {
  loading.value = false
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/.../admin/dto/AuditStatsVO.java` | UPDATE | 新增 ActionTypeStat 内部类和 actionsByType 字段 |
| `backend-spring/.../admin/mapper/AuditLogMapper.java` | UPDATE | 新增 selectStatsByActionType 方法 |
| `backend-spring/.../admin/service/impl/AuditServiceImpl.java` | UPDATE | getAuditStats 中填充 actionsByType |
| `management/src/api/admin/audit.ts` | UPDATE | AuditStats 类型新增 actionsByType |
| `management/src/stores/admin/audit.ts` | UPDATE | fetchStats 清理分页参数 |
| `management/src/views/audit/AuditLogsView.vue` | UPDATE | stats 改用后端数据，新增过滤器 |
| `management/src/views/audit/utils.ts` | UPDATE | 新增操作类型分组常量 |
| `management/src/i18n/locales/en-US/modules/audit.ts` | UPDATE | 新增翻译 key |
| `management/src/i18n/locales/zh-CN/modules/audit.ts` | UPDATE | 新增翻译 key |
| `backend-spring/.../admin/mapper/AuditLogMapper.java` | UPDATE | 移除 countDailyActiveUsers |

## NOT Building

- 日活趋势图 (无前端可视化需求)
- AuditReportView 与 AuditLogsView 合并 (超出当前范围)
- auditReport i18n key 集中化 (独立优化项)
- oldValues/newValues 类型改进 (P2 级别)
- createdAt 类型对齐 (P2 级别)

---

## Step-by-Step Tasks

### Task 1: 后端 — AuditStatsVO 新增 ActionTypeStat
- **ACTION**: 在 AuditStatsVO 中新增内部类和字段
- **IMPLEMENT**:
  ```java
  // 在 AuditStatsVO 类中新增字段
  private List<ActionTypeStat> actionsByType;

  // 新增内部类 (放在 PerformerStat 之后)
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ActionTypeStat {
      private String actionType;
      private Long count;
  }
  ```
- **MIRROR**: DTO_INNER_CLASS_PATTERN (与 EntityTypeStat/PerformerStat 相同的 @Data @AllArgsConstructor @NoArgsConstructor 结构)
- **IMPORTS**: 无新导入
- **GOTCHA**: 字段命名 `actionsByType` 与现有的 `actionsByEntity` 保持平行结构
- **VALIDATE**: `./mvnw compile` 通过

### Task 2: 后端 — AuditLogMapper 新增 selectStatsByActionType
- **ACTION**: 在 AuditLogMapper 中新增按操作类型分组的查询方法
- **IMPLEMENT**:
  ```java
  @Select("<script>" +
      "SELECT " +
      "  CASE " +
      "    WHEN action LIKE 'CREATE%' THEN 'CREATE' " +
      "    WHEN action LIKE 'UPDATE%' THEN 'UPDATE' " +
      "    WHEN action LIKE 'DELETE%' THEN 'DELETE' " +
      "    WHEN action LIKE 'FLAG%' THEN 'FLAG' " +
      "    WHEN action LIKE 'UNFLAG%' THEN 'UNFLAG' " +
      "    WHEN action LIKE 'BAN%' THEN 'BAN' " +
      "    WHEN action LIKE 'UNBAN%' THEN 'UNBAN' " +
      "    WHEN action LIKE 'GRANT%' THEN 'GRANT' " +
      "    WHEN action LIKE 'REVOKE%' THEN 'REVOKE' " +
      "    WHEN action LIKE 'RESET%' THEN 'RESET' " +
      "    WHEN action LIKE 'PIN%' THEN 'PIN' " +
      "    WHEN action LIKE 'UNPIN%' THEN 'UNPIN' " +
      "    WHEN action LIKE 'LOCK%' THEN 'LOCK' " +
      "    WHEN action LIKE 'UNLOCK%' THEN 'UNLOCK' " +
      "    WHEN action LIKE 'REQUEUE%' THEN 'REQUEUE' " +
      "    WHEN action LIKE 'MODERATE%' THEN 'MODERATE' " +
      "    ELSE 'OTHER' " +
      "  END AS actionType, " +
      "  COUNT(*) AS count " +
      "FROM audit_log " +
      "<where>" +
      "  <if test='startDate != null'>AND created_at &gt;= #{startDate}</if>" +
      "  <if test='endDate != null'>AND created_at &lt;= #{endDate}</if>" +
      "  <if test='performerId != null'>AND performer_id = #{performerId}</if>" +
      "  <if test='userId != null'>AND user_id = #{userId}</if>" +
      "  <if test='entityType != null'>AND entity_type = #{entityType}</if>" +
      "  <if test='action != null'>AND action = #{action}</if>" +
      "  <if test='search != null'>AND (action LIKE CONCAT('%',#{search},'%') OR entity_type LIKE CONCAT('%',#{search},'%'))</if>" +
      "</where>" +
      "GROUP BY actionType ORDER BY count DESC" +
      "</script>")
  List<AuditStatsVO.ActionTypeStat> selectStatsByActionType(AuditLogQueryDTO queryDTO);
  ```
- **MIRROR**: MAPPER_STATS_PATTERN (与 selectStatsByEntityType 完全相同的 @Select + 动态 SQL 结构)
- **IMPORTS**: 无新导入
- **GOTCHA**: CASE WHEN 中的 LIKE 匹配顺序很重要 — 先匹配更具体的 (如 UNBAN 在 BAN 之前)，但由于 SQL CASE 是顺序求值且 UNBAN/UNFLAG 等前缀与 BAN/FLAG 不以相同前缀开头，所以顺序不影响结果。但保险起见将 UN* 前缀放在对应动作之前。
- **VALIDATE**: `./mvnw compile` 通过

### Task 3: 后端 — AuditServiceImpl 填充 actionsByType
- **ACTION**: 在 getAuditStats 方法中调用新 mapper 方法并填充 VO
- **IMPLEMENT**:
  ```java
  // 在 getAuditStats 方法中，setTopPerformers 之后新增:
  stats.setActionsByType(auditLogMapper.selectStatsByActionType(queryDTO));
  ```
- **MIRROR**: SERVICE_PATTERN (与 setActionsByEntity 和 setTopPerformers 相同的调用模式)
- **IMPORTS**: 无新导入
- **GOTCHA**: 无
- **VALIDATE**: `./mvnw compile` 通过

### Task 4: 后端 — 移除 countDailyActiveUsers 僵尸代码
- **ACTION**: 从 AuditLogMapper 中移除未使用的方法
- **IMPLEMENT**: 删除 `countDailyActiveUsers` 方法的 `@Select` 注解和方法声明
- **MIRROR**: N/A (代码删除)
- **IMPORTS**: N/A
- **GOTCHA**: 先全局搜索确认无其他调用方再删除
- **VALIDATE**: `./mvnw compile` 通过; `grep -r "countDailyActiveUsers" backend-spring/` 返回空

### Task 5: 前端 — AuditStats 类型新增 actionsByType
- **ACTION**: 在 `management/src/api/admin/audit.ts` 中更新 AuditStats 接口
- **IMPLEMENT**:
  ```typescript
  // 在 AuditStats 接口中新增:
  actionsByType: Array<{ actionType: string; count: number }>
  ```
- **MIRROR**: 现有 actionsByEntity/topPerformers 的 Array<{}> 模式
- **IMPORTS**: 无新导入
- **GOTCHA**: 无
- **VALIDATE**: `cd management && pnpm type-check` 通过

### Task 6: 前端 — Store fetchStats 清理分页参数
- **ACTION**: 在 `management/src/stores/admin/audit.ts` 的 fetchStats 中，发送请求前移除 page/limit
- **IMPLEMENT**:
  ```typescript
  // fetchStats 方法中，normalizeDateParams 之前:
  const { page: _p, limit: _l, ...statsParams } = params
  const normalizedParams = normalizeDateParams(statsParams as AuditLogQueryParams)
  ```
- **MIRROR**: 现有 store 的参数解构模式
- **IMPORTS**: 无新导入
- **GOTCHA**: TypeScript 解构时 page/limit 可能是 undefined，需用类型断言
- **VALIDATE**: `cd management && pnpm type-check` 通过

### Task 7: 前端 — utils.ts 新增操作类型分组常量
- **ACTION**: 在 `management/src/views/audit/utils.ts` 中新增 ACTION_TYPE_GROUPS
- **IMPLEMENT**:
  ```typescript
  export const ACTION_TYPE_GROUPS: Record<string, string> = {
    CREATE: 'CREATE',
    UPDATE: 'UPDATE',
    DELETE: 'DELETE',
    FLAG: 'FLAG',
    UNFLAG: 'UNFLAG',
    BAN: 'BAN',
    UNBAN: 'UNBAN',
    GRANT: 'GRANT',
    REVOKE: 'REVOKE',
    RESET: 'RESET',
    PIN: 'PIN',
    UNPIN: 'UNPIN',
    LOCK: 'LOCK',
    UNLOCK: 'UNLOCK',
    REQUEUE: 'REQUEUE',
    MODERATE: 'MODERATE',
    OTHER: 'OTHER',
  }
  ```
- **MIRROR**: 现有 AUDIT_ENTITY_TYPES 的 Record 结构
- **IMPORTS**: 无新导入
- **GOTCHA**: 这里的 key 既是 i18n 翻译 key 也是后端返回的 actionType 值
- **VALIDATE**: `cd management && pnpm type-check` 通过

### Task 8: 前端 — AuditLogsView stats 改用后端数据
- **ACTION**: 重写 AuditLogsView 的 stats computed，从后端 actionsByType 读取
- **IMPLEMENT**:
  ```typescript
  // 替换现有的 stats computed:
  const stats = computed(() => {
    const s = auditStore.stats
    if (!s) {
      return { total: auditStore.total, create: 0, update: 0, delete: 0, other: 0 }
    }
    const byType = Object.fromEntries(s.actionsByType?.map(i => [i.actionType, i.count]) ?? [])
    return {
      total: s.totalActions,
      create: byType.CREATE ?? 0,
      update: byType.UPDATE ?? 0,
      delete: byType.DELETE ?? 0,
      other: byType.OTHER ?? 0,
    }
  })
  ```
- **MIRROR**: STATS_TICKER_PATTERN (statsCards 数组结构保持不变)
- **IMPORTS**: 无新导入
- **GOTCHA**: actionsByType 是新增字段，旧后端可能不返回，需用 `?.` 可选链
- **VALIDATE**: 启动前端，查看 stats ticker 数字是否为全量聚合值

### Task 9: 前端 — AuditLogsView 新增过滤器
- **ACTION**: 在 AuditLogsView 的 toolbar-left 区域新增日期范围、performerId、userId 过滤器
- **IMPLEMENT**:
  - 新增 DatePicker range 组件用于日期筛选 (复用 AuditReportView 中的日期选择器模式)
  - 新增 performerId 输入框 (简单的 Input 组件)
  - 新增 userId 输入框 (可折叠的高级筛选区域)
  - 更新 handleFilter 和 fetchLogs 调用传递新参数
- **MIRROR**: FILTER_DROPDOWN_PATTERN (Select 组件模式); AuditReportView 的 DatePicker 模式
- **IMPORTS**: 从 shadcn-vue 引入 DatePicker / Input / Collapsible 组件
- **GOTCHA**: 日期参数需要通过 normalizeDateParams 处理; 需确保与现有 search/action/entityType 过滤器共存
- **VALIDATE**: 启动前端，测试每个过滤器组合

### Task 10: 前端 — i18n 新增翻译 key
- **ACTION**: 在 en-US 和 zh-CN 的 audit.ts 中新增操作类型和过滤器翻译
- **IMPLEMENT**:
  ```typescript
  // en-US 新增:
  actionTypes: {
    // ... existing keys ...
    CREATE: 'Create',
    UPDATE: 'Update',
    DELETE: 'Delete',
    FLAG: 'Flag',
    UNFLAG: 'Unflag',
    BAN: 'Ban',
    UNBAN: 'Unban',
    GRANT: 'Grant',
    REVOKE: 'Revoke',
    RESET: 'Reset',
    PIN: 'Pin',
    UNPIN: 'Unpin',
    LOCK: 'Lock',
    UNLOCK: 'Unlock',
    REQUEUE: 'Requeue',
    MODERATE: 'Moderate',
    OTHER: 'Other',
  },
  filters: {
    // ... existing keys ...
    dateRange: 'Date Range',
    performerId: 'Performer ID',
    userId: 'Target User ID',
    advancedFilters: 'Advanced Filters',
  }

  // zh-CN 新增:
  actionTypes: {
    // ... existing keys ...
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    FLAG: '标记',
    UNFLAG: '取消标记',
    BAN: '封禁',
    UNBAN: '解封',
    GRANT: '授权',
    REVOKE: '撤销',
    RESET: '重置',
    PIN: '置顶',
    UNPIN: '取消置顶',
    LOCK: '锁定',
    UNLOCK: '解锁',
    REQUEUE: '重新排队',
    MODERATE: '审核',
    OTHER: '其他',
  },
  filters: {
    // ... existing keys ...
    dateRange: '日期范围',
    performerId: '操作者 ID',
    userId: '目标用户 ID',
    advancedFilters: '高级筛选',
  }
  ```
- **MIRROR**: 现有 audit.ts 的 key 组织结构
- **IMPORTS**: 无
- **GOTCHA**: 注意 en-US 和 zh-CN 的 actionTypes 中已存在 CREATE_USER 等完整 action 名的翻译，新增的 CREATE/UPDATE/DELETE 等是"类型"级别（简短），不与现有的 action 名冲突
- **VALIDATE**: 浏览器中切换语言确认翻译正确

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| selectStatsByActionType with no filters | 空查询 | 返回所有 action type 分组统计 | 否 |
| selectStatsByActionType with entityType filter | entityType=USER | 仅返回 USER 相关的 action type 统计 | 是 |
| selectStatsByActionType with date range | startDate + endDate | 仅返回范围内统计 | 是 |
| AuditStatsVO serialization | 含 actionsByType 的对象 | JSON 包含 actionsByType 字段 | 否 |
| 前端 stats computed (有后端数据) | actionsByType=[{CREATE, 100}] | create=100 | 否 |
| 前端 stats computed (无后端数据) | stats=null | create=0, total=store.total | 是 |

### Edge Cases Checklist
- [x] 空 actionsByType (后端无数据时)
- [x] 未知 actionType (不在预定义映射中)
- [x] 日期范围跨时区
- [ ] 超大结果集性能 (100万+审计日志)

---

## Validation Commands

### Static Analysis
```bash
cd backend-spring && ./mvnw compile
```
EXPECT: Zero compilation errors

```bash
cd management && pnpm type-check
```
EXPECT: Zero type errors

### Unit Tests
```bash
cd backend-spring && ./mvnw test -pl . -Dtest="AuditLog*"
```
EXPECT: All tests pass

```bash
cd management && pnpm test
```
EXPECT: All tests pass

### Lint
```bash
cd management && pnpm lint
```
EXPECT: No lint errors

### Browser Validation
```bash
pm2 restart ulticode-9001 ulticode-9003
# 然后访问 http://localhost:9003/audit
```
EXPECT:
- Stats ticker 显示全量聚合数字 (total 与 create+update+delete+other 之和相近)
- 日期范围过滤器可用
- Performer ID 过滤器可用
- Target User ID 过滤器可用

### Manual Validation
- [ ] Stats ticker 的 total 值与 create+update+delete+other 值之和数量级一致
- [ ] 翻页后 stats ticker 数字不变 (不再随页变化)
- [ ] 日期范围筛选能正确过滤日志
- [ ] Performer ID 输入后能过滤日志
- [ ] userId 输入后能过滤日志
- [ ] 切换中英文翻译正确
- [ ] 浏览器控制台无 i18n missing key 警告

---

## Acceptance Criteria
- [ ] 后端 stats 端点返回 actionsByType 字段
- [ ] 前端 stats ticker 使用后端聚合数据而非页级数据
- [ ] AuditLogsView 新增日期范围、performerId、userId 过滤器
- [ ] countDailyActiveUsers 僵尸代码已移除
- [ ] fetchStats 不再发送分页参数
- [ ] i18n 双语翻译完整
- [ ] 所有 type-check 和 lint 通过

## Completion Checklist
- [ ] 代码遵循已发现的模式
- [ ] 错误处理与 codebase 风格一致
- [ ] 无硬编码值
- [ ] 无不必要的范围扩展
- [ ] 自包含 — 实施期间无需搜索 codebase

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| SQL CASE WHEN 未覆盖所有 action 前缀 | Low | Medium | ELSE 'OTHER' 兜底；可对照 AuditActionUtil 验证 |
| 大数据量下 GROUP BY 性能 | Medium | Low | 现有 selectStatsByEntityType 已有相同模式且运行正常 |
| 前端新过滤器 UI 空间不足 | Low | Low | 使用折叠式高级筛选区域 |
| 后端部署后前端旧版不兼容 | Low | Medium | actionsByType 使用 `?.` 可选链，旧版前端忽略新字段 |

## Notes
- 后端 SQL 中 CASE WHEN 的前缀匹配与 AuditActionUtil 中定义的 41 个 action 常量完全对应
- UNBAN/UNFLAG/UNPIN/UNLOCK 等前缀需要先于 BAN/FLAG/PIN/LOCK 匹配，但由于这些前缀不共享前缀 (UNBAN ≠ BAN*)，SQL LIKE 匹配不受顺序影响
- 前端 stats computed 中 `actionsByType?.map` 的可选链确保了向后兼容
