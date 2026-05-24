# Plan: Notifications API 前后端颗粒度对齐

## Summary
修复 Management 通知页面的 10 个前后端不对齐问题，包括：添加 Admin 分页接口、引入 announcement_id 重构去重/级联逻辑、对齐枚举定义、修复 i18n 键、统一类型声明、修复 Store 刷新策略、优化 N+1 查询。

## User Story
As a 管理后台管理员, I want 通知管理页面支持分页浏览、精确的去重和级联操作、与后端完全一致的枚举和类型定义, so that 大量通知数据下系统性能稳定、操作结果可靠、UI 显示准确。

## Problem → Solution
**当前**: Admin 通知列表全量返回、去重/级联基于 title+type+createdAt 内容匹配、前端存在幽灵类型和 i18n 不对齐、Store delete 不刷新、Creator N+1 查询
**目标**: 分页查询、基于 announcement_id 的可靠去重/级联、枚举完全对齐、所有操作后刷新、批量查询 Creator

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/notifications-api-granularity-analysis.md`
- **PRD Phase**: N/A
- **Estimated Files**: 22

---

## UX Design

### Before
```
┌─────────────────────────────────────────┐
│  Notifications Management               │
│  ┌─────────────────────────────────────┐│
│  │ Search: [____]  Type: [All ▼]      ││
│  │ Total: 45  System: 30  Contest: 10 ││
│  ├──────┬──────┬─────────┬──────┬─────┤│
│  │Title │Type  │Created  │Creator│Acts ││
│  ├──────┼──────┼─────────┼──────┼─────┤│
│  │...全量加载，无分页...               ││
│  └──────┴──────┴─────────┴──────┴─────┘│
│  ❌ 数据量大时卡顿                      │
│  ❌ 删除后列表可能不同步                │
│  ❌ 类型筛选仅客户端                   │
└─────────────────────────────────────────┘
```

### After
```
┌─────────────────────────────────────────┐
│  Notifications Management               │
│  ┌─────────────────────────────────────┐│
│  │ Search: [____]  Type: [All ▼]      ││
│  │ Category: [All ▼]                  ││
│  ├──────┬──────┬──────┬─────────┬─────┤│
│  │Title │Type  │Categ│Created  │Acts ││
│  ├──────┼──────┼──────┼─────────┼─────┤│
│  │...服务端分页，每页20条...           ││
│  ├─────────────────────────────────────┤│
│  │ < 1 2 3 ... 5 >                    ││
│  └─────────────────────────────────────┘│
│  ✅ 服务端分页+过滤                     │
│  ✅ 删除后刷新列表                      │
│  ✅ 新增 Category 筛选列               │
└─────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| 列表加载 | 全量 GET | 分页 GET + query params | 性能提升 |
| 搜索/过滤 | 客户端 filter | 服务端 page/limit/type/search/category | 减少传输量 |
| 删除后 | 本地 filter 移除 | 刷新全列表 | 级联删除数据一致性 |
| 类型列 | 仅 NotificationType | Type + Category 两列 | 更细粒度分类 |
| 分页 | 无 | 底部分页控件 | 大数据量导航 |

---

## Mandatory Reading

