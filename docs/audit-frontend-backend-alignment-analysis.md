# Audit 模块前后端颗粒度与逻辑对齐分析报告

> 生成日期: 2026-05-26 | 分析范围: `/audit` 页面 + 后端 `/admin/audit/*` API | 数据来源: 代码审查 + 实际 API 响应 + 浏览器运行验证

---

## 1. 严重问题（CRITICAL）

### 1.1 createdAt 列全部显示 "Invalid Date"

**现象**: 页面所有行的 `createdAt` 列显示 **"Invalid Date"**，同时控制台持续报错 `[Table] Column with id 'createdAt' does not exist`。

**根因分析** — 两个独立问题叠加:

**问题 A**: TanStack Table column ID 与 accessorKey 不匹配

```javascript
// AuditLogsView.vue:141-145
{
  accessorKey: 'createdAt',   // accessorKey = 'createdAt' (camelCase)
  id: 'created_at',           // id = 'created_at' (snake_case)
}
```

TanStack Table 使用 `id` 作为列标识，而 `accessorKey` 只在未指定 `id` 时才会被用作 `id`。当同时指定两者时，`id='created_at'` 成为列的真实 ID，但数据对象中的字段名是 `createdAt`（后端 JSON 返回 camelCase）。Table 内部通过 `id` 查找数据属性时找不到 `created_at`，因此触发 `[Table] Column with id 'createdAt' does not exist` 错误（因为实际上列 ID 是 `created_at` 而不是 `createdAt`）。

然而 `cell` 渲染器直接调用 `row.getValue('createdAt')` 而不是 `row.getValue('created_at')`，这导致 getValue 返回 undefined → `new Date(undefined)` → **"Invalid Date"**。

**问题 B**: 后端 JSON 日期格式尾部 `.921` 无时区信息

后端返回 `"2026-05-24T09:38:49.921"` — 没有 timezone offset（如 `+08:00` 或 `Z`）。某些浏览器引擎在 Safari/旧 Chrome 下对这种格式会返回 Invalid Date。当前 Chrome 能解析，但跨浏览器兼容性差。

**影响**: 表格核心时间列完全不可读。

**修复建议**:

| 列 | accessorKey | id | 修复方案 |
|---|---|---|---|
| createdAt | `createdAt` | `created_at` | 统一 id 为 `createdAt` 或去掉 id 让 accessorKey 自动充当 |
| action | `action` | `action` | OK |
| entityType | `entityType` | `entity_type` | 同样存在不一致 |
| performer | `performer` | `performer` | OK |
| user | `user` | `user` | OK |
| ipAddress | `ipAddress` | `ip_address` | 同样不一致 |

**推荐做法**: 所有列要么全部去掉显式 `id`（让 `accessorKey` 自动充当 `id`），要么确保 `id` 和 `accessorKey` 一致。对于 DataTable 的 i18n 列名系统 `resolveColumnName(columnId)`，它通过 `t('table.columnNames.${columnId}')` 来翻译列名，所以 `columnId` 需要和 i18n key 匹配。

当前 `table.ts` i18n 中同时定义了 snake_case 和 camelCase key（如 `created_at` 和 `createdAt`），所以无论哪种都能翻译。但 TanStack Table 数据访问需要 `accessorKey` 与数据对象字段名一致。

### 1.2 Stats ticker 中 CREATE/DELETE 数值显示为 0

**现象**: Stats ticker 显示 `Create: 0, Update: 1, Delete: 0`，但后端 stats API 返回的 `actionsByType` 是 `BAN, UPDATE, UNBAN, PIN, UNPIN, UNLOCK, RESET, LOCK` — 没有 `CREATE`/`DELETE` 类型。

**根因**:

```javascript
// AuditLogsView.vue:77-90
const byType = Object.fromEntries(s.actionsByType?.map((i) => [i.actionType, i.count]) ?? [])
return {
  create: byType.CREATE ?? 0,  // 0 - 因为后端无 CREATE actionType
  update: byType.UPDATE ?? 0,  // 1
  delete: byType.DELETE ?? 0,  // 0 - 因为后端无 DELETE actionType
  other: s.totalActions - ...  // 8
}
```

后端 `selectStatsByActionType` SQL 的 CASE 分组将 `CREATE_*` 归为 `CREATE`，`DELETE_*` 归为 `DELETE`。但当前测试数据中没有 CREATE/DELETE 类型的审计记录，所以 actionsByType 中没有这些键。前端用 `?? 0` 兜底，导致显示 0。

这本身不是 bug，但 `other` 计算 `totalActions - CREATE - UPDATE - DELETE = 9 - 0 - 1 - 0 = 8`，意味着 8 条记录被归类为 "other"，但实际上它们是 BAN, UNBAN, PIN, UNPIN 等有明确类型归属的操作。**前端只展示 CREATE/UPDATE/DELETE 三类**，其余全部归为 "other"，严重低估了操作类型的丰富度。

**修复建议**: Stats ticker 应动态展示后端返回的所有 actionTypes，而不是硬编码只展示 3 类。

---

