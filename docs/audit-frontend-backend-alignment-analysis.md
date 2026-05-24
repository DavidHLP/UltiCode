# Audit 模块前后端对齐深度分析报告

*生成日期: 2026-05-24 | 分析范围: management/audit 前端 + backend-spring audit 模块*

---

## 一、接口对齐总览

| 前端 API 方法 | 前端路径 | 后端 Controller 方法 | 后端路径 | 对齐状态 |
|---|---|---|---|---|
| `getAuditLogs` | `GET /admin/audit/logs` | `getAuditLogs` | `GET /admin/audit/logs` | 对齐 |
| `getAuditStats` | `GET /admin/audit/stats` | `getAuditStats` | `GET /admin/audit/stats` | 部分对齐 |
| `exportAuditLogs` | `GET /admin/audit/export` | `exportAuditLogs` | `GET /admin/audit/export` | 对齐 |

---

## 二、类型/字段对齐分析

### 2.1 AuditLog 实体对齐

| 字段 | 后端 `AuditLogVO` | 前端 `AuditLog` | 状态 |
|---|---|---|---|
| id | `String` | `string` | 对齐 |
| createdAt | `LocalDateTime` | `Date` | 对齐（JSON 自动转换） |
| performer | `PerformerInfo` (id/username/name/role) | `PerformerInfo?` (id/username/name/role) | 对齐 |
| user | `UserInfo` (id/username/name) | `UserInfo?` (id/username/name) | 对齐 |
| action | `String` | `string` | 对齐 |
| entityType | `String` | `string?` | 对齐 |
| entityId | `String` | `string?` | 对齐 |
| oldValues | `Map<String,Object>` | `unknown` | 对齐（JSON 动态结构） |
| newValues | `Map<String,Object>` | `unknown` | 对齐 |
| ipAddress | `String` | `string?` | 对齐 |
| userAgent | `String` | `string?` | 对齐 |

### 2.2 AuditStats 对齐

| 字段 | 后端 `AuditStatsVO` | 前端 `AuditStats` | 状态 |
|---|---|---|---|
| totalActions | `Long` | `number` | 对齐 |
| actionsByEntity | `List<EntityTypeStat>` (entityType/count) | `Array<{entityType, count}>` | 对齐 |
| topPerformers | `List<PerformerStat>` (performerId/username/name/role/count) | `Array<{performerId, username, name, role, count}>` | 对齐 |

### 2.3 查询参数对齐

| 参数 | 后端 `AuditLogQueryDTO` | 前端 `AuditLogQueryParams` | 状态 |
|---|---|---|---|
| search | `@Size(max=200) String` | `string?` | 对齐 |
| performerId | `String` | `string?` | 对齐 |
| userId | `String` | `string?` | 对齐 |
| entityType | `String` | `string?` | 对齐 |
| entityId | `String` | `string?` | 对齐 |
| action | `String` | `string?` | 对齐 |
| startDate | `LocalDateTime` | `string?` | 类型差异（见问题6） |
| endDate | `LocalDateTime` | `string?` | 类型差异（见问题6） |
| page | `Integer (default=1, @Min(1))` | `number?` | 对齐 |
| limit | `Integer (default=50, @Min(1) @Max(1000))` | `number?` | 对齐 |

---

## 三、发现的问题与不一致

### 问题 1：前端 Action 过滤器选项严重不全

**严重程度：HIGH**

后端 `AuditActionUtil` 定义了 30+ 个 action 常量，但前端 `AuditLogsView.vue` 的 action 过滤器 `Select` 只列出了 7 个：

| 前端已有的 action | 后端有但前端缺失的 action |
|---|---|
| CREATE_USER | RESET_PASSWORD |
| UPDATE_USER | CREATE_PROBLEM, UPDATE_PROBLEM, DELETE_PROBLEM |
| DELETE_USER | CREATE_CONTEST, UPDATE_CONTEST, DELETE_CONTEST |
| BAN_USER | CREATE_SOLUTION, UPDATE_SOLUTION, DELETE_SOLUTION |
| UNBAN_USER | FLAG_SOLUTION, UNFLAG_SOLUTION |
| GRANT_PERMISSION | CREATE_FORUM_POST, UPDATE_FORUM_POST, DELETE_FORUM_POST |
| REVOKE_PERMISSION | PIN_POST, UNPIN_POST, LOCK_POST, UNLOCK_POST, FLAG_POST, UNFLAG_POST |
| | CREATE_TAG, UPDATE_TAG, DELETE_TAG |
| | UPDATE_SETTINGS |
| | UPDATE_PROBLEM_LIST, DELETE_PROBLEM_LIST |
| | CREATE_NOTIFICATION, UPDATE_NOTIFICATION, DELETE_NOTIFICATION |
| | CREATE_CONTEST_ANNOUNCEMENT, UPDATE_CONTEST_ANNOUNCEMENT, DELETE_CONTEST_ANNOUNCEMENT |
| | REQUEUE_SUBMISSION, DELETE_SUBMISSION |
| | FLAG_COMMENT, UNFLAG_COMMENT, DELETE_COMMENT |
| | MODERATE_CONTENT |

