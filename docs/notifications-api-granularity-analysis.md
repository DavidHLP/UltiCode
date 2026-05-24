# Notifications API 前后端颗粒度对齐分析

> 生成日期: 2026-05-24 | 分析范围: Management 前端 (`/notifications`) ↔ 后端 API

## 执行摘要

Management 通知页面 (`/notifications`) 调用后端 `AdminNotificationController` (`/admin/notifications`) 的 4 个端点。前后端在 CRUD 操作层面基本对齐，但存在 **7 个颗粒度/逻辑不对齐问题**，涉及分页缺失、枚举不一致、类型定义冗余、去重逻辑脆弱、字段映射不对称等。

---

## 1. 后端 API 端点清单

### 1.1 Admin 端点 (`/admin/notifications`)

| 方法 | 路径 | 控制器 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|--------|------|
| GET | `/admin/notifications` | `AdminNotificationController` | — | `List<AdminNotificationVO>` | 获取所有系统通知（无分页） |
| POST | `/admin/notifications` | `AdminNotificationController` | `CreateSystemNotificationRequest` | `AdminNotificationVO` | 创建系统通知 |
| PUT | `/admin/notifications/{id}` | `AdminNotificationController` | `UpdateSystemNotificationRequest` | `AdminNotificationVO` | 更新系统通知 |
| DELETE | `/admin/notifications/{id}` | `AdminNotificationController` | — | `Void` | 删除系统通知 |

### 1.2 用户端端点 (`/notifications`) — Management 不直接调用

| 方法 | 路径 | 控制器 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|--------|------|
| GET | `/notifications` | `NotificationController` | `NotificationQueryDTO` | `PageResult<NotificationVO>` | 分页获取用户通知 |
| GET | `/notifications/unread-count` | `NotificationController` | — | `UnreadCountVO` | 未读计数 |
| GET | `/notifications/preferences` | `NotificationController` | — | `NotificationPreferenceVO` | 获取偏好 |
| PATCH | `/notifications/preferences` | `NotificationController` | `UpdateNotificationPreferenceDTO` | `NotificationPreferenceVO` | 更新偏好 |
| POST | `/notifications/mark-all-read` | `NotificationController` | — | `Void` | 全部标为已读 |
| DELETE | `/notifications/clear` | `NotificationController` | — | `Void` | 清空通知 |
| PATCH | `/notifications/{id}` | `NotificationController` | `UpdateNotificationDTO` | `NotificationVO` | 更新单条通知 |
| DELETE | `/notifications/{id}` | `NotificationController` | — | `Void` | 删除单条通知 |

---

## 2. 前端 API 调用清单

### 2.1 API 层 (`management/src/api/admin/notifications.ts`)

| 方法 | 路径 | 请求类型 | 响应类型 |
|------|------|----------|----------|
| `getAll()` | GET `/admin/notifications` | — | `SystemAnnouncement[]` |
| `create(data)` | POST `/admin/notifications` | `CreateNotificationDto` | `SystemAnnouncement` |
| `update(id, data)` | PUT `/admin/notifications/${id}` | `UpdateNotificationDto` | `SystemAnnouncement` |
| `delete(id)` | DELETE `/admin/notifications/${id}` | — | `{ message: string }` |

### 2.2 Store 层 (`management/src/stores/admin/notifications.ts`)

| 方法 | 调用 | 说明 |
|------|------|------|
| `fetchAnnouncements()` | `adminNotifications.getAll()` | 获取全部，赋值给 `announcements` |
| `createNotification(data)` | `adminNotifications.create(data)` → `fetchAnnouncements()` | 创建后刷新 |
| `updateNotification(id, data)` | `adminNotifications.update(id, data)` → `fetchAnnouncements()` | 更新后刷新 |
| `deleteAnnouncement(id)` | `adminNotifications.delete(id)` | 本地过滤删除（不刷新） |

### 2.3 视图层 (`NotificationsListView.vue`)

- 表格列: `title`, `type`, `createdAt`, `creator`, `actions`
- 客户端过滤: 搜索（title + creator.username）、类型筛选
- 统计: total / system / contest / submission / other
- 操作: 创建、编辑、删除

---

## 3. DTO / 类型对比

### 3.1 AdminNotificationVO (后端) ↔ SystemAnnouncement (前端)

