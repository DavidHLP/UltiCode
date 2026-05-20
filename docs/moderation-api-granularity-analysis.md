# Moderation 模块前后端颗粒度分析报告

*生成日期: 2026-05-20 | 分析范围: 后端 API + Management 前端 + Console 前端*

---

## 一、模块总览

### 后端架构

| 层级 | 文件数 | 说明 |
|------|--------|------|
| Controller | 1 | `ModerationController` — 20 个端点 |
| Service | 2 | 接口 + 实现 |
| Mapper | 6 | ModerationQueue, ModerationAction, Report, Appeal, UserWarning, UserBan |
| Entity | 6 | 对应 6 张表 |
| DTO | 15 | 5 个查询 DTO + 5 个请求 DTO + 5 个 VO |
| Enum | 5 | ModerationStatus, ModerationActionType, ReportCategory, ReportStatus, AppealStatus |

### 前端架构 (Management)

| 层级 | 文件数 | 说明 |
|------|--------|------|
| 页面视图 | 4 | Queue, Dashboard, Reports, Appeals |
| 组件 | 5 | BatchActionDialog, DetailDrawer, ActionPanel, EntityPreviewCard, ActionHistoryTimeline |
| API | 1 | 3 组 API 函数 (queue/reports/appeals) |
| Store | 1 | Pinia store (500 行) |
| 列定义 | 3 | 队列/举报/申诉表格列 |
| Composable | 1 | useModerationFilters |

### 前端架构 (Console)

| 层级 | 文件数 | 说明 |
|------|--------|------|
| 组件 | 1 | ReportDialog (举报对话框) |
| 使用位置 | 3 | ForumThreadView, SolutionDetail, CommentNode |

---

## 二、API 端点对齐分析

### 2.1 完全对齐的端点

| # | 后端端点 | 前端 API 函数 | 状态 |
|---|---------|-------------|------|
| 1 | `GET /moderation/queue` | `moderationQueueApi.getQueue()` | **对齐** |
| 2 | `GET /moderation/queue/:id` | `moderationQueueApi.getQueueItem()` | **对齐** |
| 3 | `GET /moderation/queue/entity/:entityType/:entityId` | `moderationQueueApi.getQueueByEntity()` | **对齐** |
| 4 | `GET /moderation/queue/stats` | `moderationQueueApi.getStats()` | **对齐** |
| 5 | `POST /moderation/queue/:id/claim` | `moderationQueueApi.claimItem()` | **对齐** |
| 6 | `POST /moderation/queue/:id/assign` | `moderationQueueApi.assignItem()` | **对齐** |
| 7 | `PATCH /moderation/queue/:id/unassign` | `moderationQueueApi.unassignItem()` | **对齐** |
| 8 | `POST /moderation/queue/:id/action` | `moderationQueueApi.performAction()` | **对齐** |
| 9 | `POST /moderation/queue/batch-action` | `moderationQueueApi.batchAction()` | **对齐** |
| 10 | `GET /moderation/reports` | `reportsApi.getReports()` | **对齐** |
| 11 | `GET /moderation/reports/:id` | `reportsApi.getReport()` | **对齐** |
| 12 | `GET /moderation/reports/entity/:entityType/:entityId` | `reportsApi.getReportsByEntity()` | **对齐** |
| 13 | `POST /moderation/reports` | `reportsApi.createReport()` | **对齐** |
| 14 | `GET /moderation/appeals` | `appealsApi.getAppeals()` | **对齐** |
| 15 | `GET /moderation/appeals/my` | `appealsApi.getMyAppeals()` | **对齐** |
| 16 | `GET /moderation/appeals/:id` | `appealsApi.getAppeal()` | **对齐** |
| 17 | `GET /moderation/appeals/stats` | `appealsApi.getStats()` | **对齐** |
| 18 | `POST /moderation/appeals` | `appealsApi.createAppeal()` | **对齐** |
| 19 | `POST /moderation/appeals/:id/review` | `appealsApi.reviewAppeal()` | **对齐** |

### 2.2 未对齐的端点

| # | 后端端点 | 前端状态 | 问题 |
|---|---------|---------|------|
| 20 | `GET /moderation/enums` | **未调用** | 前端硬编码枚举，未使用后端动态枚举接口 |

**结论**: 19/20 端点完全对齐，1 个端点未被使用。

---

## 三、DTO 类型颗粒度对比