**影响**：用户无法通过过滤器筛选大部分审计操作类型，只能看到 User 和 Permission 相关的操作。

### 问题 2：前端 EntityType 过滤器选项不全

**严重程度：HIGH**

后端 `AuditActionUtil` 定义了 14 个 entity type，前端只列出 5 个：

| 前端已有的 entityType | 后端有但前端缺失的 entityType |
|---|---|
| USER | CONTEST_ANNOUNCEMENT |
| PROBLEM | SUBMISSION |
| CONTEST | FORUM_COMMENT |
| SOLUTION | COMMENT |
| FORUM_POST | TAG, PROBLEM_LIST, SETTINGS, PERMISSION, NOTIFICATION |

**影响**：用户无法按这些实体类型过滤审计日志。

### 问题 3：i18n 缺失大量 action/entityType 翻译键

**严重程度：MEDIUM**

`audit.ts` i18n 文件只定义了 7 个 `actionTypes` 和 5 个 `entityTypes` 的翻译键，与后端 30+ actions 和 14 entity types 严重不匹配。

即使前端添加了更多过滤器选项，也需要同步添加 i18n 键。

### 问题 4：Stats Ticker 统计逻辑不准确

**严重程度：MEDIUM**

`AuditLogsView.vue` 的 stats computed 使用**当前页数据**计算 create/update/delete 数量：

```typescript
const stats = computed(() => {
  const logs = auditStore.logs ?? []
  return {
    total: auditStore.total,
    create: logs.filter((l) => l.action.includes('CREATE')).length,
    update: logs.filter((l) => l.action.includes('UPDATE')).length,
    delete: logs.filter((l) => l.action.includes('DELETE') || l.action.includes('BAN')).length,
  }
})
```

**问题**：
- 只统计**当前页**的日志，不是全局统计
- `delete` 统计包含了 `BAN` 操作（语义不准确：BAN 不是 DELETE）
- 后端 `/stats` 接口已有全局统计能力，但日志列表页没有使用它
- 应该调用 `getAuditStats` 获取全局统计，或至少让用户知道这只是当前页统计

### 问题 5：AuditReportView 绕过 Store 直接调用 API

**严重程度：LOW**

`AuditReportView.vue` 直接调用 `auditApi.getAuditStats()` 而不通过 `useAuditStore`。而 `AuditLogsView.vue` 使用 store。这导致：
- 两个页面的 loading/error 状态不共享
- 报告页的错误不会反映在 store 中
- 不一致的架构模式

### 问题 6：日期参数类型不一致

**严重程度：LOW**

后端 `AuditLogQueryDTO.startDate/endDate` 类型为 `LocalDateTime`，前端 `AuditLogQueryParams` 传 `string?`。Spring Boot 可以自动解析 ISO 格式字符串，但 `AuditReportView` 使用 `<Input type="date">` 只产生 `YYYY-MM-DD` 格式（无时间部分），可能导致：
- `startDate` 被解析为当天 00:00:00（正确）
- `endDate` 被解析为当天 00:00:00（**错误**，应该是 23:59:59 或次日 00:00:00）

### 问题 7：AuditReportView 导出功能参数不完整

**严重程度：LOW**

`AuditReportView.exportReport()` 只传 `startDate`、`endDate`、`performerId` 和 `format`，没有传 `search`、`userId`、`entityType`、`entityId`、`action` 等后端支持的过滤参数。虽然报告页没有这些过滤器的 UI，但导出参数类型 `AuditExportParams extends AuditLogQueryParams` 继承了所有字段，可能导致混淆。

### 问题 8：后端 stats SQL 不使用 query wrapper 的全部条件

**严重程度：MEDIUM**