| Priority | File | Why |
|---|---|---|
| P0 | `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminCommentController.java` | 分页 Admin 端点模式参考 |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminCommentQueryDTO.java` | QueryDTO 模式参考 |
| P0 | `management/src/views/comments/CommentsListView.vue` | 分页列表视图模式参考 |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminNotificationController.java` | 当前修改目标 |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminNotificationServiceImpl.java` | 当前修改目标 |
| P1 | `management/src/api/admin/notifications.ts` | 当前修改目标 |
| P1 | `management/src/stores/admin/notifications.ts` | 当前修改目标 |
| P1 | `management/src/views/notifications/NotificationsListView.vue` | 当前修改目标 |
| P2 | `backend-spring/src/main/java/com/ulticode/modules/notification/entity/Notification.java` | 实体结构 |
| P2 | `db-manager/migrations/V109__add_solution_comment_permissions.sql` | 最新迁移编号参考 |

---

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| MyBatis-Plus 分页 | 项目已有模式 | 使用 `Page<T>` + `IPage<T>` 分页查询 |
| Flyway 迁移命名 | 项目约定 | `V{N}__{description}.sql`，当前最大 V109 |

---

## Patterns to Mirror

### PAGINATED_ADMIN_CONTROLLER
// SOURCE: `AdminCommentController.java`
```java
@GetMapping
public Result<PageResult<AdminCommentVO>> list(AdminCommentQueryDTO queryDTO) {
    PageResult<AdminCommentVO> result = adminCommentService.listComments(queryDTO);
    return Result.success(result);
}
```

### ADMIN_QUERY_DTO
// SOURCE: `AdminCommentQueryDTO.java`
```java
@Data
public class AdminCommentQueryDTO {
    private Integer page = 1;
    private Integer limit = 20;
    private String keyword;
    private String type;
    private String status;
    private String sortBy = "createdAt";
    private String sortOrder = "desc";
}
```

### SERVICE_PAGINATION
// SOURCE: `AdminCommentServiceImpl.java`
```java
public PageResult<AdminCommentVO> listComments(AdminCommentQueryDTO queryDTO) {
    Page<ForumComment> page = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
    // ... build query with filters
    IPage<ForumComment> result = forumCommentMapper.selectPage(page, queryWrapper);
    List<AdminCommentVO> vos = result.getRecords().stream()
        .map(this::toAdminVO).collect(Collectors.toList());
    return PageResult.of(vos, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
}
```

### FRONTEND_PAGINATED_API
// SOURCE: `management/src/api/admin/comments.ts`
```typescript
export const adminCommentsApi = {
  getAll: (params?: CommentQueryParams) =>
    apiGet<PageResult<AdminComment>>('/admin/comments', { params }),
  // ...
}
```

### FRONTEND_PAGINATED_STORE
// SOURCE: `management/src/stores/admin/comments.ts`
```typescript
async fetchComments(params?: CommentQueryParams) {
  this.isLoading = true
  try {
    const result = await adminCommentsApi.getAll(params)
    this.comments = result.items
    this.total = result.total
  } finally {
    this.isLoading = false
  }
}
```

### FLYWAY_MIGRATION
// SOURCE: `db-manager/migrations/V109__add_solution_comment_permissions.sql`
```sql
-- 标准迁移格式
ALTER TABLE ... ADD COLUMN ...;
```

### ERROR_HANDLING
// SOURCE: `AdminNotificationServiceImpl.java`
```java
throw new BusinessException("通知不存在");
```

### LOGGING_PATTERN
// SOURCE: `AdminCommentServiceImpl.java`
```java
log.info("管理员删除评论, commentId: {}, operatorId: {}", commentId, operatorId);
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `db-manager/migrations/V110__add_announcement_id_to_notifications.sql` | CREATE | 添加 announcement_id 列 |
| `backend-spring/.../notification/entity/Notification.java` | UPDATE | 添加 announcementId 字段 |
| `backend-spring/.../admin/dto/AdminNotificationQueryDTO.java` | CREATE | 分页查询 DTO |
| `backend-spring/.../admin/dto/CreateSystemNotificationRequest.java` | UPDATE | 添加 announcementId |
| `backend-spring/.../admin/dto/AdminNotificationVO.java` | UPDATE | 添加 announcementId 字段 |
| `backend-spring/.../admin/controller/AdminNotificationController.java` | UPDATE | 分页端点 |
| `backend-spring/.../admin/service/AdminNotificationService.java` | UPDATE | 接口签名变更 |
| `backend-spring/.../admin/service/impl/AdminNotificationServiceImpl.java` | UPDATE | 重构去重/级联 + 分页 + N+1 |
| `backend-spring/.../notification/mapper/NotificationMapper.java` | UPDATE | 添加批量查询方法 |
| `management/src/lib/entities/notification.ts` | UPDATE | 删除幽灵类型，统一枚举 |
| `management/src/api/admin/notifications.ts` | UPDATE | 分页 API + 类型对齐 |
| `management/src/stores/admin/notifications.ts` | UPDATE | 分页 + delete 刷新 |
| `management/src/views/notifications/NotificationsListView.vue` | UPDATE | 服务端分页 + Category 列 |
| `management/src/views/notifications/NotificationCreateDialog.vue` | UPDATE | 添加 category 选择 |
| `management/src/i18n/locales/zh-CN/modules/notifications.ts` | UPDATE | 对齐 categories 枚举 |
| `management/src/i18n/locales/en-US/modules/notifications.ts` | UPDATE | 对齐 categories 枚举 |
| `management/src/views/notifications/columns.ts` | CREATE (if needed) | 抽取列定义 |

## NOT Building
- 通知发送状态/送达率追踪系统
- 批量操作（批量删除/标记）
- 通知详情展开面板
- WebSocket NotificationType 大小写统一（需同步修改 console 前端，范围过大，另立任务）
- 用户端 NotificationController 的变更（非本次范围）

---

## Step-by-Step Tasks

---

### TASK-001: [DB] 添加 announcement_id 列到 notifications 表

- **ACTION**: 创建 Flyway 迁移脚本，为 notifications 表添加 announcement_id 列
- **IMPLEMENT**:
  ```sql
  -- V110__add_announcement_id_to_notifications.sql
  ALTER TABLE notifications ADD COLUMN announcement_id VARCHAR(64) DEFAULT NULL AFTER id;
  CREATE INDEX idx_notifications_announcement_id ON notifications(announcement_id);
  -- 为现有系统通知回填 announcement_id (使用 title+type+created_at 分组)
  UPDATE notifications n
  INNER JOIN (
    SELECT MIN(id) as min_id, MD5(CONCAT(title, type, DATE(created_at))) as aid
    FROM notifications WHERE category = 'SYSTEM'
    GROUP BY title, type, DATE(created_at)
  ) grp ON n.id = grp.min_id
  SET n.announcement_id = grp.aid;
  -- 将同组其他记录也更新为相同的 announcement_id
  UPDATE notifications n
  INNER JOIN notifications ref ON ref.announcement_id IS NOT NULL
    AND n.title = ref.title AND n.type = ref.type AND n.category = 'SYSTEM'
    AND DATE(n.created_at) = DATE(ref.created_at)
  SET n.announcement_id = ref.announcement_id
  WHERE n.announcement_id IS NULL AND n.category = 'SYSTEM';
  ```
- **MIRROR**: `db-manager/migrations/V109__add_solution_comment_permissions.sql`
- **IMPORTS**: 无
- **GOTCHA**: 迁移编号必须为 V110（当前最大为 V109）。回填 SQL 需处理 NULL announcement_id 的情况。VARCHAR(64) 足够存 MD5 hash。
- **VALIDATE**: 运行 `db-manager migrate --dry-run` 确认迁移可执行；运行 `db-manager migrate` 确认实际执行成功；查询确认 announcement_id 列存在且有索引。

---

### TASK-002: [Backend Entity] Notification 实体添加 announcementId 字段

- **ACTION**: 在 Notification 实体类中添加 announcementId 属性
- **IMPLEMENT**: 在 `Notification.java` 的 id 字段后添加:
  ```java
  @TableField("announcement_id")
  private String announcementId;
  ```
- **MIRROR**: `Notification.java` 中其他 `@TableField` 注解的使用方式
- **IMPORTS**: `com.baomidou.mybatisplus.annotation.TableField`
- **GOTCHA**: 使用 `@TableField` 显式映射列名，保持与数据库列名一致（snake_case → camelCase）
- **VALIDATE**: `./mvnw compile` 编译通过

---

### TASK-003: [Backend DTO] 创建 AdminNotificationQueryDTO

- **ACTION**: 创建管理端通知查询 DTO，支持分页、搜索、类型和分类过滤
- **IMPLEMENT**:
  ```java
  @Data
  public class AdminNotificationQueryDTO {
      private Integer page = 1;
      private Integer limit = 20;
      private String keyword;
      private String type;
      private String category;
      private String sortBy = "createdAt";
      private String sortOrder = "desc";
  }
  ```
- **MIRROR**: `AdminCommentQueryDTO.java` — 完全对齐字段命名和默认值
- **IMPORTS**: `lombok.Data`
- **GOTCHA**: sortBy/sortOrder 字段用于服务端排序，需在 Service 层做白名单校验防止 SQL 注入
- **VALIDATE**: `./mvnw compile` 编译通过；字段命名与 AdminCommentQueryDTO 一致

---

### TASK-004: [Backend DTO] 更新 CreateSystemNotificationRequest 和 AdminNotificationVO

- **ACTION**: 在创建请求 DTO 和 Admin VO 中添加 announcementId/category 相关字段
- **IMPLEMENT**:
  - `CreateSystemNotificationRequest.java`: 无需添加 announcementId（由后端自动生成）
  - `AdminNotificationVO.java`: 添加 `private String announcementId;` 和确保 `category` 字段存在
  - `UpdateSystemNotificationRequest.java`: 确认已有 category 字段（无需修改）
- **MIRROR**: `AdminNotificationVO.java` 中其他字段定义风格
- **IMPORTS**: 无新增
- **GOTCHA**: announcementId 仅在 VO 中返回，不在 Create/Update 请求中传入
- **VALIDATE**: `./mvnw compile` 编译通过

---

### TASK-005: [Backend Service] 重构 AdminNotificationServiceImpl — 分页查询 + announcementId 去重 + Creator 批量查询

- **ACTION**: 重写 AdminNotificationServiceImpl 核心方法，实现三大改进
- **IMPLEMENT**:

  **1) getAllSystemNotifications → listSystemNotifications (分页)**:
  ```java
  @Override
  public PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQueryDTO queryDTO) {
      Page<Notification> page = new Page<>(queryDTO.getPage(), queryDTO.getLimit());

      QueryWrapper<Notification> qw = new QueryWrapper<>();
      qw.eq("category", "SYSTEM");
      if (StringUtils.hasText(queryDTO.getKeyword())) {
          qw.and(w -> w.like("title", queryDTO.getKeyword())
                      .or().like("body", queryDTO.getKeyword()));
      }
      if (StringUtils.hasText(queryDTO.getType())) {
          qw.eq("type", queryDTO.getType());
      }
      // sortBy 白名单校验
      Set<String> allowedSorts = Set.of("createdAt", "title", "type");
      String sortCol = allowedSorts.contains(queryDTO.getSortBy()) ? queryDTO.getSortBy() : "created_at";
      qw.orderBy(true, "asc".equalsIgnoreCase(queryDTO.getSortOrder()), sortCol);

      IPage<Notification> result = notificationMapper.selectPage(page, qw);
      List<AdminNotificationVO> vos = toAdminVOList(result.getRecords());
      return PageResult.of(vos, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
  }
  ```

  **2) 去重改为基于 announcementId**:
  ```java
  // getAllSystemNotifications 去重逻辑
  List<Notification> all = notificationMapper.selectList(qw);
  Map<String, Notification> deduped = new LinkedHashMap<>();
  for (Notification n : all) {
      String key = n.getAnnouncementId() != null
          ? n.getAnnouncementId()
          : n.getTitle() + "|" + n.getType() + "|" + n.getCreatedAt();
      deduped.putIfAbsent(key, n);
  }
  ```

  **3) Creator 批量查询替代 N+1**:
  ```java
  private List<AdminNotificationVO> toAdminVOList(List<Notification> notifications) {
      if (notifications.isEmpty()) return Collections.emptyList();

      // 批量获取 creator 信息
      Set<String> creatorIds = notifications.stream()
          .map(n -> n.getMetadata() != null ? n.getMetadata().getCreatedBy() : null)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());

      Map<String, User> userMap = creatorIds.isEmpty() ? Collections.emptyMap()
          : userMapper.selectBatchIds(creatorIds).stream()
              .collect(Collectors.toMap(User::getId, Function.identity()));

      return notifications.stream()
          .map(n -> toAdminVO(n, userMap.get(n.getMetadata() != null ? n.getMetadata().getCreatedBy() : null)))
          .collect(Collectors.toList());
  }
  ```

  **4) createSystemNotification 生成 announcementId**:
  ```java
  String announcementId = UUID.randomUUID().toString().replace("-", "");
  // 创建所有用户通知时设置相同的 announcementId
  notification.setAnnouncementId(announcementId);
  ```

  **5) deleteNotification / updateSystemNotification 级联基于 announcementId**:
  ```java
  // 删除
  notificationMapper.delete(new QueryWrapper<Notification>()
      .eq("announcement_id", notification.getAnnouncementId()));
  // 更新
  notificationMapper.update(updateEntity, new QueryWrapper<Notification>()
      .eq("announcement_id", notification.getAnnouncementId()));
  ```

