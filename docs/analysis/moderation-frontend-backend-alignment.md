# Moderation 模块前后端颗粒度对齐分析

*Generated: 2026-05-18*

## 一、API 端点对齐

### 1.1 匹配的端点 (10/17)

| 端点 | 后端 | 前端 | 状态 |
|------|------|------|------|
| GET /moderation/queue | `getQueue()` | `moderationQueueApi.getQueue()` | ✅ |
| GET /moderation/queue/stats | `getStats()` | `moderationQueueApi.getStats()` | ✅ |
| GET /moderation/queue/{id} | `getQueueItem()` | `moderationQueueApi.getQueueItem()` | ✅ |
| POST /moderation/queue/{id}/claim | `claim()` | `moderationQueueApi.claimItem()` | ✅ |
| POST /moderation/queue/{id}/assign | `assign()` | `moderationQueueApi.assignItem()` | ✅ |
| PATCH /moderation/queue/{id}/unassign | `unassign()` | `moderationQueueApi.unassignItem()` | ✅ |
| POST /moderation/queue/{id}/action | `performAction()` | `moderationQueueApi.performAction()` | ✅ |
| GET /moderation/queue/entity/{type}/{id} | `findByEntity()` | `moderationQueueApi.getQueueByEntity()` | ✅ |
| POST /moderation/queue/batch-action | `batchAction()` | `moderationQueueApi.batchAction()` | ✅ |
| POST /moderation/reports | `createReport()` | `reportsApi.createReport()` | ✅ |

### 1.2 HTTP 方法不匹配 (1)

| 端点 | 后端 | 前端 | 问题 |
|------|------|------|------|
| /moderation/appeals/{id}/review | **POST** | **apiPatch (PATCH)** | 方法不一致 |

### 1.3 前端有但后端缺失的端点 (5)