### 3.1 ModerationQueueVO — 响应类型

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| id | String | string | **对齐** |
| entityType | String | ModeratableEntityType | **前端更严格** |
| entityId | String | string | **对齐** |
| parentId | String | string? | **对齐** |
| authorId | String | string? | **对齐** |
| authorName | String | string? | **对齐** |
| authorUsername | String | string? | **对齐** |
| priority | Integer | number | **对齐** |
| status | String | ModerationStatus | **前端更严格** |
| reportCount | Integer | number | **对齐** |
| primaryCategory | String | ReportCategory | **前端更严格** |
| assignedToId | String | string? | **对齐** |
| assignedToName | String | string? | **对齐** |
| assignedToUsername | String | string? | **对齐** |
| assignedAt | LocalDateTime | Date? | **对齐** (JSON 序列化兼容) |
| reviewedById | String | string? | **对齐** |
| reviewedByName | String | string? | **对齐** |
| reviewedAt | LocalDateTime | Date? | **对齐** |
| resolution | String | string? | **对齐** |
| resolutionNote | String | string? | **对齐** |
| createdAt | LocalDateTime | Date | **对齐** |
| updatedAt | LocalDateTime | Date | **对齐** |
| resolvedAt | LocalDateTime | Date? | **对齐** |

**结论**: 字段完全对齐。前端使用枚举类型替代裸字符串，提升类型安全性。

### 3.2 ReportVO — 响应类型

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| id | String | string | **对齐** |
| reporterId | String | string | **对齐** |
| reporterName | String | string? | **对齐** |
| reporterUsername | String | string? | **对齐** |
| entityType | String | ModeratableEntityType | **前端更严格** |
| entityId | String | string | **对齐** |
| category | String | ReportCategory | **前端更严格** |
| reason | String | string? | **对齐** |
| evidence | String | string? | **对齐** |
| status | String | ReportStatus | **前端更严格** |
| queueId | String | string? | **对齐** |
| createdAt | LocalDateTime | Date | **对齐** |
| updatedAt | LocalDateTime | Date | **对齐** |

**结论**: 字段完全对齐。前端枚举化是合理的类型增强。

### 3.3 AppealVO — 响应类型

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| id | String | string | **对齐** |
| queueId | String | string | **对齐** |
| appellantId | String | string | **对齐** |
| appellantName | String | string? | **对齐** |
| appellantUsername | String | string? | **对齐** |
| reason | String | string | **对齐** |
| evidence | String | string? | **对齐** |
| status | String | AppealStatus | **前端更严格** |
| reviewedById | String | string? | **对齐** |
| reviewedByName | String | string? | **对齐** |
| reviewedAt | LocalDateTime | Date? | **对齐** |
| response | String | string? | **对齐** |
| createdAt | LocalDateTime | Date | **对齐** |
| updatedAt | LocalDateTime | Date | **对齐** |

**结论**: 字段完全对齐。

### 3.4 ModerationStatsVO — 统计响应

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| pendingCount | long | number | **对齐** |
| underReviewCount | long | number | **对齐** |
| resolvedCount | long | number | **对齐** |
| dismissedCount | long | number | **对齐** |
| resolvedToday | long | number | **对齐** |
| avgResolutionTimeHours | Double | number? | **对齐** |
| pendingAppealsCount | long | number | **对齐** |
| byCategory | Map<String, Long> | Record<string, number>? | **对齐** |
| byEntityType | Map<String, Long> | Record<string, number>? | **对齐** |

**结论**: 完全对齐。

### 3.5 AppealStatsVO — 申诉统计响应

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| totalPending | long | number | **对齐** |
| totalUnderReview | long | number | **对齐** |
| totalApproved | long | number | **对齐** |
| totalRejected | long | number | **对齐** |
| avgReviewTimeHours | Double | number? | **对齐** |

**结论**: 完全对齐。

### 3.6 BatchActionResultVO — 批量操作响应

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| successCount | int | number | **对齐** |
| failureCount | int | number | **对齐** |
| errors[].queueId | String | string | **对齐** |
| errors[].message | String | string | **对齐** |

**结论**: 完全对齐。

---

## 四、请求 DTO 对比

### 4.1 PerformModerationActionDTO

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| action | String (@NotBlank) | ModerationActionType | **前端更严格** |
| note | String | string? | **对齐** |
| durationDays | Integer (@Min 1, @Max 3650) | number? | **对齐** |

**问题**: 后端 `action` 字段为裸 `String`，未使用 `ModerationActionType` 枚举。Service 层中用 `Set.of("DELETED", "HIDDEN", ...)` 硬编码验证，而非枚举类型检查。

### 4.2 CreateReportDTO