## 2. 前后端颗粒度不对齐（HIGH）

### 2.1 EntityType 命名不一致

**前端 `AUDIT_ENTITY_TYPES` 常量** (utils.ts:20-35):
```
'USER', 'PROBLEM', 'CONTEST', 'CONTEST_ANNOUNCEMENT', 'SOLUTION',
'SUBMISSION', 'FORUM_POST', 'FORUM_COMMENT', 'COMMENT', 'TAG',
'PROBLEM_LIST', 'SETTINGS', 'PERMISSION', 'NOTIFICATION'
```

**前端 `AUDIT_ACTIONS_BY_ENTITY` 映射** (utils.ts:37-80):
```
key: 'user', 'problem', 'contest', 'forumPost', 'comment', 'tag', 'permission', 'other'
```

**后端 EntityType 值** (数据库/API 实际存储): `USER`, `FORUM_POST` 等（全大写 SNAKE_CASE）

**不对齐点**:
1. `AUDIT_ENTITY_TYPES` 是全大写，`AUDIT_ACTIONS_BY_ENTITY` 的 key 是 camelCase（`forumPost` vs `FORUM_POST`）— 前端内部两套命名风格不一致
2. `AUDIT_ENTITY_TYPES` 包含 14 种实体类型，但 `AUDIT_ACTIONS_BY_ENTITY` 只覆盖 8 个分组，缺少 `CONTEST_ANNOUNCEMENT`, `SUBMISSION`, `FORUM_COMMENT`, `PROBLEM_LIST`, `SETTINGS`, `NOTIFICATION` 的专属操作映射 — 这些类型的操作被归入 `other` 组
3. 后端 SQL `selectStatsByEntityType` 返回的是原始大写值（如 `FORUM_POST`），前端 `entityTypeToI18nKey()` 直接拼接 `audit.entityTypes.${type}`，但 `AUDIT_ENTITY_TYPES` 定义和 API 返回的值一致（大写），而 `AUDIT_ACTIONS_BY_ENTITY` 的 key 用小写 camelCase — **筛选器与 i18n 的映射不一致**

### 2.2 AuditReportView 筛选器默认值不一致

**AuditLogsView** (列表页): entityType/action 筛选器默认值 `'all'`，发送 API 时转换为 `undefined`

**AuditReportView** (报表页): entityType/action 筛选器默认值 `''`（空字符串），还有 `__all__` 选项

两者使用不同的"全部"值约定：`'all'` vs `''` vs `'__all__'`。空字符串发送到后端会被当作 `action=""` 传入 query DTO，但 `buildQueryWrapper` 只检查 `query.getAction() != null` — 空字符串非 null，会生成 `AND action = ''` 条件，导致查询结果为空！

**这是 bug**: AuditReportView 中选择 `__all__` 时发送 `action=__all__` 或空字符串到后端，会导致查询出错。

### 2.3 `normalizeDateParams` 只在部分页面生效

- **AuditLogsView**: `loadLogs()` 没有调用 `normalizeDateParams()`！日期参数直接发送
- **AuditReportView**: `loadStats()` 正确调用了 `normalizeDateParams()`
- **auditApi**: API 函数内部也调用了 `normalizeDateParams()`

AuditLogsView 的日期筛选器发送的 `startDate` 和 `endDate` 如果是 `2026-05-24` 格式（没有时间部分），后端 `AuditLogQueryDTO.startDate` 类型是 `LocalDateTime`，Spring 会尝试将 `"2026-05-24"` 解析为 LocalDateTime — **这可能失败或解析为当天 00:00:00**，且 endDate 没有做 "next day" 处理，导致结束日期当天 00:00 之后的数据被排除。

---

## 3. 逻辑不对齐（MEDIUM）

### 3.1 Stats 与 Logs 使用相同查询参数但语义不同

`loadLogs()` 和 `fetchStats()` 使用完全相同的 params 对象：

```javascript
await auditStore.fetchLogs(params)
await auditStore.fetchStats(params)
```

但 stats API 不需要 `page`/`limit` 参数（它是聚合查询），而 logs API 需要。当前两者共用同一个 params 对象，stats 请求会带上不必要的分页参数。后端 `getAuditStats()` 方法虽然接收 `AuditLogQueryDTO`（包含 page/limit），但统计查询内部只用 startDate/endDate/performerId/userId/entityType/action/search，不使用分页字段 — 所以功能上没问题，但语义上不干净。

### 3.2 entityId 显示截断逻辑

前端 `entityId?.slice(0, 8)` 截取前 8 字符显示。后端 `entityId` 为 `"N/A"` 时（在 `log()` 方法中设置），显示为 `N/A`（只有 3 字符，无需截断）。但截断逻辑对 UUID 类型的 entityId 有用（如 `c612f403...`），对短 entityId 如 `"post-segtree-visual"` 只显示 `"post-seg"`，丢失关键信息。

### 3.3 oldValues/newValues 类型精度丢失

**前端类型**: `oldValues?: unknown`, `newValues?: unknown`

**后端类型**: `Map<String, Object>` → JSON 序列化后为 `{key: value}`