| 后端字段 (`AdminNotificationVO`) | 前端字段 (`SystemAnnouncement`) | 类型匹配 | 状态 |
|----------------------------------|----------------------------------|----------|------|
| `id: String` | `id: string` | ✅ | 对齐 |
| `title: String` | `title: string` | ✅ | 对齐 |
| `content: String` | `content: string` | ✅ | 对齐 |
| `type: String` | `type: NotificationType` | ⚠️ | **不对齐** — 后端 String，前端枚举 |
| `category: String` | `category?: NotificationCategory` | ⚠️ | **不对齐** — 后端 String，前端枚举 |
| `createdAt: LocalDateTime` | `createdAt: string` | ✅ | 对齐（JSON 序列化自动转换） |
| `creator: CreatorInfo` | `creator: { id, username, avatar }` | ✅ | 对齐 |

### 3.2 CreateSystemNotificationRequest (后端) ↔ CreateNotificationDto (前端)

| 后端字段 | 前端字段 | 类型匹配 | 状态 |
|----------|----------|----------|------|
| `title: @NotBlank String` | `title: string` | ✅ | 对齐 |
| `content: @NotBlank String` | `content: string` | ✅ | 对齐 |
| `type: @NotBlank String` | `type: NotificationType` | ⚠️ | 枚举 vs String |
| `category: String (default "SYSTEM")` | `category?: NotificationCategory` | ⚠️ | 枚举 vs String |
| `target: @NotBlank String` | `target: NotificationTarget` | ⚠️ | 枚举 vs String |
| `userIds: List<String>` | `userIds?: string[]` | ✅ | 对齐 |

### 3.3 UpdateSystemNotificationRequest (后端) ↔ UpdateNotificationDto (前端)

| 后端字段 | 前端字段 | 类型匹配 | 状态 |
|----------|----------|----------|------|
| `title: @NotBlank String` | `title: string` | ✅ | 对齐 |
| `content: @NotBlank String` | `content: string` | ✅ | 对齐 |
| `type: String` | `type?: NotificationType` | ⚠️ | 枚举 vs String |
| `category: String` | `category?: NotificationCategory` | ⚠️ | 枚举 vs String |

---

## 4. 枚举对比

### 4.1 NotificationType

| 后端 `NotificationType` (entity/enums) | 前端 `NotificationType` (api/admin/notifications.ts) | 前端 `NotificationType` (lib/entities/notification.ts) | WebSocket `NotificationPayload.NotificationType` |
|---|---|---|---|
| COMMENT | COMMENT | — | — |
| REPLY | REPLY | — | reply |
| MENTION | MENTION | — | mention |
| UPVOTE | UPVOTE | — | — |
| FOLLOW | FOLLOW | — | — |
| SYSTEM | SYSTEM | SYSTEM | system |
| SUBMISSION | SUBMISSION | — | problem_solved |
| CONTEST | CONTEST | CONTEST | contest_reminder |
| CONTEST_REMINDER | — | — | contest_reminder |

**问题 1: 前端存在两套 NotificationType 定义**
- `api/admin/notifications.ts`: 8 个值 (COMMENT, REPLY, MENTION, UPVOTE, FOLLOW, SYSTEM, SUBMISSION, CONTEST)
- `lib/entities/notification.ts`: 5 个值 (SYSTEM, CONTEST, PROBLEM, FORUM, ACCOUNT) — 完全不同的值集

**问题 2: 后端 `CONTEST_REMINDER` 在前端 Admin enum 中缺失**

**问题 3: WebSocket NotificationType 使用小写常量 (reply, mention, system...)，与后端枚举/前端枚举大小写不一致**

### 4.2 NotificationCategory

| 后端 `NotificationCategory` (entity/enums) | 前端 `NotificationCategory` (api) | 前端 i18n (zh-CN) |
|---|---|---|
| COMMUNICATION | COMMUNICATION | — (缺失) |
| MARKETING | MARKETING | — (缺失) |
| SECURITY | SECURITY | — (缺失) |
| SYSTEM | SYSTEM | 系统 |
| CONTEST | CONTEST | — (缺失) |

**问题 4: i18n 的 `categories` 键与后端枚举完全不对齐** — i18n 定义了 ANNOUNCEMENT, PROMOTION, UPDATE, WARNING，而后端枚举是 COMMUNICATION, MARKETING, SECURITY, SYSTEM, CONTEST

### 4.3 NotificationTarget

| 后端 (String 字面量) | 前端 `NotificationTarget` |
|---|---|
| "ALL" | ALL |
| "USERS" | USERS |

✅ 对齐（后端用 String 字面量比较，前端用枚举）

---

## 5. 颗粒度不对齐问题

### 🔴 P1: Admin 列表接口无分页