| 字段 | 后端类型 | Console 前端 | Management 前端 | 对齐 |
|------|---------|------------|----------------|------|
| entityType | String (@NotBlank) | ModeratableEntityType | ModeratableEntityType | **对齐** |
| entityId | String (@NotBlank) | string | string | **对齐** |
| category | String (@NotBlank) | ReportCategory (7值) | ReportCategory (9值) | **Console 缺少 2 值** |
| reason | String | string? | string? | **对齐** |
| evidence | String | **未发送** | string? | **Console 缺失** |

**问题**:
1. Console 的 `ReportDialog` 不发送 `evidence` 字段
2. Console 的 `ReportCategory` 枚举缺少 `WRONG_ANSWER` 和 `COPYRIGHT`（Management 有 9 值，Console 有 7 值）

### 4.3 CreateAppealDTO

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| queueId | String (@NotBlank) | string | **对齐** |
| reason | String (@NotBlank) | string | **对齐** |
| evidence | String | string? | **对齐** |

**结论**: 完全对齐。

### 4.4 ReviewAppealDTO

| 字段 | 后端类型 | 前端类型 | 对齐 |
|------|---------|---------|------|
| decision | String | AppealStatus.APPROVED \| AppealStatus.REJECTED | **前端更严格** |
| response | String | string? | **对齐** |

**问题**: 后端 `decision` 为裸 `String`，前端使用枚举字面量联合类型，更安全。

---

## 五、枚举值颗粒度对比

### 5.1 ReportCategory

| 值 | 后端 | Management | Console |
|---|------|-----------|---------|
| SPAM | ✅ | ✅ | ✅ |
| HARASSMENT | ✅ | ✅ | ✅ |
| HATE_SPEECH | ✅ | ✅ | ✅ |
| VIOLENCE | ✅ | ✅ | ✅ |
| SEXUAL_CONTENT | ✅ | ✅ | ✅ |
| MISINFORMATION | ✅ | ✅ | ✅ |
| WRONG_ANSWER | ✅ | ✅ | ❌ |
| COPYRIGHT | ✅ | ✅ | ❌ |
| OTHER | ✅ | ✅ | ✅ |

**问题**: Console 的 `ReportDialog` 硬编码了 7 个类别，缺少后端和管理端都支持的 `WRONG_ANSWER` 和 `COPYRIGHT`。

### 5.2 ModerationActionType

| 值 | 后端 | 前端 |
|---|------|------|
| DELETED | ✅ | ✅ |
| HIDDEN | ✅ | ✅ |
| RESTORED | ✅ | ✅ |
| WARNED | ✅ | ✅ |
| TEMP_BANNED | ✅ | ✅ |
| PERM_BANNED | ✅ | ✅ |
| DISMISSED | ✅ | ✅ |
| RESOLVED | ✅ | ✅ |
| APPEAL_PENDING | ✅ | ✅ |
| APPEAL_APPROVED | ✅ | ✅ |
| APPEAL_REJECTED | ✅ | ✅ |

**结论**: 完全对齐。

### 5.3 ModerationStatus / ReportStatus / AppealStatus

所有三个状态枚举前后端完全对齐。

---

## 六、幽灵类型（前端定义但无对应 API）

| 类型 | 前端文件 | 对应后端 | 问题 |
|------|---------|---------|------|
| `ModerationAction` (带 `performer` 嵌套) | moderation.ts | 无 VO | 前端定义了 `performer: { id, username, displayName, avatarUrl }` 嵌套对象，但后端无此结构 |
| `UserWarning` | moderation.ts | Entity 存在，无独立 API | 前端定义了完整类型但无 API 调用 |
| `UserBan` | moderation.ts | Entity 存在，无独立 API | 前端定义了完整类型但无 API 调用 |
| `QueryUserWarningsParams` | moderation.ts | 无对应端点 | 定义了查询参数但无 API 函数使用 |
| `QueryUserBansParams` | moderation.ts | 无对应端点 | 定义了查询参数但无 API 函数使用 |
| `CreateUserBanDto` | moderation.ts | 无对应端点 | 定义了创建 DTO 但无 API 函数使用 |
| `RevokeBanDto` | moderation.ts | 无对应端点 | 定义了撤销 DTO 但无 API 函数使用 |

**问题**: 前端预定义了 7 个类型/接口，但后端没有对应的 API 端点。这些是"预留代码"——可能是为未来功能准备的，但当前是死代码。

---

## 七、Service 层颗粒度分析

### 7.1 过粗方法: `performAction()`