- **MIRROR**: `AdminCommentServiceImpl.java` 的分页查询模式
- **IMPORTS**: `com.baomidou.mybatisplus.core.metadata.IPage`, `com.baomidou.mybatisplus.extension.plugins.pagination.Page`
- **GOTCHA**: sortBy 必须做白名单校验防止 SQL 注入；旧数据 announcementId 为 NULL 时降级到 title+type 去重；批量查询用 `selectBatchIds` 而非循环
- **VALIDATE**: `./mvnw compile` 编译通过；单元测试验证分页、过滤、去重逻辑

---

### TASK-006: [Backend Controller] 更新 AdminNotificationController 分页端点

- **ACTION**: 将 `list` 方法从返回 `List<AdminNotificationVO>` 改为返回 `PageResult<AdminNotificationVO>`
- **IMPLEMENT**:
  ```java
  @GetMapping
  public Result<PageResult<AdminNotificationVO>> list(AdminNotificationQueryDTO queryDTO) {
      PageResult<AdminNotificationVO> result = adminNotificationService.listSystemNotifications(queryDTO);
      return Result.success(result);
  }
  ```
- **MIRROR**: `AdminCommentController.java` 的 `list` 方法签名
- **IMPORTS**: `com.ulticode.common.dto.PageResult`
- **GOTCHA**: 接口签名变更是 breaking change，需同步更新前端调用
- **VALIDATE**: `./mvnw compile` 编译通过；Swagger UI 验证新端点参数