前端使用 `unknown` 类型放弃了所有类型安全。虽然实际数据结构不可预测（每个 entityType 的变更字段不同），但至少应定义为 `Record<string, unknown>` 以保证顶层结构可遍历。

### 3.4 AuditStatsVO.totalActions 是 Long，前端 AuditStats.totalActions 是 number

后端 `Long` 类型 → JSON 序列化为数字 → 前端 `number`。对于小于 2^53 的值没有问题，但这是一个隐式类型缩窄。

---

## 4. 遗留/缺失功能（LOW）

### 4.1 导出功能未暴露到 UI

`auditApi.exportAuditLogs()` 和 store 中 `exportLogs()` 方法已实现，但 AuditLogsView 页面没有导出按钮。

### 4.2 AuditReportView 的 entityType 筛选器使用 `__all__` 值

与 AuditLogsView 的 `'all'` 值不一致，且可能导致后端查询错误（见 2.2）。

### 4.3 countDailyActiveUsers mapper 方法未被使用

`AuditLogMapper.countDailyActiveUsers()` 已定义但未被任何 service/controller 调用。前端也没有活跃用户时间线图表。

---

## 5. 对齐修复优先级总结

| 优先级 | 问题 | 修复方案 |
|--------|------|----------|
| **CRITICAL** | createdAt 列 "Invalid Date" | 统一 column id 与 accessorKey（去掉 id 或设为相同值），cell 中用 `row.getValue('createdAt')` 对齐数据字段名 |
| **CRITICAL** | `[Table] Column 'createdAt' does not exist` | 同上 — 确保所有列 id/accessorKey 一致 |
| **HIGH** | AuditReportView 筛选器默认值不一致/空字符串 bug | 统一使用 `'all'` → `undefined` 转换，删除 `__all__` |
| **HIGH** | AuditLogsView 缺少 normalizeDateParams | 在 loadLogs() 中添加 normalizeDateParams 调用 |
| **HIGH** | Stats ticker 只硬编码 3 类 | 动态展示所有 actionTypes 或至少展示后端返回的全部类型 |
| **MEDIUM** | AUDIT_ACTIONS_BY_ENTITY key 小写 vs EntityType 大写 | 统一 key 为大写或建立显式映射 |
| **MEDIUM** | oldValues/newValues 类型 `unknown` | 改为 `Record<string, unknown>` |
| **LOW** | 导出按钮缺失 | 添加导出按钮到 toolbar |
| **LOW** | countDailyActiveUsers 未使用 | 移除或实现前端时间线图表 |

---

## 6. 后端 API 实际响应数据参考

### 6.1 `/admin/audit/logs?page=1&limit=3` 响应

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "action": "UNLOCK_POST",
        "createdAt": "2026-05-24T09:38:49.921",
        "entityId": "post-segtree-visual",
        "entityType": "FORUM_POST",
        "id": "8421ef852a65bac461b63ad87e18fc32",
        "ipAddress": "127.0.0.1",
        "newValues": null,
        "oldValues": null,
        "performer": {
          "id": "u-admin-001",
          "username": "admin",
          "name": "系统管理员",
          "role": "SUPER_ADMIN"
        },
        "user": {
          "id": "user-tourist",
          "username": "tourist",
          "name": "Gennady"
        },
        "userAgent": "Mozilla/5.0 ..."
      }
    ],
    "page": 1,
    "pageSize": 3,
    "total": 9,
    "totalPages": 3
  },
  "message": "success"
}
```

**关键发现**: 后端 JSON 字段全部使用 **camelCase**（`createdAt`, `entityId`, `entityType`, `ipAddress`），与前端 TypeScript interface 完全一致。但前端 TanStack Table 列定义使用了 snake_case `id`，造成数据访问断裂。

### 6.2 `/admin/audit/stats?page=1&limit=50` 响应

```json
{
  "code": 0,
  "data": {
    "actionsByEntity": [
      { "entityType": "USER", "count": 5 },
      { "entityType": "FORUM_POST", "count": 4 }
    ],
    "actionsByType": [
      { "actionType": "BAN", "count": 2 },
      { "actionType": "UPDATE", "count": 1 },
      { "actionType": "UNBAN", "count": 1 },
      { "actionType": "PIN", "count": 1 },
      { "actionType": "UNPIN", "count": 1 },
      { "actionType": "UNLOCK", "count": 1 },
      { "actionType": "RESET", "count": 1 },
      { "actionType": "LOCK", "count": 1 }
    ],
    "topPerformers": [
      {
        "performerId": "u-admin-001",
        "username": "admin",
        "name": "系统管理员",
        "role": "SUPER_ADMIN",
        "count": 9
      }
    ],
    "totalActions": 9
  }
}
```

**关键发现**: `actionType` 值是后端 CASE 语句归类的粗粒度类型（如 `BAN`, `UPDATE`），而非细粒度 action（如 `BAN_USER`）。前端 stats ticker 硬编码 `CREATE/UPDATE/DELETE` 三类，忽略了后端返回的全部其他类型。