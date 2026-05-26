# Plan: Audit Frontend-Backend Alignment Fix

## Summary
修复 audit 模块前后端颗粒度不对齐问题：TanStack Table 列 ID 导致 "Invalid Date"、Stats ticker 硬编码分类、筛选器默认值不一致、日期归一化缺失、EntityType 命名风格分裂。

## User Story
As a 管理员, I want 审计日志页面的时间列正确显示、统计分类完整反映操作类型、筛选器行为一致, so that 我能准确追踪系统操作历史。

## Problem → Solution
[createdAt 全部显示 "Invalid Date"，stats 只显示 3 类操作，Report 页筛选器会发送错误查询] → [所有列正确渲染，stats 动态展示全部 actionType，两页筛选器行为统一]

## Metadata
- **Complexity**: Medium
- **Source PRD**: `docs/audit-frontend-backend-alignment-analysis.md`
- **PRD Phase**: N/A (standalone)
- **Estimated Files**: 6

---

## UX Design

### Before
```
┌─ audit-logs ──────────────────────────────────────┐
│ Total: 9  Create: 0  Update: 1  Delete: 0  Other: 8 │
├──────────┬─────────┬──────────┬──────────┬───────┤
│Invalid Dt│UNLOCK_P │FORUM_POST│admin     │127... │
│Invalid Dt│LOCK_POS │FORUM_POST│admin     │127... │
│Invalid Dt│UNPIN_PO │FORUM_POST│admin     │127... │
└──────────┴─────────┴──────────┴──────────┴───────┘
  ↑ 时间列全部损坏  ↑ Stats 只有 3 类，8 条归 other
```