`AuditServiceImpl.getAuditStats()` 中：
- `totalActions` 使用 `buildQueryWrapper(query)` — 支持所有过滤条件
- `actionsByEntity` 和 `topPerformers` 使用自定义 SQL（`selectStatsByEntityType`/`selectStatsByPerformer`），**只支持 startDate、endDate、performerId 三个参数**

这意味着如果前端传了 `entityType`、`action`、`userId` 等参数给 stats 接口，`totalActions` 会正确过滤，但 `actionsByEntity` 和 `topPerformers` 会忽略这些条件，导致数据不一致。

---

## 四、架构分析

### 4.1 数据流

```
@Audited 注解 --> AuditAspect (AOP) --> AuditService.log() --> audit_logs 表
                                                    |
                                        AuditController (查询/统计/导出)
                                                    |
                                        前端 auditApi --> Store/View
```

### 4.2 前端页面结构

| 页面 | 路由 | 组件 | 数据源 |
|---|---|---|---|
| 审计日志列表 | `/audit` | `AuditLogsView` | Store -> `getAuditLogs` |
| 审计报告 | `/audit/report` | `AuditReportView` | 直接 API -> `getAuditStats` + `exportAuditLogs` |

### 4.3 后端接口结构

| 接口 | 权限 | 参数粒度 | 返回粒度 |
|---|---|---|---|
| `GET /logs` | ADMIN/SUPER_ADMIN | 10 个过滤参数 + 分页 | `PageResult<AuditLogVO>` |
| `GET /stats` | ADMIN/SUPER_ADMIN | 同上（但 SQL 只用 3 个） | `AuditStatsVO` |
| `GET /export` | ADMIN/SUPER_ADMIN | 同上 + format | 文件流 |

---

## 五、改进建议

### 优先级 P0（功能缺陷）

1. **补全前端 action 过滤器选项**：将所有 30+ 个 `AuditActionUtil` 常量添加到 `AuditLogsView` 的 action Select 中，并同步添加 i18n 键
2. **补全前端 entityType 过滤器选项**：将所有 14 个 entity type 常量添加到 entityType Select 中

### 优先级 P1（数据准确性）

3. **修复 Stats Ticker 逻辑**：
   - 方案 A：调用 `getAuditStats` 获取全局统计
   - 方案 B：在 UI 上标注"当前页统计"
   - 修正 `delete` 统计不包含 `BAN`
4. **修复后端 stats SQL**：`selectStatsByEntityType` 和 `selectStatsByPerformer` 应支持全部查询条件（entityType、action、userId 等），而非只有 startDate/endDate/performerId
5. **修复 endDate 时区问题**：前端传 `YYYY-MM-DD` 格式的 endDate 时，后端应自动补为当天 23:59:59.999

### 优先级 P2（一致性）

6. **统一 Store 使用**：`AuditReportView` 应通过 `useAuditStore` 获取数据
7. **统一错误处理**：报告页的 `console.error` 应改为使用 store 的 error 状态和 toast 通知

---

## 六、文件清单

### 前端

| 文件 | 用途 |
|---|---|
| `management/src/api/admin/audit.ts` | API 定义 + 类型 |
| `management/src/stores/admin/audit.ts` | Pinia Store |
| `management/src/views/audit/AuditLogsView.vue` | 日志列表页 |
| `management/src/views/audit/AuditReportView.vue` | 报告页 |
| `management/src/views/audit/AuditLogDetailDrawer.vue` | 详情抽屉 |
| `management/src/views/audit/utils.ts` | 工具函数 |
| `management/src/i18n/locales/*/modules/audit.ts` | i18n |
| `management/src/i18n/locales/*/modules/audit-report.ts` | i18n |

### 后端

| 文件 | 用途 |
|---|---|
| `AuditController.java` | REST 接口 |
| `AuditService.java` / `AuditServiceImpl.java` | 业务逻辑 |
| `AuditLogMapper.java` | 数据访问 + 自定义 SQL |
| `AuditLog.java` | 实体 |
| `AuditLogVO.java` / `AuditLogQueryDTO.java` / `AuditStatsVO.java` | DTO/VO |
| `AuditActionUtil.java` | 常量定义 |
| `@Audited` + `AuditAspect.java` | AOP 审计记录 |
| `AuditContext.java` | ThreadLocal 上下文 |
| `AuditHelper.java` | 手动审计（已废弃） |