---

### TASK-007: [Backend Service Interface] 更新 AdminNotificationService 接口签名

- **ACTION**: 更新接口方法签名以匹配实现变更
- **IMPLEMENT**:
  ```java
  // 旧: List<AdminNotificationVO> getAllSystemNotifications();
  // 新:
  PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQueryDTO queryDTO);
  ```
- **MIRROR**: `AdminCommentService.java` 接口定义风格
- **IMPORTS**: `com.ulticode.common.dto.PageResult`, `AdminNotificationQueryDTO`
- **GOTCHA**: 删除旧方法签名，添加新方法签名，确保 ServiceImpl 同步更新
- **VALIDATE**: `./mvnw compile` 编译通过

---

### TASK-008: [Frontend API] 重构 notifications API 模块 — 分页 + 类型对齐

- **ACTION**: 重写 `management/src/api/admin/notifications.ts`，支持分页参数、对齐枚举、修复 delete 返回类型
- **IMPLEMENT**:
  ```typescript
  import type { PageResult } from '@/lib/types'
  import { apiGet, apiPost, apiPut, apiDelete } from '@/lib/api'

  // 统一枚举定义（与后端 NotificationType / NotificationCategory 完全对齐）
  export type NotificationType =
    | 'COMMENT' | 'REPLY' | 'MENTION' | 'UPVOTE' | 'FOLLOW'
    | 'SYSTEM' | 'SUBMISSION' | 'CONTEST' | 'CONTEST_REMINDER'

  export type NotificationCategory =
    | 'COMMUNICATION' | 'MARKETING' | 'SECURITY' | 'SYSTEM' | 'CONTEST'

  export type NotificationTarget = 'ALL' | 'USERS'

  export interface AdminNotificationQueryParams {
    page?: number
    limit?: number
    keyword?: string
    type?: NotificationType
    category?: NotificationCategory
    sortBy?: string
    sortOrder?: 'asc' | 'desc'
  }

  export interface SystemAnnouncement {
    id: string
    announcementId?: string
    title: string
    content: string
    type: NotificationType
    category: NotificationCategory
    createdAt: string
    creator: { id: string; username: string; avatar?: string }
  }

  export interface CreateNotificationDto {
    title: string
    content: string
    type: NotificationType
    category?: NotificationCategory
    target: NotificationTarget
    userIds?: string[]
  }

  export interface UpdateNotificationDto {
    title: string
    content: string
    type?: NotificationType
    category?: NotificationCategory
  }

  export const adminNotificationsApi = {
    getAll: (params?: AdminNotificationQueryParams) =>
      apiGet<PageResult<SystemAnnouncement>>('/admin/notifications', { params }),
    create: (data: CreateNotificationDto) =>
      apiPost<SystemAnnouncement>('/admin/notifications', data),
    update: (id: string, data: UpdateNotificationDto) =>
      apiPut<SystemAnnouncement>(`/admin/notifications/${id}`, data),
    delete: (id: string) =>
      apiDelete<void>(`/admin/notifications/${id}`),
  }
  ```
