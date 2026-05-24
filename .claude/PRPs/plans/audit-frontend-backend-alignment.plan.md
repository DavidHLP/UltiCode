# Plan: Audit 模块前后端对齐修复

## Summary
修复 Audit 模块前后端不一致问题：补全前端 action/entityType 过滤器选项与 i18n 键、修正 Stats 统计逻辑、修复后端 stats SQL 过滤条件不全、统一 Store 使用、修复 endDate 时区问题。

## User Story
作为管理员，我希望审计日志页面能按所有操作类型和实体类型进行过滤，并且统计数据准确反映全局而非仅当前页，以便完整追踪系统中的所有操作。

## Problem → Solution
**当前**：前端过滤器仅覆盖 7/30+ actions 和 5/14 entityTypes；stats 仅统计当前页；后端 stats SQL 忽略部分过滤条件；endDate 存在时区 bug。
**目标**：前后端完全对齐，所有 action/entityType 可过滤，stats 全局准确，架构一致。

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/audit-frontend-backend-alignment-analysis.md`
- **PRD Phase**: N/A
- **Estimated Files**: 14

---

## UX Design

### Before
```
┌─ Audit Logs ─────────────────────────────┐
│ Filters:                                  │
│   Action: [7 options ▼]  (missing 23+)   │
│   Entity: [5 options ▼]  (missing 9)     │
│                                           │
│ Stats: CREATE: 3  UPDATE: 2  DELETE: 1   │
│        ↑ only current page, BAN counted   │
│        as DELETE                           │
└───────────────────────────────────────────┘
```

### After
```
┌─ Audit Logs ─────────────────────────────┐
│ Filters:                                  │
│   Action: [30+ options, grouped ▼]       │
│   Entity: [14 options ▼]                 │
│                                           │
│ Stats: CREATE: 152  UPDATE: 89  DELETE: 34│
│        ↑ global stats from /stats API     │
│        BAN tracked separately             │
└───────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Action filter | 7 个选项 | 30+ 选项，按实体分组 | 使用 OptGroup 分组 |
| EntityType filter | 5 个选项 | 14 个选项 | 完整覆盖 |
| Stats ticker | 当前页统计 | 全局统计 | 调用 /stats 接口 |
| Report page | 直接调 API | 通过 Store | 架构统一 |
| endDate | 截止当天 00:00 | 截止当天 23:59:59 | 修复时区 bug |

---

## Mandatory Reading

| Priority | File | Why |
|---|---|---|
| P0 | `backend-spring/.../util/AuditActionUtil.java` | 所有 action/entityType 常量的权威来源 |
| P0 | `management/src/views/audit/AuditLogsView.vue` | 主要修改目标 |
| P0 | `management/src/i18n/locales/zh-CN/modules/audit.ts` | i18n 键定义 |
| P1 | `management/src/stores/admin/audit.ts` | Store 统一改造 |
| P1 | `backend-spring/.../mapper/AuditLogMapper.java` | stats SQL 修复 |
| P1 | `backend-spring/.../service/impl/AuditServiceImpl.java` | stats 逻辑修复 |
| P2 | `management/src/views/audit/AuditReportView.vue` | Store 统一 |
| P2 | `management/src/views/audit/utils.ts` | 工具函数 |

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: management/src/i18n/locales/zh-CN/modules/audit.ts
```typescript
actionTypes: {
  createUser: '创建用户',
  updateUser: '更新用户',
  // camelCase key = action constant → lowerCamelCase
}
entityTypes: {
  user: '用户',
  problem: '题目',
  // camelCase key = entityType → lowerCamelCase
}
```

### SELECT_OPTIONS_PATTERN
// SOURCE: management/src/views/audit/AuditLogsView.vue (现有 action Select)
```vue
<Select v-model="filters.action">
  <SelectTrigger><SelectValue placeholder="..." /></SelectTrigger>
  <SelectContent>
    <SelectItem value="CREATE_USER">{{ t('audit.actionTypes.createUser') }}</SelectItem>
    <!-- 每个 action 一个 SelectItem -->
  </SelectContent>
</Select>
```

### STORE_PATTERN
// SOURCE: management/src/stores/admin/audit.ts
```typescript
export const useAuditStore = defineStore('admin-audit', () => {
  const logs = ref<AuditLog[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)
  // ...
  return { logs, total, loading, error, ... }
})
```