| 维度 | 后端 | 前端 |
|------|------|------|
| 响应类型 | `List<AdminNotificationVO>` (全量) | `SystemAnnouncement[]` (全量) |
| 分页参数 | 无 | 无 |
| 服务端过滤 | 无 | 无 |

**影响**: 当系统通知量增长时，全量返回会导致性能问题。前端做了客户端搜索和类型过滤，但数据量受限于全量加载。

**建议**: 添加 `AdminNotificationQueryDTO`（含 page, limit, type, search 参数），返回 `PageResult<AdminNotificationVO>`。

### 🔴 P2: 去重逻辑脆弱

`AdminNotificationServiceImpl.getAllSystemNotifications()` 使用 `title + type + createdAt` 作为去重键。这存在以下问题：
- 同一秒内创建的同名同类型通知会被错误去重
- 不同管理员创建相同标题的通知会被合并
- `createdAt` 精度到秒级，高并发下可能冲突

**建议**: 在 `Notification` 实体中添加 `announcementId` 字段，系统通知创建时生成唯一 announcementId，所有用户副本共享此 ID。去重改为按 `announcementId` 分组。

### 🟡 P3: 删除/更新操作的级联逻辑基于内容匹配

`deleteNotification()` 和 `updateSystemNotification()` 通过 `title + type + category=SYSTEM + createdAt` 匹配所有"相关"通知记录进行级联操作。这同样存在 P2 中的脆弱性问题。

**建议**: 同 P2，使用 `announcementId` 进行级联匹配。

### 🟡 P4: 前端 delete 响应类型不匹配

| 后端 | 前端 |
|------|------|
| `Result<Void>` (无数据) | `{ message: string }` |

前端 `adminNotifications.delete()` 声明返回 `{ message: string }`，但后端返回 `Result.success()` 即 `{ code: 0, message: "success", data: null }`。前端实际不会使用返回值，但类型声明不准确。

**建议**: 将前端 delete 返回类型改为 `void` 或 `null`。

### 🟡 P5: `lib/entities/notification.ts` 中的 NotificationType 与 API 层完全不一致

`lib/entities/notification.ts` 定义了 `'SYSTEM' | 'CONTEST' | 'PROBLEM' | 'FORUM' | 'ACCOUNT'`，以及 `NotificationPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'`。这些类型：
- 与后端枚举不匹配（PROBLEM, FORUM, ACCOUNT 在后端不存在）
- NotificationPriority 在后端完全不存在
- 似乎是旧版或预留的"幽灵类型"

**建议**: 删除或重构此文件，统一使用 `api/admin/notifications.ts` 中的枚举定义。

### 🟢 P6: 前端客户端过滤无法利用后端分页

前端 `filteredData` 在客户端做搜索和类型过滤，但由于后端无分页/过滤参数，这是当前唯一可行方案。如果 P1 修复后，应将过滤逻辑移至后端。

### 🟢 P7: Store 的 deleteAnnouncement 不刷新列表

`deleteAnnouncement()` 使用本地 `filter` 移除已删除项，而 `createNotification()` 和 `updateNotification()` 都调用 `fetchAnnouncements()` 刷新。不一致的刷新策略可能在级联删除场景下导致数据不同步（后端删除一条可能级联删除多条用户副本，本地 filter 只移除一条）。

**建议**: `deleteAnnouncement()` 成功后也调用 `fetchAnnouncements()` 刷新。

---

## 6. 字段映射问题

### 6.1 `body` vs `content` 映射

| 层 | 字段名 |
|----|--------|
| Entity `Notification` | `body` |
| DTO `NotificationVO` (用户端) | `body` |
| DTO `AdminNotificationVO` (管理端) | `content` (mapped from body) |
| 前端 `SystemAnnouncement` | `content` |
| `CreateSystemNotificationRequest` | `content` |
| `CreateNotificationDto` (前端) | `content` |

后端在 `toAdminVO()` 中做了 `vo.setContent(notification.getBody())` 映射，所以 Admin 侧统一用 `content`。用户端 `NotificationVO` 仍用 `body`。**这个映射是合理的**，但需要在文档中明确说明。

### 6.2 `creator` 信息来源

`AdminNotificationVO.creator` 从 `notification.metadata.createdBy` 提取，每次查询都通过 `userMapper.selectById()` 获取用户信息。**N+1 查询问题** — 列表查询时对每条通知都做一次用户查询。

**建议**: 批量获取 creator 信息，或在 metadata 中直接存储 username/avatar 避免额外查询。

---

## 7. 缺失功能分析

### 7.1 Management 前端缺失但后端已支持的功能