- **MIRROR**: `management/src/api/admin/comments.ts` 的分页 API 模式
- **IMPORTS**: `@/lib/types` (PageResult), `@/lib/api` (apiGet/apiPost/apiPut/apiDelete)
- **GOTCHA**: delete 返回类型改为 `void`（P4 修复）；枚举添加 `CONTEST_REMINDER`（P7 修复）；类型从 `string` 改为联合类型（P5 部分修复）
- **VALIDATE**: `pnpm type-check` 通过；枚举值与后端完全匹配

---

### TASK-009: [Frontend Store] 重构 notifications store — 分页 + delete 刷新

- **ACTION**: 重写 `management/src/stores/admin/notifications.ts`，支持分页状态和 delete 后刷新
- **IMPLEMENT**:
  ```typescript
  import { defineStore } from 'pinia'
  import { adminNotificationsApi, type AdminNotificationQueryParams, type SystemAnnouncement, type CreateNotificationDto, type UpdateNotificationDto } from '@/api/admin/notifications'
  import type { PageResult } from '@/lib/types'

  export const useAdminNotificationsStore = defineStore('admin-notifications', {
    state: () => ({
      notifications: [] as SystemAnnouncement[],
      total: 0,
      currentPage: 1,
      pageSize: 20,
      isLoading: false,
      error: null as string | null,
    }),
    actions: {
      async fetchNotifications(params?: AdminNotificationQueryParams) {
        this.isLoading = true
        this.error = null
        try {
          const queryParams: AdminNotificationQueryParams = {
            page: this.currentPage,
            limit: this.pageSize,
            ...params,
          }
          const result = await adminNotificationsApi.getAll(queryParams)
          this.notifications = result.items
          this.total = result.total
          if (params?.page) this.currentPage = params.page
          if (params?.limit) this.pageSize = params.limit
        } catch (e: any) {
          this.error = e.message || '获取通知列表失败'
        } finally {
          this.isLoading = false
        }
      },
      async createNotification(data: CreateNotificationDto) {
        await adminNotificationsApi.create(data)
        await this.fetchNotifications()
      },
      async updateNotification(id: string, data: UpdateNotificationDto) {
        await adminNotificationsApi.update(id, data)
        await this.fetchNotifications()
      },
      async deleteAnnouncement(id: string) {
        await adminNotificationsApi.delete(id)
        await this.fetchNotifications() // P8 修复：删除后刷新全列表
      },
    },
  })
  ```