### After
```
┌─ audit-logs ────────────────────────────────────────┐
│ Total: 9  CREATE: 0  UPDATE: 1  DELETE: 0  BAN: 2   │
│ UNBAN: 1  PIN: 1  UNPIN: 1  LOCK: 1  UNLOCK: 1     │
│ RESET: 1                                              │
├──────────────────┬──────────┬──────────┬───────┬────┤
│ 5/24/2026, 5:38 │ UNLOCK_P │FORUM_POST│admin  │127 │
│ 5/24/2026, 5:38 │ LOCK_POS │FORUM_POST│admin  │127 │
│ 5/24/2026, 5:38 │ UNPIN_PO │FORUM_POST│admin  │127 │
└──────────────────┴──────────┴──────────┴───────┴────┘
  ↑ 时间正确显示  ↑ Stats 展示全部 actionType
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| createdAt 列 | "Invalid Date" | 正确日期时间 | 修复列 ID/accessorKey 不匹配 |
| Stats ticker | 硬编码 CREATE/UPDATE/DELETE + other | 动态展示所有 actionType | 完整反映操作类型分布 |
| Report 页筛选器 | `__all__` + 空字符串默认值 | `'all'` → `undefined` | 与 Logs 页一致，避免空字符串查询 bug |
| 日期筛选 | AuditLogsView 未归一化 | 两页均调用 normalizeDateParams | 结束日期包含当天全部数据 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `management/src/views/audit/AuditLogsView.vue` | 140-291 | 列定义 — 需修改 id/accessorKey |
| P0 | `management/src/views/audit/AuditReportView.vue` | 25-84 | 筛选器默认值 + normalizeDateParams |
| P1 | `management/src/views/audit/utils.ts` | all | AUDIT_ACTIONS_BY_ENTITY key 命名 |
| P1 | `management/src/api/admin/audit.ts` | 75-92, 103-118 | AuditStats 类型 + normalizeDateParams |
| P2 | `management/src/i18n/locales/zh-CN/modules/audit.ts` | all | i18n key 需匹配新命名 |
| P2 | `management/src/i18n/locales/en-US/modules/audit.ts` | all | 同上英文版 |
| P2 | `management/src/i18n/locales/zh-CN/modules/table.ts` | 50-115 | DataTable 列名 i18n |

---

## Patterns to Mirror

### COLUMN_DEF_PATTERN (其他视图的正确做法)
// SOURCE: management/src/views/notifications/NotificationsListView.vue:179
```typescript
{
  accessorKey: 'createdAt',
  header: () => t('notifications.sentAt'),
  cell: ({ row }) =>
    h('span', { class: 'font-data text-sm text-[var(--silver-500)] tabular-nums' },
      new Date(row.getValue('createdAt') as string).toLocaleString()
    ),
}
```
**关键**: 只用 `accessorKey`，不设 `id`。accessorKey 同时充当列 ID 和数据访问键。

### FILTER_DEFAULT_PATTERN (AuditLogsView 的正确做法)
// SOURCE: management/src/views/audit/AuditLogsView.vue:54-96
```typescript
const actionFilter = ref<string>('all')
// ...
action: actionFilter.value === 'all' ? undefined : actionFilter.value,
```
**关键**: 默认值 `'all'`，传 API 时转为 `undefined`，后端收到 null 则跳过该条件。

### DATE_NORMALIZATION_PATTERN
// SOURCE: management/src/api/admin/audit.ts:103-118
```typescript
export function normalizeDateParams<T extends { startDate?: string; endDate?: string }>(
  params: T,
): T {
  const p = { ...params }
  if (p.startDate && p.startDate.length === 10) {
    p.startDate = `${p.startDate}T00:00:00`
  }
  if (p.endDate && p.endDate.length === 10) {
    const next = new Date(p.endDate + 'T00:00:00')
    next.setDate(next.getDate() + 1)
    // ... format to YYYY-MM-DDT00:00:00
    p.endDate = `${next.getFullYear()}-${mm}-${dd}T00:00:00`
  }
  return p
}
```
**关键**: startDate 补 T00:00:00，endDate 推到次日（exclusive upper bound）。

### I18N_KEY_PATTERN (DataTable 列名翻译)
// SOURCE: management/src/components/table/DataTable.vue:84-93
```typescript
function resolveColumnName(columnId: string): string {
  const name = t(`table.columnNames.${columnId}`, columnId)
  // ...
}
```
**关键**: columnId 直接拼接为 `table.columnNames.${columnId}`，所以 columnId 必须在 table.ts 的 columnNames 中有对应 key。

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `management/src/views/audit/AuditLogsView.vue` | UPDATE | 修复列 ID、stats ticker、添加 normalizeDateParams |
| `management/src/views/audit/AuditReportView.vue` | UPDATE | 修复筛选器默认值，统一 `'all'` 模式 |
| `management/src/views/audit/utils.ts` | UPDATE | 统一 AUDIT_ACTIONS_BY_ENTITY key 为大写 |
| `management/src/api/admin/audit.ts` | UPDATE | 改 oldValues/newValues 类型为 Record |
| `management/src/i18n/locales/zh-CN/modules/audit.ts` | UPDATE | 补充 entityGroups 大写 key |
| `management/src/i18n/locales/en-US/modules/audit.ts` | UPDATE | 同上英文版 |

## NOT Building
- 后端 Java 代码修改（所有问题均在客户端侧修复）
- 导出按钮 UI（LOW 优先级，单独任务）
- countDailyActiveUsers 时间线图表（LOW 优先级，单独任务）
- 后端日期格式添加 timezone offset（需要后端改动，另案处理）

---

## Step-by-Step Tasks

### Task 1: 修复 TanStack Table 列 ID/accessorKey 不匹配
- **ACTION**: 移除 AuditLogsView.vue 中所有列定义的显式 `id` 字段，让 accessorKey 自动充当列 ID
- **IMPLEMENT**:
  ```typescript
  // Before:
  { accessorKey: 'createdAt', id: 'created_at', ... }
  { accessorKey: 'entityType', id: 'entity_type', ... }
  { accessorKey: 'ipAddress', id: 'ip_address', ... }

  // After:
  { accessorKey: 'createdAt', ... }
  { accessorKey: 'entityType', ... }
  { accessorKey: 'ipAddress', ... }
  ```
  移除 6 个列定义中的 `id` 字段（createdAt、action、entityType、performer、user、ipAddress）。action/performer/user 的 id 与 accessorKey 相同，移除无副作用。createdAt/entityType/ipAddress 的 id 与 accessorKey 不同，移除后修复断裂。
- **MIRROR**: COLUMN_DEF_PATTERN — 通知列表只用 accessorKey
- **IMPORTS**: 无新增
- **GOTCHA**: 移除 `id` 后，DataTable 的 `resolveColumnName(columnId)` 使用 accessorKey 值作为 columnId。当前 table.ts 已有 `createdAt`、`entityType`、`ipAddress` 的 camelCase key（第 75、90、111 行），所以 i18n 翻译不会断。
- **VALIDATE**: 浏览器打开 `/audit`，createdAt 列显示正确日期而非 "Invalid Date"，控制台无 `[Table] Column with id 'createdAt' does not exist` 错误。

### Task 2: 修复 Stats ticker 动态展示全部 actionType
- **ACTION**: 重写 AuditLogsView.vue 中 `stats` computed，从硬编码 3 类改为动态渲染
- **IMPLEMENT**:
  ```typescript
  // Before: 硬编码 CREATE/UPDATE/DELETE + other
  const stats = computed(() => {
    const s = auditStore.stats
    if (!s) return { total: auditStore.total, create: 0, update: 0, delete: 0, other: 0 }
    const byType = Object.fromEntries(s.actionsByType?.map((i) => [i.actionType, i.count]) ?? [])
    return {
      total: s.totalActions,
      create: byType.CREATE ?? 0,
      update: byType.UPDATE ?? 0,
      delete: byType.DELETE ?? 0,
      other: s.totalActions - (byType.CREATE ?? 0) - (byType.UPDATE ?? 0) - (byType.DELETE ?? 0),
    }
  })

  // After: 直接传递 actionsByType 数组给模板
  const actionTypeStats = computed(() => auditStore.stats?.actionsByType ?? [])
  ```
  模板中 stats ticker 从固定 4 个 span 改为 `v-for` 遍历 `actionTypeStats`，每个显示 `t(\`audit.actionTypeGroups.${item.actionType}\`)` + count。保留 total 显示。
- **MIRROR**: AuditReportView.vue:186-198 已有 `v-for` 遍历 `actionsByType` 的模式
- **IMPORTS**: 无新增
- **GOTCHA**: i18n `audit.actionTypeGroups` 已覆盖全部 16 种 actionType（CREATE, UPDATE, DELETE, FLAG, UNFLAG, BAN, UNBAN, GRANT, REVOKE, RESET, PIN, UNPIN, LOCK, UNLOCK, REQUEUE, MODERATE, OTHER），无需新增 key。
- **VALIDATE**: Stats ticker 展示后端返回的所有 actionType 及其计数，不再有 "other" 类。

### Task 3: 修复 AuditReportView 筛选器默认值
- **ACTION**: 将 AuditReportView 的 entityType/action 筛选器默认值从 `''` 改为 `'all'`，`__all__` 选项改为 `'all'`，发送 API 时统一转换
- **IMPLEMENT**:
  ```typescript
  // Before:
  const entityTypeFilter = ref<string>('')
  const actionFilter = ref<string>('')
  // SelectItem: value="__all__"

  // After:
  const entityTypeFilter = ref<string>('all')
  const actionFilter = ref<string>('all')
  // SelectItem: value="all"
  ```
  同时更新 `loadStats()` 中的参数转换：
  ```typescript
  entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
  action: actionFilter.value === 'all' ? undefined : actionFilter.value,
  ```
  与 AuditLogsView 保持一致。`resetFilters()` 也要改为 `'all'`。
- **MIRROR**: FILTER_DEFAULT_PATTERN — AuditLogsView 的 `'all'` → `undefined` 模式
- **IMPORTS**: 无新增
- **GOTCHA**: `actionOptions` computed 在 `entityTypeFilter` 为 `'all'` 时应返回空数组（因为没有选择具体实体类型）。当前 `!entityTypeFilter.value` 检查空字符串，改为 `'all'` 后需检查 `=== 'all'`。
- **VALIDATE**: AuditReportView 筛选器默认选中 "All Entity Types"/"All Actions"，选择后能正确查询 stats。

### Task 4: 为 AuditLogsView 添加 normalizeDateParams
- **ACTION**: 在 AuditLogsView 的 `loadLogs()` 中调用 `normalizeDateParams`
- **IMPLEMENT**:
  ```typescript
  // Before:
  import type { AuditLog } from '@/api/admin/audit'

  // After:
  import { normalizeDateParams, type AuditLog } from '@/api/admin/audit'

  // loadLogs() 中:
  async function loadLogs() {
    const params = normalizeDateParams({
      search: searchQuery.value || undefined,
      action: actionFilter.value === 'all' ? undefined : actionFilter.value,
      entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
      startDate: startDateFilter.value || undefined,
      endDate: endDateFilter.value || undefined,
      performerId: performerIdFilter.value || undefined,
      userId: userIdFilter.value || undefined,
      page: tablePagination.value.pageIndex + 1,
      limit: tablePagination.value.pageSize,
    })
    await auditStore.fetchLogs(params)
    await auditStore.fetchStats(params)
  }
  ```
  注意：`auditApi.getAuditLogs()` 内部也调用了 `normalizeDateParams`，所以会双重归一化。但 `normalizeDateParams` 只在 `length === 10`（纯日期）时追加时间部分，已包含时间的字符串不受影响，所以双重调用是安全的。为保持一致性仍在此处调用。
- **MIRROR**: DATE_NORMALIZATION_PATTERN — AuditReportView 已正确使用
- **IMPORTS**: 从 `@/api/admin/audit` 增加 `normalizeDateParams`
- **GOTCHA**: `normalizeDateParams` 返回新对象（`{ ...params }`），不会修改原对象。
- **VALIDATE**: 在 AuditLogsView 设置 endDate 为今天日期，确认包含当天的审计记录。

### Task 5: 统一 AUDIT_ACTIONS_BY_ENTITY key 为大写
- **ACTION**: 将 `utils.ts` 中 `AUDIT_ACTIONS_BY_ENTITY` 的 key 从 camelCase 改为大写 SNAKE_CASE，与 `AUDIT_ENTITY_TYPES` 和后端值一致
- **IMPLEMENT**:
  ```typescript
  // Before:
  export const AUDIT_ACTIONS_BY_ENTITY: Record<string, string[]> = {
    user: [...],
    problem: [...],
    contest: [...],
    forumPost: [...],
    comment: [...],
    tag: [...],
    permission: [...],
    other: [...],
  }

  // After:
  export const AUDIT_ACTIONS_BY_ENTITY: Record<string, string[]> = {
    USER: [...],
    PROBLEM: [...],
    CONTEST: [...],
    FORUM_POST: [...],
    COMMENT: [...],
    TAG: [...],
    PERMISSION: [...],
    OTHER: [...],
  }
  ```
  同步更新 i18n `entityGroups` key 从 camelCase 改为大写：
  ```typescript
  // Before:
  entityGroups: { user: '用户操作', problem: '题目操作', ... }
  // After:
  entityGroups: { USER: '用户操作', PROBLEM: '题目操作', ... }
  ```
  同步更新 AuditLogsView 中筛选器的分组遍历：
  ```html
  <!-- Before: -->
  <SelectGroup v-for="group in AUDIT_ACTION_GROUPS" :key="group">
    <SelectLabel>{{ t(`audit.entityGroups.${group}`) }}</SelectLabel>
    <SelectItem v-for="action in AUDIT_ACTIONS_BY_ENTITY[group]" ...>

  <!-- After: 无需改动，因为 AUDIT_ACTION_GROUPS = Object.keys(AUDIT_ACTIONS_BY_ENTITY) -->
  ```
  AuditReportView 的 `entityTypeOptions` 和 `actionOptions` 也不需改动（它们基于 AUDIT_ENTITY_TYPES 和 AUDIT_ACTIONS_BY_ENTITY 动态生成）。

  补充缺失的分组：将 `CONTEST_ANNOUNCEMENT`、`SUBMISSION`、`FORUM_COMMENT`、`PROBLEM_LIST`、`SETTINGS`、`NOTIFICATION` 的操作从 `OTHER` 拆出，建立独立分组：
  ```typescript
  CONTEST_ANNOUNCEMENT: ['CREATE_CONTEST_ANNOUNCEMENT', 'UPDATE_CONTEST_ANNOUNCEMENT', 'DELETE_CONTEST_ANNOUNCEMENT'],
  SUBMISSION: ['REQUEUE_SUBMISSION', 'DELETE_SUBMISSION'],
  FORUM_COMMENT: [],  // 目前无专属操作，预留
  PROBLEM_LIST: ['UPDATE_PROBLEM_LIST', 'DELETE_PROBLEM_LIST'],
  SETTINGS: ['UPDATE_SETTINGS'],
  NOTIFICATION: ['CREATE_NOTIFICATION', 'UPDATE_NOTIFICATION', 'DELETE_NOTIFICATION'],
  ```
  `OTHER` 组只保留 `MODERATE_CONTENT`。
- **MIRROR**: AUDIT_ENTITY_TYPES 使用全大写 SNAKE_CASE，与后端 entityType 值完全一致
- **IMPORTS**: 无新增
- **GOTCHA**: `entityTypeToI18nKey()` 拼接 `audit.entityTypes.${type}`，当前 i18n 中 entityTypes key 已是大写（`USER`, `FORUM_POST` 等），所以 key 改大写后 entityTypes 翻译不受影响。需要更新的是 `entityGroups` 的 key。
- **VALIDATE**: 筛选器下拉框中分组标签正确显示中文/英文翻译，选择操作后能正确筛选。

### Task 6: 修复 oldValues/newValues 类型
- **ACTION**: 将 `audit.ts` 中 `AuditLog` 接口的 `oldValues/newValues` 从 `unknown` 改为 `Record<string, unknown> | null`
- **IMPLEMENT**:
  ```typescript
  // Before:
  oldValues?: unknown
  newValues?: unknown

  // After:
  oldValues?: Record<string, unknown> | null
  newValues?: Record<string, unknown> | null
  ```
  后端返回 `null`（非 undefined），所以类型应包含 `null`。`Record<string, unknown>` 保证顶层可遍历。
- **MIRROR**: 后端 `Map<String, Object>` → JSON `{key: value}`
- **IMPORTS**: 无新增
- **GOTCHA**: AuditLogDetailDrawer.vue 中 `formatJson()` 函数接受 `unknown`，改为 `Record<string, unknown> | null` 后仍兼容（`unknown` 是 top type 的子集，实际运行无影响，但类型更精确）。
- **VALIDATE**: `pnpm type-check` 通过，无类型错误。

---

## Testing Strategy

### Unit Tests
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| normalizeDateParams 无日期 | `{ search: 'test' }` | `{ search: 'test' }` | No |
| normalizeDateParams 仅日期 | `{ startDate: '2026-05-24', endDate: '2026-05-24' }` | `{ startDate: '2026-05-24T00:00:00', endDate: '2026-05-25T00:00:00' }` | Yes — 结束日推一天 |
| normalizeDateParams 已含时间 | `{ startDate: '2026-05-24T10:00:00' }` | `{ startDate: '2026-05-24T10:00:00' }` | Yes — 不重复处理 |
| column ID 等于 accessorKey | column `{ accessorKey: 'createdAt' }` 无显式 id | `column.id === 'createdAt'` | No |

### Edge Cases Checklist
- [x] 空字符串筛选值 → 转为 undefined 不发送
- [x] 日期格式 `2026-05-24` → 补时间
- [x] 日期格式 `2026-05-24T10:00:00` → 不重复补
- [x] stats 无数据 → actionsByType 为空数组
- [x] entityId 为 `"N/A"` → slice(0,8) 显示 "N/A"
- [x] oldValues 为 null → drawer 显示 "无数据变更"

---

## Validation Commands

### Static Analysis
```bash
cd management && pnpm type-check
```
EXPECT: Zero type errors

### Lint
```bash
cd management && pnpm lint
```
EXPECT: No lint errors

### Unit Tests
```bash
cd management && pnpm test
```
EXPECT: All tests pass

### Browser Validation
```bash
pm2 restart ulticode-9003
```
然后打开 `http://localhost:9003/audit`：
1. createdAt 列显示正确日期时间（非 "Invalid Date"）
2. 控制台无 `[Table] Column with id 'createdAt' does not exist` 错误
3. Stats ticker 展示全部 actionType 分类及计数
4. 设置 endDate 为今天，数据包含今天的记录
5. 切换到 Audit Report 页，筛选器默认选中 "All"，stats 正常加载

### Manual Validation
- [ ] AuditLogsView 时间列正确
- [ ] AuditLogsView 控制台无列 ID 错误
- [ ] Stats ticker 展示全部 actionType
- [ ] AuditReportView 筛选器默认值正确
- [ ] AuditReportView 清空筛选器后重置为 'all'
- [ ] 日期筛选 endDate 包含当天数据
- [ ] 筛选器下拉分组标签有正确 i18n 翻译

---

## Acceptance Criteria
- [x] 所有 tasks 完成
- [ ] pnpm type-check 零错误
- [ ] pnpm lint 零错误
- [ ] pnpm test 全部通过
- [ ] createdAt 列正确显示日期
- [ ] 控制台无 `[Table] Column with id 'createdAt' does not exist`
- [ ] Stats ticker 展示全部 actionType
- [ ] AuditReportView 筛选器与 AuditLogsView 行为一致

## Completion Checklist
- [x] 代码遵循 codebase 模式（其他视图的列定义模式）
- [x] 错误处理与 codebase 风格一致
- [x] i18n key 遵循现有命名规则
- [x] 无硬编码值
- [x] 无不必要范围扩展

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 移除列 id 后 DataTable 列名翻译断 | Low | Medium | table.ts 已有 camelCase key 兜底 |
| AUDIT_ACTIONS_BY_ENTITY key 改大写后筛选器联动断 | Low | High | AUDIT_ACTION_GROUPS 动态取 key，模板无需改动 |
| normalizeDateParams 双重调用 | None | None | 只在 length===10 时追加，幂等 |

## Notes
- 后端无需改动，所有修复均在前端侧
- 后端日期格式缺 timezone offset（如 `+08:00`）问题需另案在后端 Jackson 配置中处理，当前 Chrome 可正常解析
- 导出按钮和 countDailyActiveUsers 时间线图表为 LOW 优先级，不在本计划范围