| 功能 | 后端端点 | Management 前端 |
|------|----------|-----------------|
| 查看单条通知详情 | 无独立端点 | ❌ 无（表格行无展开/详情） |
| 按分类筛选 | 后端无参数 | 客户端仅按 type 筛选，无 category 筛选 |
| 批量操作 | 无 | ❌ 无批量删除/标记 |

### 7.2 后端缺失但前端可能需要的功能

| 功能 | 说明 |
|------|------|
| Admin 通知分页 | P1 已描述 |
| Admin 通知搜索 | 后端无 search 参数 |
| Admin 通知统计 | 前端客户端计算，后端无专用端点 |
| 通知发送状态/送达率 | 无追踪机制 |

---

## 8. 完整不对齐问题汇总

| # | 优先级 | 问题 | 影响 | 修复建议 |
|---|--------|------|------|----------|
| 1 | 🔴 P1 | Admin 列表无分页 | 数据量大时性能问题 | 添加 `AdminNotificationQueryDTO` + `PageResult` |
| 2 | 🔴 P2 | 去重逻辑脆弱 (title+type+createdAt) | 数据错误/丢失 | 添加 `announcementId` 字段 |
| 3 | 🟡 P3 | 删除/更新级联基于内容匹配 | 同 P2 | 同 P2 |
| 4 | 🟡 P4 | delete 返回类型不匹配 | 类型不安全 | 改为 `void` |
| 5 | 🟡 P5 | `lib/entities/notification.ts` 幽灵类型 | 维护混乱/误导 | 删除或重构 |
| 6 | 🟡 P6 | i18n categories 与后端枚举不对齐 | UI 显示错误 | 对齐 i18n 键 |
| 7 | 🟡 P7 | `CONTEST_REMINDER` 前端缺失 | 类型不完整 | 添加到前端 enum |
| 8 | 🟢 P8 | Store delete 不刷新列表 | 级联删除后数据不一致 | 改为刷新 |
| 9 | 🟢 P9 | Creator N+1 查询 | 性能问题 | 批量查询或缓存 |
| 10 | 🟢 P10 | WebSocket NotificationType 大小写不一致 | 潜在匹配失败 | 统一大小写 |

---

## 9. 架构建议

### 9.1 短期修复（低风险）

1. **修复 P4**: `adminNotifications.delete()` 返回类型改为 `void`
2. **修复 P5**: 删除或标记 `lib/entities/notification.ts` 为废弃
3. **修复 P6**: 对齐 i18n categories 键为 COMMUNICATION, MARKETING, SECURITY, SYSTEM, CONTEST
4. **修复 P7**: 前端 NotificationType 添加 `CONTEST_REMINDER`
5. **修复 P8**: `deleteAnnouncement()` 成功后调用 `fetchAnnouncements()`

### 9.2 中期修复（中风险）

6. **修复 P1**: Admin 列表添加分页和过滤参数
7. **修复 P9**: Creator 信息批量查询

### 9.3 长期修复（需要 DB 迁移）

8. **修复 P2/P3**: 添加 `announcement_id` 列到 `notifications` 表，重构去重和级联逻辑
9. **修复 P10**: 统一 WebSocket NotificationType 为大写枚举值

---

## 10. 数据流图

```
Management Frontend                    Backend
┌─────────────────────┐              ┌──────────────────────────┐
│ NotificationsListView│              │ AdminNotificationController│
│   ↓ fetchAnnouncements│             │   GET /admin/notifications│
│   ↓ createNotification│             │   POST /admin/notifications│
│   ↓ updateNotification│             │   PUT /admin/notifications/{id}│
│   ↓ deleteAnnouncement │            │   DELETE /admin/notifications/{id}│
└─────────┬───────────┘              └──────────┬───────────────┘
           │                                     │
┌──────────▼───────────┐              ┌─────────▼───────────────┐
│ notifications store   │              │ AdminNotificationService │
│  announcements[]      │              │  getAllSystemNotifications()│
│  isLoading            │              │  createSystemNotification()│
│  error                │              │  updateSystemNotification()│
└──────────┬───────────┘              │  deleteNotification()    │
           │                           └──────────┬───────────────┘
┌──────────▼───────────┐                        │
│ adminNotifications API│              ┌─────────▼───────────────┐
│  getAll() → GET       │              │ NotificationMapper       │
│  create() → POST     │              │  (MyBatis-Plus)          │
│  update() → PUT      │              │  + UserMapper (creator)  │
│  delete() → DELETE   │              └──────────────────────────┘
└───────────────────────┘
```