- **MIRROR**: `management/src/stores/admin/comments.ts` 的分页 store 模式
- **IMPORTS**: `pinia`, API 模块类型
- **GOTCHA**: `deleteAnnouncement` 改为 `fetchNotifications()` 刷新而非本地 filter（P8 修复）；添加 `total`, `currentPage`, `pageSize` 状态字段
- **VALIDATE**: `pnpm type-check` 通过；store actions 签名与 API 层匹配

---

### TASK-010: [Frontend View] 重构 NotificationsListView — 服务端分页 + Category 列

- **ACTION**: 重写 NotificationsListView，使用 DataTable 分页模式，添加服务端过滤和 Category 列
- **IMPLEMENT**:
  - 移除客户端 `filteredData` computed，改用 store 的 `fetchNotifications(params)` 传递搜索/过滤参数
  - 添加 DataTable 分页控件（`@update:page`, `@update:page-size`）
  - 表格列添加 `category` 列
  - 搜索和类型筛选改为调用 `fetchNotifications({ keyword, type })` 而非本地 filter
  - 统计信息从服务端 `total` 获取，移除客户端 count computed
  - 类型筛选下拉使用对齐后的 `NotificationType` 枚举
  - 添加 Category 筛选下拉使用 `NotificationCategory` 枚举
- **MIRROR**: `management/src/views/comments/CommentsListView.vue` 的分页 DataTable 模式
- **IMPORTS**: store, API 类型, DataTable 组件
- **GOTCHA**: 分页跳转时需保持当前搜索/过滤条件；筛选变更时重置 page 为 1
- **VALIDATE**: `pnpm type-check` 通过；浏览器验证分页、搜索、类型筛选、Category 筛选均正常工作

---

### TASK-011: [Frontend View] 更新 NotificationCreateDialog — 添加 Category 选择

- **ACTION**: 在创建/编辑对话框中添加 Category 下拉选择
- **IMPLEMENT**:
  - 添加 `category` 字段到表单，默认值 `'SYSTEM'`
  - 使用 `NotificationCategory` 枚举值作为选项
  - 表单提交时包含 category 字段