| 前端 API | 期望端点 | 后端状态 |
|----------|----------|----------|
| `reportsApi.getReport()` | GET /moderation/reports/{id} | ❌ 无对应端点 |
| `appealsApi.getMyAppeals()` | GET /moderation/appeals/my | ❌ 无对应端点 |
| `appealsApi.getStats()` | GET /moderation/appeals/stats | ❌ 无对应端点 |
| User Warnings CRUD | GET/POST /moderation/warnings/* | ❌ 无对应端点 |
| User Bans CRUD | GET/POST /moderation/bans/* | ❌ 无对应端点 |

### 1.4 后端有但前端未调用的端点 (1)

| 后端端点 | 前端状态 |
|----------|----------|
| GET /moderation/enums | ❌ 前端硬编码了枚举值，未调用此端点 |

---

## 二、DTO/类型字段对齐

### 2.1 请求 DTO 字段不匹配

#### QueryModerationQueueDTO vs QueryModerationQueueParams

| 字段 | 后端 | 前端 | 问题 |
|------|------|------|------|
| assignedTo / assignedToId | `assignedTo` | `assignedToId` | **字段名不一致** |
| primaryCategory | ❌ | `primaryCategory` | 前端多出，后端不支持 |
| minPriority | ❌ | `minPriority` | 前端多出，后端不支持 |
| sortBy / sortOrder | ❌ | `sortBy`, `sortOrder` | 前端多出，后端不支持 |

#### QueryReportsDTO vs QueryReportsParams

| 字段 | 后端 | 前端 | 问题 |
|------|------|------|------|
| entityType | ❌ | `entityType` | 前端多出，后端不支持 |
| entityId | ❌ | `entityId` | 前端多出，后端不支持 |
| sortBy / sortOrder | ❌ | `sortBy`, `sortOrder` | 前端多出，后端不支持 |

#### QueryAppealsDTO vs QueryAppealsParams

| 字段 | 后端 | 前端 | 问题 |
|------|------|------|------|
| sortBy / sortOrder | ❌ | `sortBy`, `sortOrder` | 前端多出，后端不支持 |

#### AssignDTO vs AssignModerationDto

| 字段 | 后端 | 前端 | 问题 |
|------|------|------|------|
| assignedTo / assignedToId | `assignedTo` | `assignedToId` | **字段名不一致** |

#### BatchModerationActionDTO

| 字段 | 后端 | 前端 | 问题 |
|------|------|------|------|
| durationDays | 有 | ❌ 缺失 | 前端漏掉了 `durationDays` |

### 2.2 响应 VO 字段不匹配

#### BatchActionResultVO vs BatchActionResult

| 字段 | 后端 | 前端 | 问题 |
|------|------|------|------|
| failureCount / errorCount | `failureCount` | `errorCount` | **字段名不一致** |
| errors[].message / errors[].error | `message` | `error` | **字段名不一致** |

#### ReportVO vs Report

| 字段 | 后端 | 前端 | 问题 |
|------|------|------|------|
| parentId | ❌ | `parentId?` | 前端多出，后端不返回 |

#### ModerationAction (前端类型 vs 后端实体)

| 字段 | 后端 | 前端 | 问题 |
|------|------|------|------|
| action / actionType | `action` | `actionType` | **字段名不一致** |
| performedById vs performedBy + performer | `performedById` | `performedBy` + `performer` 对象 | **结构不一致** |

---

## 三、前端定义了但后端无 API 支持的类型

| 前端类型 | 用途 | 后端状态 |
|----------|------|----------|
| `UserWarning` | 用户警告 | 仅有 Entity + Mapper，无 VO/Controller |
| `UserBan` | 用户封禁 | 仅有 Entity + Mapper，无 VO/Controller |
| `CreateUserBanDto` | 创建封禁 | 无对应端点 |
| `RevokeBanDto` | 解除封禁 | 无对应端点 |
| `QueryUserWarningsParams` | 查询警告 | 无对应端点 |
| `QueryUserBansParams` | 查询封禁 | 无对应端点 |

---

## 四、汇总：需修复的问题

### CRITICAL - 会导致运行时错误

| # | 问题 | 修复方向 |
|---|------|----------|
| 1 | `reviewAppeal` HTTP 方法不匹配：后端 POST vs 前端 PATCH | 前端改用 `apiPost`，或后端改 PATCH |
| 2 | `AssignDTO.assignedTo` vs `AssignModerationDto.assignedToId` 字段名不一致 | 统一为 `assignedToId` |
| 3 | `BatchActionResultVO.failureCount` vs `BatchActionResult.errorCount` 不一致 | 统一为 `failureCount` |
| 4 | `BatchError.message` vs 前端 `error` 不一致 | 统一为 `message` |

### HIGH - 功能缺失

| # | 问题 | 修复方向 |
|---|------|----------|
| 5 | 前端 `reportsApi.getReport()` 无后端端点 | 后端增加 GET /reports/{id} |
| 6 | 前端 `appealsApi.getMyAppeals()` 无后端端点 | 后端增加 GET /appeals/my |
| 7 | 前端 `appealsApi.getStats()` 无后端端点 | 后端增加 GET /appeals/stats |
| 8 | UserWarning/UserBan 无 CRUD 端点 | 后端增加警告/封禁管理端点 |
| 9 | 前端未调用 GET /moderation/enums | 前端改为动态获取枚举 |

### MEDIUM - 查询能力不对齐

| # | 问题 | 修复方向 |
|---|------|----------|
| 10 | 后端 Query DTO 缺少 `primaryCategory` 过滤 | 后端增加字段 |
| 11 | 后端 Query DTO 缺少 `minPriority` 过滤 | 后端增加字段 |
| 12 | 后端 Query DTO 缺少 `sortBy` / `sortOrder` | 后端增加排序支持 |
| 13 | 后端 QueryReportsDTO 缺少 `entityType` / `entityId` 过滤 | 后端增加字段 |
| 14 | 前端 `BatchModerationActionDto` 缺少 `durationDays` | 前端增加字段 |
| 15 | 前端 Report 多出 `parentId` 字段 | 前端移除或后端增加 |
| 16 | `ModerationAction` 字段名/结构不一致 | 后端增加 VO 并统一 |

---

**统计**: 4 CRITICAL / 5 HIGH / 7 MEDIUM = 共 16 个待修复问题