**位置**: `ModerationServiceImpl.java:189-277`

该方法承担了过多职责：
1. 验证 action 类型有效性
2. 验证 queue 项存在性
3. 创建 `ModerationAction` 记录
4. 更新 `ModerationQueue` 状态
5. 根据 action 类型执行不同副作用（8 个分支）:
   - `DELETED` → 删除内容
   - `HIDDEN` → 隐藏内容
   - `RESTORED` → 恢复内容
   - `WARNED` → 创建 `UserWarning`
   - `TEMP_BANNED` → 创建 `UserBan`
   - `PERM_BANNED` → 创建 `UserBan`
   - `APPEAL_PENDING/APPROVED/REJECTED` → 更新申诉状态
6. 更新 content flag
7. 更新关联 reports 状态

**建议**: 拆分为策略模式或独立处理器方法。

### 7.2 略粗方法: `createReport()`

创建 report 和创建/更新 queue 的逻辑混合在同一方法中。建议拆分为 `createReportRecord()` 和 `upsertQueueItem()`。

### 7.3 后端枚举未类型化

`PerformModerationActionDTO.action` 使用 `String` 而非 `ModerationActionType` 枚举。Service 层中使用 `Set.of("DELETED", ...)` 硬编码验证，而非枚举类型检查。

---

## 八、Console 前端颗粒度问题

### 8.1 举报表单缺失字段

`ReportDialog.vue` 只发送 4 个字段（`entityType`, `entityId`, `category`, `reason`），缺少 `evidence` 字段。后端 DTO 接受此字段。

### 8.2 枚举值不完整

Console 硬编码了 7 个 `ReportCategory` 值，缺少 `WRONG_ANSWER` 和 `COPYRIGHT`。对于编程竞赛平台，`WRONG_ANSWER` 是一个重要的举报类别。

### 8.3 直接使用 `apiPost` 而非类型化 API

Console 的 `ReportDialog` 直接调用 `apiPost("/moderation/reports", {...})`，而非使用类型化的 API 函数。这绕过了 Management 前端中定义的类型安全接口。

---

## 九、颗粒度对齐总结

### 对齐良好的部分

| 维度 | 评价 |
|------|------|
| API 端点覆盖 | 19/20 对齐 (95%) |
| 响应 DTO 字段 | 100% 对齐 |
| 请求 DTO 字段 | Management 侧 100% 对齐 |
| 枚举值 (Management) | 100% 对齐 |
| 统计数据结构 | 100% 对齐 |
| 批量操作结构 | 100% 对齐 |

### 需要对齐的问题

| # | 问题 | 严重级别 | 位置 | 建议 |
|---|------|---------|------|------|
| 1 | Console ReportCategory 缺少 WRONG_ANSWER/COPYRIGHT | **MEDIUM** | console/ReportDialog.vue | 同步枚举值 |
| 2 | Console 举报不发送 evidence 字段 | **LOW** | console/ReportDialog.vue | 添加证据上传 |
| 3 | 后端 action 字段使用 String 而非枚举 | **MEDIUM** | PerformModerationActionDTO.java | 使用 ModerationActionType 枚举 |
| 4 | Service 层 performAction() 方法过粗 | **HIGH** | ModerationServiceImpl.java:189-277 | 拆分为策略模式 |
| 5 | 前端幽灵类型（7个无对应API） | **LOW** | management/moderation.ts | 移除或实现对应API |
| 6 | `/moderation/enums` 端点未被前端使用 | **LOW** | 后端 + 前端 | 使用动态枚举或移除端点 |
| 7 | Console 直接使用 apiPost 绕过类型 | **LOW** | console/ReportDialog.vue | 使用共享类型定义 |
| 8 | 前端 ModerationAction.performer 嵌套对象无后端对应 | **MEDIUM** | management/moderation.ts | 确认数据来源或移除 |

---

## 十、建议优先级

1. **P1 (立即)**: 拆分 `performAction()` 方法 — 当前 90 行方法包含 8 个分支，维护困难
2. **P2 (短期)**: 后端 DTO 使用枚举类型替代裸字符串 — 提升编译时安全
3. **P2 (短期)**: 同步 Console ReportCategory 枚举 — 添加 WRONG_ANSWER 和 COPYRIGHT
4. **P3 (中期)**: 清理前端幽灵类型 — 移除 7 个未使用的类型定义
5. **P3 (中期)**: 决定 `/moderation/enums` 端点去留 — 使用或移除
6. **P4 (低优)**: Console 举报添加 evidence 字段 — 增强举报质量