- **MIRROR**: 对话框中 type 下拉选择的实现方式
- **IMPORTS**: `NotificationCategory` from API 模块
- **GOTCHA**: 编辑模式下 category 应预填当前值
- **VALIDATE**: `pnpm type-check` 通过；创建通知时可选择 category 并成功提交

---

### TASK-012: [Frontend i18n] 对齐 notifications 模块国际化键

- **ACTION**: 修复中英文 i18n 文件中 categories 键，使其与后端枚举完全对齐
- **IMPLEMENT**:
  - `zh-CN/modules/notifications.ts` — categories 键修改为:
    ```typescript
    categories: {
      COMMUNICATION: '沟通通知',
      MARKETING: '营销通知',
      SECURITY: '安全通知',
      SYSTEM: '系统通知',
      CONTEST: '竞赛通知',
    },
    ```
  - `en-US/modules/notifications.ts` — categories 键修改为:
    ```typescript
    categories: {
      COMMUNICATION: 'Communication',
      MARKETING: 'Marketing',
      SECURITY: 'Security',
      SYSTEM: 'System',
      CONTEST: 'Contest',
    },
    ```
  - types 键添加缺失的 `CONTEST_REMINDER`:
    ```typescript
    types: {
      // ... existing
      CONTEST_REMINDER: '竞赛提醒',
    },
    ```
- **MIRROR**: `management/src/i18n/locales/zh-CN/modules/comments.ts` 的枚举键组织方式
- **IMPORTS**: 无
- **GOTCHA**: 删除旧的 ANNOUNCEMENT, PROMOTION, UPDATE, WARNING 键；添加 COMMUNICATION, MARKETING, SECURITY, CONTEST 键
- **VALIDATE**: `pnpm type-check` 通过；浏览器确认 Category 列显示中文/英文标签

---

### TASK-013: [Frontend Cleanup] 删除或重构 lib/entities/notification.ts 幽灵类型

- **ACTION**: 清理 `management/src/lib/entities/notification.ts` 中的幽灵类型定义
- **IMPLEMENT**:
  - 删除 `NotificationType` 和 `NotificationPriority` 类型定义
  - 检查是否有其他文件引用此文件的导出，如有则更新导入路径到 `@/api/admin/notifications`
  - 如果文件中仍有其他有效类型则保留，否则删除整个文件
- **MIRROR**: 无（清理操作）
- **IMPORTS**: 无
- **GOTCHA**: 必须先 grep 检查所有引用此文件的导入，避免删除后编译失败
- **VALIDATE**: `pnpm type-check` 通过；确认无残留导入错误

---

### TASK-014: [Integration] 端到端验证与回归测试

- **ACTION**: 完成所有修改后，进行完整的端到端验证
- **IMPLEMENT**:
  - 启动后端服务 (`pm2 restart ulticode-9001`)
  - 启动前端服务 (`pm2 restart ulticode-9003`)
  - 在浏览器中验证:
    1. 通知列表页面加载正常，显示分页控件
    2. 分页跳转正常，每页显示正确数量的记录
    3. 搜索框输入关键词后服务端过滤正常
    4. 类型下拉筛选正常
    5. Category 下拉筛选正常
    6. 创建通知对话框中 Category 选择正常
    7. 编辑通知后列表刷新
    8. 删除通知后列表刷新（验证级联删除场景）
    9. i18n 标签显示正确（中英文）
  - 检查浏览器控制台无错误
  - 检查后端日志无异常
- **MIRROR**: 无
- **IMPORTS**: 无
- **GOTCHA**: 需确保数据库迁移已执行且后端重启后新字段生效
- **VALIDATE**: 全部验证项通过

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `listSystemNotifications` 分页 | page=1, limit=20 | 返回 PageResult，total 正确 | 否 |
| `listSystemNotifications` 关键词过滤 | keyword="test" | 仅返回 title/body 包含 test 的通知 | 否 |
| `listSystemNotifications` 空结果 | keyword="nonexistent" | 返回空列表，total=0 | 是 |
| `listSystemNotifications` 排序白名单 | sortBy="createdAt" | 正常排序 | 否 |
| `listSystemNotifications` 排序注入 | sortBy="; DROP TABLE" | 降级为默认排序 | 是 |
| `createSystemNotification` announcementId 生成 | 正常创建请求 | 所有用户副本共享 announcementId | 否 |
| `deleteNotification` announcementId 级联 | 删除有 announcementId 的通知 | 同 announcementId 的所有记录被删除 | 是 |
| `deleteNotification` 无 announcementId 降级 | 删除旧数据（announcementId=null） | 仅删除单条 | 是 |
| Creator 批量查询 | 多条通知，多个 creator | 一次批量查询，非 N+1 | 否 |