### STATS_SQL_PATTERN
// SOURCE: backend-spring/.../mapper/AuditLogMapper.java
```xml
<select id="selectStatsByEntityType" resultType="...">
  SELECT entity_type AS entityType, COUNT(*) AS count
    FROM audit_logs
    WHERE deleted = 0
    <if test="startDate != null">AND created_at >= #{startDate}</if>
    <if test="endDate != null">AND created_at &lt;= #{endDate}</if>
    <if test="performerId != null">AND performer_id = #{performerId}</if>
  GROUP BY entity_type ORDER BY count DESC
</select>
```

### ERROR_HANDLING
// SOURCE: management/src/stores/admin/audit.ts
```typescript
catch (err: any) {
  error.value = err.response?.data?.message || err.message || 'Failed to fetch audit logs'
  toast.error(error.value)
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `management/src/views/audit/AuditLogsView.vue` | UPDATE | 补全 action/entityType 过滤器；stats 改用全局 API |
| `management/src/views/audit/utils.ts` | UPDATE | 新增 action/entityType 常量和分组函数 |
| `management/src/stores/admin/audit.ts` | UPDATE | 添加 stats state + fetchStats action；endDate 修复 |
| `management/src/views/audit/AuditReportView.vue` | UPDATE | 改用 Store 获取数据 |
| `management/src/i18n/locales/zh-CN/modules/audit.ts` | UPDATE | 补全 23+ actionTypes + 9 entityTypes 翻译 |
| `management/src/i18n/locales/en-US/modules/audit.ts` | UPDATE | 同上英文版 |
| `backend-spring/.../mapper/AuditLogMapper.java` | UPDATE | stats SQL 添加 entityType/action/userId 条件 |
| `backend-spring/.../service/impl/AuditServiceImpl.java` | UPDATE | stats 方法传全量查询参数 |
| `management/src/i18n/locales/zh-CN/modules/audit-report.ts` | UPDATE | 如需新增键 |
| `management/src/i18n/locales/en-US/modules/audit-report.ts` | UPDATE | 如需新增键 |

## NOT Building
- 新的后端接口（已有接口足够）
- 新的数据库迁移
- 新的审计记录逻辑（AOP 层不变）
- 导出功能参数补全（LOW 优先级，后续迭代）

---

## Step-by-Step Tasks

### Task 1: 在 utils.ts 中定义完整的 action/entityType 常量及分组
- **ACTION**: 在 `management/src/views/audit/utils.ts` 中添加完整的 action 和 entityType 常量，以及按实体分组的 action 分组函数
- **IMPLEMENT**:
  ```typescript
  // 完整的 entityType 列表（对应 AuditActionUtil.ENTITY_TYPE_*）
  export const AUDIT_ENTITY_TYPES = [
    'USER', 'PROBLEM', 'CONTEST', 'SOLUTION', 'FORUM_POST',
    'CONTEST_ANNOUNCEMENT', 'SUBMISSION', 'COMMENT', 'TAG',
    'PROBLEM_LIST', 'SETTINGS', 'PERMISSION', 'NOTIFICATION',
  ] as const

  // 完整的 action 列表（对应 AuditActionUtil.ACTION_*）
  export const AUDIT_ACTIONS = [
    // User
    'CREATE_USER', 'UPDATE_USER', 'DELETE_USER', 'RESET_PASSWORD',
    'BAN_USER', 'UNBAN_USER',
    // Problem
    'CREATE_PROBLEM', 'UPDATE_PROBLEM', 'DELETE_PROBLEM',
    // Contest
    'CREATE_CONTEST', 'UPDATE_CONTEST', 'DELETE_CONTEST',
    'CREATE_CONTEST_ANNOUNCEMENT', 'UPDATE_CONTEST_ANNOUNCEMENT', 'DELETE_CONTEST_ANNOUNCEMENT',
    // Solution
    'CREATE_SOLUTION', 'UPDATE_SOLUTION', 'DELETE_SOLUTION',
    'FLAG_SOLUTION', 'UNFLAG_SOLUTION',
    // Forum
    'CREATE_FORUM_POST', 'UPDATE_FORUM_POST', 'DELETE_FORUM_POST',
    'PIN_POST', 'UNPIN_POST', 'LOCK_POST', 'UNLOCK_POST',
    'FLAG_POST', 'UNFLAG_POST',
    // Comment
    'FLAG_COMMENT', 'UNFLAG_COMMENT', 'DELETE_COMMENT',
    // Tag
    'CREATE_TAG', 'UPDATE_TAG', 'DELETE_TAG',
    // Other
    'GRANT_PERMISSION', 'REVOKE_PERMISSION',
    'UPDATE_SETTINGS', 'UPDATE_PROBLEM_LIST', 'DELETE_PROBLEM_LIST',
    'CREATE_NOTIFICATION', 'UPDATE_NOTIFICATION', 'DELETE_NOTIFICATION',
    'REQUEUE_SUBMISSION', 'DELETE_SUBMISSION',
    'MODERATE_CONTENT',
  ] as const

  // 按 entityType 分组的 action 映射（用于 Select OptGroup）
  export const AUDIT_ACTIONS_BY_ENTITY: Record<string, string[]> = {
    user: ['CREATE_USER', 'UPDATE_USER', 'DELETE_USER', 'RESET_PASSWORD', 'BAN_USER', 'UNBAN_USER'],
    problem: ['CREATE_PROBLEM', 'UPDATE_PROBLEM', 'DELETE_PROBLEM'],
    contest: ['CREATE_CONTEST', 'UPDATE_CONTEST', 'DELETE_CONTEST', 'CREATE_CONTEST_ANNOUNCEMENT', 'UPDATE_CONTEST_ANNOUNCEMENT', 'DELETE_CONTEST_ANNOUNCEMENT'],
    solution: ['CREATE_SOLUTION', 'UPDATE_SOLUTION', 'DELETE_SOLUTION', 'FLAG_SOLUTION', 'UNFLAG_SOLUTION'],
    forumPost: ['CREATE_FORUM_POST', 'UPDATE_FORUM_POST', 'DELETE_FORUM_POST', 'PIN_POST', 'UNPIN_POST', 'LOCK_POST', 'UNLOCK_POST', 'FLAG_POST', 'UNFLAG_POST'],
    comment: ['FLAG_COMMENT', 'UNFLAG_COMMENT', 'DELETE_COMMENT'],
    tag: ['CREATE_TAG', 'UPDATE_TAG', 'DELETE_TAG'],
    permission: ['GRANT_PERMISSION', 'REVOKE_PERMISSION'],
    other: ['UPDATE_SETTINGS', 'UPDATE_PROBLEM_LIST', 'DELETE_PROBLEM_LIST', 'CREATE_NOTIFICATION', 'UPDATE_NOTIFICATION', 'DELETE_NOTIFICATION', 'REQUEUE_SUBMISSION', 'DELETE_SUBMISSION', 'MODERATE_CONTENT'],
  }

  // entityType 的 i18n key 映射（用于 label）
  export const entityTypeToI18nKey = (type: string): string => {
    const map: Record<string, string> = {
      USER: 'user', PROBLEM: 'problem', CONTEST: 'contest', SOLUTION: 'solution',
      FORUM_POST: 'forumPost', CONTEST_ANNOUNCEMENT: 'contestAnnouncement',
      SUBMISSION: 'submission', COMMENT: 'comment', TAG: 'tag',
      PROBLEM_LIST: 'problemList', SETTINGS: 'settings', PERMISSION: 'permission',
      NOTIFICATION: 'notification',
    }
    return map[type] ?? type.toLowerCase()
  }

  // action 的 i18n key 映射
  export const actionToI18nKey = (action: string): string => {
    // CREATE_USER → createUser
    return action.charAt(0).toLowerCase() + action.slice(1).replace(/_([A-Z])/g, (_, c) => c.toUpperCase())
  }
  ```
- **MIRROR**: 常量命名参考 `AuditActionUtil.java` 中的定义，确保一一对应
- **GOTCHA**: 后端 `COMMENT` 和 `FORUM_COMMENT` 可能是同一个 entityType，需确认。查看 AuditActionUtil 中 `FLAG_COMMENT` 对应的 entityType
- **VALIDATE**: `AUDIT_ACTIONS` 的长度应 >= 30，`AUDIT_ENTITY_TYPES` 的长度应 >= 13

### Task 2: 补全 i18n 翻译键（zh-CN + en-US）
- **ACTION**: 在 `management/src/i18n/locales/zh-CN/modules/audit.ts` 和 `en-US/modules/audit.ts` 中补全所有缺失的 actionTypes 和 entityTypes 翻译
- **IMPLEMENT**: 为每个缺失的 action 和 entityType 添加翻译键
  ```typescript
  // zh-CN 新增的 actionTypes
  resetPassword: '重置密码',
  createProblem: '创建题目',
  updateProblem: '更新题目',
  deleteProblem: '删除题目',
  createContest: '创建竞赛',
  updateContest: '更新竞赛',
  deleteContest: '删除竞赛',
  createContestAnnouncement: '创建竞赛公告',
  updateContestAnnouncement: '更新竞赛公告',
  deleteContestAnnouncement: '删除竞赛公告',
  createSolution: '创建题解',
  updateSolution: '更新题解',
  deleteSolution: '删除题解',
  flagSolution: '标记题解',
  unflagSolution: '取消标记题解',
  createForumPost: '创建帖子',
  updateForumPost: '更新帖子',
  deleteForumPost: '删除帖子',
  pinPost: '置顶帖子',
  unpinPost: '取消置顶',
  lockPost: '锁定帖子',
  unlockPost: '解锁帖子',
  flagPost: '标记帖子',
  unflagPost: '取消标记帖子',
  flagComment: '标记评论',
  unflagComment: '取消标记评论',
  deleteComment: '删除评论',
  createTag: '创建标签',
  updateTag: '更新标签',
  deleteTag: '删除标签',
  updateSettings: '更新设置',
  updateProblemList: '更新题单',
  deleteProblemList: '删除题单',
  createNotification: '创建通知',
  updateNotification: '更新通知',
  deleteNotification: '删除通知',
  requeueSubmission: '重新排队提交',
  deleteSubmission: '删除提交',
  moderateContent: '审核内容',

  // zh-CN 新增的 entityTypes
  contestAnnouncement: '竞赛公告',
  submission: '提交',
  comment: '评论',
  tag: '标签',
  problemList: '题单',
  settings: '设置',
  permission: '权限',
  notification: '通知',
  ```
  ```typescript
  // en-US 新增的 actionTypes
  resetPassword: 'Reset Password',
  createProblem: 'Create Problem',
  updateProblem: 'Update Problem',
  deleteProblem: 'Delete Problem',
  createContest: 'Create Contest',
  updateContest: 'Update Contest',
  deleteContest: 'Delete Contest',
  createContestAnnouncement: 'Create Contest Announcement',
  updateContestAnnouncement: 'Update Contest Announcement',
  deleteContestAnnouncement: 'Delete Contest Announcement',
  createSolution: 'Create Solution',
  updateSolution: 'Update Solution',
  deleteSolution: 'Delete Solution',
  flagSolution: 'Flag Solution',
  unflagSolution: 'Unflag Solution',
  createForumPost: 'Create Forum Post',
  updateForumPost: 'Update Forum Post',
  deleteForumPost: 'Delete Forum Post',
  pinPost: 'Pin Post',
  unpinPost: 'Unpin Post',
  lockPost: 'Lock Post',
  unlockPost: 'Unlock Post',
  flagPost: 'Flag Post',
  unflagPost: 'Unflag Post',
  flagComment: 'Flag Comment',
  unflagComment: 'Unflag Comment',
  deleteComment: 'Delete Comment',
  createTag: 'Create Tag',
  updateTag: 'Update Tag',
  deleteTag: 'Delete Tag',
  updateSettings: 'Update Settings',
  updateProblemList: 'Update Problem List',
  deleteProblemList: 'Delete Problem List',
  createNotification: 'Create Notification',
  updateNotification: 'Update Notification',
  deleteNotification: 'Delete Notification',
  requeueSubmission: 'Requeue Submission',
  deleteSubmission: 'Delete Submission',
  moderateContent: 'Moderate Content',

  // en-US 新增的 entityTypes
  contestAnnouncement: 'Contest Announcement',
  submission: 'Submission',
  comment: 'Comment',
  tag: 'Tag',
  problemList: 'Problem List',
  settings: 'Settings',
  permission: 'Permission',
  notification: 'Notification',
  ```
- **MIRROR**: 现有 i18n 键的命名方式：`actionTypes.createUser`, `entityTypes.user`
- **GOTCHA**: 确保 `forumPost` 不写成 `forum_post`，i18n key 使用 camelCase
- **VALIDATE**: `Object.keys(audit.actionTypes).length >= 30`, `Object.keys(audit.entityTypes).length >= 13`

### Task 3: 重构 AuditLogsView 过滤器 — 使用 utils 常量和分组
- **ACTION**: 将 `AuditLogsView.vue` 中硬编码的 action/entityType Select 替换为动态渲染，使用 Task 1 定义的常量
- **IMPLEMENT**:
  - action Select：使用 `AUDIT_ACTIONS_BY_ENTITY` 渲染 `SelectGroup` + `SelectLabel` + `SelectItem`
  - entityType Select：使用 `AUDIT_ENTITY_TYPES` 渲染 `SelectItem`
  - 删除硬编码的 `SelectItem` 列表
  ```vue
  <!-- Action filter: grouped by entity -->
  <Select v-model="filters.action">
    <SelectTrigger><SelectValue placeholder="..." /></SelectTrigger>
    <SelectContent>
      <SelectItem :value="undefined">{{ t('audit.filters.allActions') }}</SelectItem>
      <SelectGroup v-for="(actions, group) in AUDIT_ACTIONS_BY_ENTITY" :key="group">
        <SelectLabel>{{ t(`audit.entityGroups.${group}`) }}</SelectLabel>
        <SelectItem v-for="action in actions" :key="action" :value="action">
          {{ t(`audit.actionTypes.${actionToI18nKey(action)}`) }}
        </SelectItem>
      </SelectGroup>
    </SelectContent>
  </Select>

  <!-- EntityType filter: flat list -->
  <Select v-model="filters.entityType">
    <SelectTrigger><SelectValue placeholder="..." /></SelectTrigger>
    <SelectContent>
      <SelectItem :value="undefined">{{ t('audit.filters.allTypes') }}</SelectItem>
      <SelectItem v-for="type in AUDIT_ENTITY_TYPES" :key="type" :value="type">
        {{ t(`audit.entityTypes.${entityTypeToI18nKey(type)}`) }}
      </SelectItem>
    </SelectContent>
  </Select>
  ```
- **MIRROR**: 使用 shadcn-vue 的 `SelectGroup`/`SelectLabel` 组件（项目已有）
- **IMPORTS**: `import { AUDIT_ACTIONS_BY_ENTITY, AUDIT_ENTITY_TYPES, actionToI18nKey, entityTypeToI18nKey } from './utils'`
- **GOTCHA**: `SelectItem :value="undefined"` 用于"全部"选项，确认 shadcn-vue Select 支持此写法。可能需要用空字符串 `""` 代替
- **VALIDATE**: 打开页面，确认 action 下拉有 30+ 选项且按实体分组；entityType 下拉有 13+ 选项

### Task 4: 修复 Stats Ticker 逻辑 — 使用全局 stats API
- **ACTION**: 修改 `AuditLogsView.vue` 中的 `stats` computed，改用 store 中的全局统计数据
- **IMPLEMENT**:
  1. 在 `useAuditStore` 中添加 `stats` state 和 `fetchStats` action
  2. 在 `AuditLogsView` 的 `onMounted` 和 `fetchLogs` 后同时调用 `fetchStats`
  3. 将 template 中的 stats ticker 绑定到 `auditStore.stats`
  4. 新增 `BAN` 和 `OTHER` 类别，不再将 BAN 计入 DELETE
  ```typescript
  // store 中新增
  const stats = ref<AuditStats | null>(null)
  const fetchStats = async () => {
    try {
      stats.value = await auditApi.getAuditStats(filters)
    } catch { /* error handled by store */ }
  }
  ```
  ```typescript
  // AuditLogsView 中替换 stats computed
  const stats = computed(() => {
    const s = auditStore.stats
    if (!s) return { total: 0, create: 0, update: 0, delete: 0, other: 0 }
    const actions = auditStore.logs ?? []
    return {
      total: s.totalActions,
      create: actions.filter(l => l.action.includes('CREATE')).length,  // 仍用当前页做分类展示
      update: actions.filter(l => l.action.includes('UPDATE')).length,
      delete: actions.filter(l => l.action.includes('DELETE')).length,
      other: actions.filter(l => !l.action.includes('CREATE') && !l.action.includes('UPDATE') && !l.action.includes('DELETE')).length,
    }
  })
  ```
  **更好的方案**：直接使用 `stats.totalActions` 展示全局总数，分类统计保留当前页逻辑但标注"当前页"
- **MIRROR**: 参考 `AuditReportView.vue` 中调用 `getAuditStats` 的模式
- **GOTCHA**: stats 请求也需要传过滤参数，这样统计与列表一致
- **VALIDATE**: stats 数字应反映全局数据，而非仅当前页

### Task 5: 修复后端 stats SQL — 支持全量查询条件
- **ACTION**: 修改 `AuditLogMapper.java` 中的 `selectStatsByEntityType` 和 `selectStatsByPerformer` SQL，添加 entityType/action/userId 条件
- **IMPLEMENT**: 在两个 `<select>` 中添加 MyBatis 动态条件：
  ```xml
  <if test="entityType != null and entityType != ''">AND entity_type = #{entityType}</if>
  <if test="action != null and action != ''">AND action = #{action}</if>
  <if test="userId != null and userId != ''">AND user_id = #{userId}</if>
  ```
- **MIRROR**: 参考同一文件中 `selectAuditLogs` 的条件写法
- **GOTCHA**: Mapper 方法的 `@Param` 需要包含新增的参数。当前方法签名可能只接收 `startDate`, `endDate`, `performerId`，需要扩展为接收完整的 `AuditLogQueryDTO`
- **VALIDATE**: 调用 `GET /admin/audit/stats?action=CREATE_USER` 应只返回 CREATE_USER 相关统计

### Task 6: 修复 AuditServiceImpl — stats 方法传全量参数
- **ACTION**: 修改 `AuditServiceImpl.getAuditStats()` 调用 Mapper 时传递完整 query 对象而非仅 startDate/endDate/performerId
- **IMPLEMENT**:
  ```java
  // Before: mapper.selectStatsByEntityType(query.getStartDate(), query.getEndDate(), query.getPerformerId())
  // After:
  mapper.selectStatsByEntityType(query)
  mapper.selectStatsByPerformer(query)
  ```
  同时更新 Mapper 接口方法签名从多参数改为单 `AuditLogQueryDTO` 参数
- **MIRROR**: 参考同一 Service 中 `getAuditLogs` 的 `buildQueryWrapper(query)` 用法
- **GOTCHA**: 确保 Mapper XML 中的 `@Param` 注解更新与 Java 接口一致
- **VALIDATE**: 编译通过 + 传 action 参数时 stats 结果正确过滤

### Task 7: 修复 endDate 时区问题
- **ACTION**: 在 `management/src/stores/admin/audit.ts` 或 `api/admin/audit.ts` 中，当 endDate 只有日期部分时自动补为 `23:59:59`
- **IMPLEMENT**:
  ```typescript
  // 在 API 调用前处理日期参数
  const normalizeDateParams = (params: AuditLogQueryParams): AuditLogQueryParams => {
    const p = { ...params }
    if (p.startDate && p.startDate.length === 10) {
      p.startDate = `${p.startDate}T00:00:00`
    }
    if (p.endDate && p.endDate.length === 10) {
      p.endDate = `${p.endDate}T23:59:59`
    }
    return p
  }
  ```
  或在后端 `AuditLogQueryDTO` 中用 `@JsonDeserialize` 自定义反序列化
- **MIRROR**: 前端方案更简单，优先在 API 层处理
- **GOTCHA**: 后端 `LocalDateTime` 默认解析 `YYYY-MM-DD` 为当天 00:00:00
- **VALIDATE**: 选择 endDate 为今天，验证包含今天的日志

### Task 8: 统一 AuditReportView 使用 Store
- **ACTION**: 将 `AuditReportView.vue` 中直接调用 `auditApi` 改为通过 `useAuditStore`
- **IMPLEMENT**:
  1. 在 store 中添加 `stats` state 和 `fetchStats` / `exportLogs` actions（Task 4 已部分完成）
  2. `AuditReportView` 改用 `const auditStore = useAuditStore()`
  3. 替换 `auditApi.getAuditStats()` → `auditStore.fetchStats()`
  4. 替换 `auditApi.exportAuditLogs()` → `auditStore.exportLogs()`
  5. 移除本地 `loading`/`error` ref，改用 store 状态
- **MIRROR**: 参考 `AuditLogsView.vue` 使用 store 的模式
- **GOTCHA**: `exportLogs` 返回 Blob，store action 需要正确处理文件下载
- **VALIDATE**: 报告页功能不变，但 error/loading 状态通过 store 管理

### Task 9: 补全 i18n entityGroups 键
- **ACTION**: 为 Task 3 中使用的 `audit.entityGroups.*` 添加 i18n 翻译
- **IMPLEMENT**:
  ```typescript
  // zh-CN
  entityGroups: {
    user: '用户操作',
    problem: '题目操作',
    contest: '竞赛操作',
    solution: '题解操作',
    forumPost: '论坛操作',
    comment: '评论操作',
    tag: '标签操作',
    permission: '权限操作',
    other: '其他操作',
  }
  // en-US
  entityGroups: {
    user: 'User Actions',
    problem: 'Problem Actions',
    contest: 'Contest Actions',
    solution: 'Solution Actions',
    forumPost: 'Forum Actions',
    comment: 'Comment Actions',
    tag: 'Tag Actions',
    permission: 'Permission Actions',
    other: 'Other Actions',
  }
  ```
- **MIRROR**: 遵循现有 i18n 结构
- **VALIDATE**: 页面 action 下拉分组标签显示正确

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `actionToI18nKey('CREATE_USER')` | CREATE_USER | createUser | 否 |
| `actionToI18nKey('RESET_PASSWORD')` | RESET_PASSWORD | resetPassword | 否 |
| `entityTypeToI18nKey('FORUM_POST')` | FORUM_POST | forumPost | 否 |
| `normalizeDateParams({endDate: '2026-05-24'})` | endDate 无时间 | '2026-05-24T23:59:59' | 是 |
| `normalizeDateParams({endDate: '2026-05-24T12:00:00'})` | endDate 有时间 | 不修改 | 是 |
| Stats SQL with action filter | action=CREATE_USER | 仅返回 CREATE_USER 统计 | 否 |

### Edge Cases Checklist
- [ ] action 过滤器选择"全部"时传 undefined/空
- [ ] entityType 值为 `COMMENT` vs `FORUM_COMMENT` 的一致性
- [ ] endDate 跨时区场景
- [ ] stats API 无数据时返回空数组而非 null
- [ ] SelectGroup 在无选项时不渲染

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

### Backend Compile
```bash
cd backend-spring && ./mvnw compile -q
```
EXPECT: BUILD SUCCESS

### Browser Validation
```bash
# 启动服务后验证
pm2 restart ulticode-9001 ulticode-9003
```
EXPECT:
1. `/audit` 页面 action 下拉有 30+ 选项按分组显示
2. entityType 下拉有 13+ 选项
3. Stats 数字反映全局数据
4. 报告页功能正常

---

## Acceptance Criteria
- [ ] 前端 action 过滤器覆盖 AuditActionUtil 中所有 30+ action
- [ ] 前端 entityType 过滤器覆盖所有 13+ entity type
- [ ] 所有新增 action/entityType 有 zh-CN 和 en-US 翻译
- [ ] Stats 使用全局 /stats API 数据
- [ ] 后端 stats SQL 支持全量查询条件
- [ ] endDate 时区问题已修复
- [ ] AuditReportView 通过 Store 获取数据
- [ ] TypeScript 无类型错误
- [ ] ESLint 无错误

## Completion Checklist
- [ ] 前后端 action/entityType 完全对齐
- [ ] 错误处理使用 store + toast
- [ ] 无硬编码过滤选项
- [ ] i18n 键完整覆盖
- [ ] 后端 SQL 条件完整

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| COMMENT vs FORUM_COMMENT entityType 不一致 | Medium | High | 需确认 AuditActionUtil 中 FLAG_COMMENT 的 entityType 映射 |
| SelectGroup 组件样式问题 | Low | Low | shadcn-vue 原生支持 SelectGroup |
| Stats API 性能（大数据量） | Low | Medium | stats 已有索引，且查询有分页 |

## Notes
- 此计划基于 `docs/audit-frontend-backend-alignment-analysis.md` 中的 8 个问题
- Task 1-3 解决问题 1-3（P0），Task 4-7 解决问题 4-8（P1-P2）
- 优先实施 Task 1-3（前端过滤器补全），这是用户最直接的功能缺陷