### Edge Cases Checklist
- [ ] 空列表分页（page=1, limit=20, 无数据）
- [ ] 极大页码（page=9999）
- [ ] 无效 sortBy 值（SQL 注入防护）
- [ ] 旧数据 announcementId 为 NULL 的降级去重
- [ ] 同一 announcementId 下存在大量用户副本的级联删除
- [ ] 并发创建通知时 announcementId 的唯一性
- [ ] 搜索关键词包含 SQL 特殊字符
- [ ] category 过滤器选择 "CONTEST" 时无 CONTEST 类型通知

---

## Validation Commands

### Static Analysis
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw compile -DskipTests
```
EXPECT: Zero compilation errors

### Backend Unit Tests
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw test
```
EXPECT: All tests pass

### Frontend Type Check
```bash
cd /home/david/project/UltiCode-Public-Next/management && pnpm type-check
```
EXPECT: Zero type errors

### Frontend Lint
```bash
cd /home/david/project/UltiCode-Public-Next/management && pnpm lint
```
EXPECT: Zero lint errors

### Database Migration
```bash
cd /home/david/project/UltiCode-Public-Next/db-manager && source .venv/bin/activate && db-manager migrate --dry-run
```
EXPECT: Migration V110 previewed successfully

```bash
db-manager migrate
```
EXPECT: Migration applied successfully

### Browser Validation
```bash
pm2 restart ulticode-9001 && pm2 restart ulticode-9003
```
EXPECT: 通知管理页面分页、搜索、过滤、CRUD 操作均正常

### Manual Validation
- [ ] 通知列表分页正常
- [ ] 搜索功能服务端过滤正常
- [ ] Type 筛选下拉包含 CONTEST_REMINDER
- [ ] Category 筛选下拉显示后端枚举值
- [ ] 创建通知对话框包含 Category 选择
- [ ] 删除通知后列表刷新
- [ ] 编辑通知后列表刷新
- [ ] 中英文 i18n 标签正确

---

## Acceptance Criteria
- [ ] Admin 通知列表支持分页查询（page/limit 参数）
- [ ] Admin 通知列表支持服务端搜索（keyword）和过滤（type/category）
- [ ] 去重逻辑基于 announcementId（降级到 title+type）
- [ ] 删除/更新级联基于 announcementId
- [ ] delete 返回类型为 void
- [ ] lib/entities/notification.ts 幽灵类型已清理
- [ ] i18n categories 键与后端枚举完全对齐
- [ ] NotificationType 枚举包含 CONTEST_REMINDER
- [ ] Store delete 后刷新全列表
- [ ] Creator 信息批量查询（无 N+1）
- [ ] 所有编译/类型检查/测试通过

## Completion Checklist
- [ ] 代码遵循 AdminCommentController 的分页模式
- [ ] 错误处理使用 BusinessException
- [ ] 日志记录关键操作
- [ ] 无硬编码值
- [ ] Flyway 迁移向后兼容
- [ ] 前后端枚举值完全匹配
- [ ] i18n 键无缺失
- [ ] 无不必要的 scope 扩展

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| V110 迁移回填 SQL 在大数据量下执行缓慢 | 中 | 中 | 分批执行，添加 LIMIT 分段更新 |
| announcementId 为 NULL 的旧数据降级去重可能产生误合并 | 低 | 中 | 降级逻辑仅用于展示去重，级联操作仍需二次确认 |
| 前端分页重构引入 UI 回归 | 中 | 低 | 逐步替换，保留客户端过滤作为 fallback |
| 类型定义变更导致 console 前端受影响 | 低 | 高 | 仅修改 management 前端，console 不受影响 |

## Notes
- WebSocket NotificationType 大小写统一（P10）不在本次范围，需同步修改 console 前端和 WebSocket 消息格式，另立任务
- 用户端 NotificationController 不在本次范围，其分页和类型已独立实现
- 迁移编号 V110 基于当前最大编号 V109，实施前需确认无新增迁移
