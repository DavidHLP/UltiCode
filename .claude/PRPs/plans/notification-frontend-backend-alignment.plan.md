# Plan: 通知模块前后端颗粒度对齐

## Summary
修复通知模块前后端之间 6 个颗粒度不对齐问题：查询参数命名不匹配、枚举大小写不一致、分类缺失、WebSocket payload 字段名差异、偏好设置 UI 缺失、管理端编辑功能缺失。涉及后端 3 个文件修改、Console 前端 5 个文件修改、Management 前端 4 个文件修改、新增 2 个文件。

## User Story
As a 用户/管理员, I want 通知模块前后端数据契约完全一致, so that 通知功能在所有场景下正确工作，且偏好设置可配置。

## Problem → Solution
**Current**: 前端查询参数 `unreadOnly` 后端不识别（被忽略）；Console 枚举小写与后端大写不匹配；CONTEST 分类前端缺失；偏好设置 API 已就绪但无 UI；管理端无编辑能力。
**Desired**: 前后端数据契约 1:1 对齐；偏好设置可配置；管理端支持编辑系统公告。

## Metadata
- **Complexity**: Large
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 14 (3 backend, 7 console, 4 management, 2 new)

---

## UX Design

### Before
```
┌─ Console 通知页面 ──────────────────────┐
│  [通知列表]  [筛选: 类型/分类]           │
│  ⚠ unreadOnly 筛选实际不生效             │
│  ⚠ 枚举比对可能失败                      │
│  ⚠ 无偏好设置入口                        │
│  ⚠ CONTEST 分类筛选缺失                  │
└──────────────────────────────────────────┘

┌─ Management 通知页面 ───────────────────┐
│  [公告列表]  [创建]                      │
│  ⚠ 无编辑功能，只能删除重建              │
│  ⚠ CONTEST 分类选项缺失                  │
└──────────────────────────────────────────┘
```

### After
```
┌─ Console 通知页面 ──────────────────────┐
│  [通知列表]  [筛选: 类型/分类/已读状态]   │
│  ✓ isRead 筛选正确工作                    │
│  ✓ 枚举大写统一                          │
│  [偏好设置] 按钮 → 弹出设置面板           │
│  ✓ CONTEST 分类可选                       │
└──────────────────────────────────────────┘

┌─ Management 通知页面 ───────────────────┐
│  [公告列表]  [创建]  [编辑]              │
│  ✓ 编辑对话框可修改已有公告               │
│  ✓ CONTEST 分类选项可用                   │
└──────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Console 通知筛选 | `unreadOnly` 参数被后端忽略 | `isRead` 参数正确过滤 | 修复 Bug |
| Console 枚举比对 | 小写 vs 大写不匹配 | 统一大写枚举 | 防止运行时错误 |
| Console 偏好设置 | 无入口 | 页面内偏好设置面板 | 新功能 |
| Management 分类选择 | 缺 CONTEST | 完整 5 个分类 | 补齐 |
| Management 公告编辑 | 只能删除重建 | 编辑对话框 | 新功能 |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `console/src/api/notification.ts` | all | 修复查询参数和 API 层 |
| P0 | `console/src/types/notification.ts` | all | 修复枚举类型定义 |
| P0 | `console/src/stores/notification.ts` | all | 修复 store 中的枚举引用和偏好设置集成 |
| P0 | `console/src/views/personal/NotificationsView.vue` | all | 添加偏好设置 UI |
| P1 | `management/src/api/admin/notifications.ts` | all | 补充 CONTEST 分类和编辑 API |
| P1 | `management/src/stores/admin/notifications.ts` | all | 添加 updateNotification action |
| P1 | `management/src/views/notifications/NotificationsListView.vue` | all | 添加编辑入口 |
| P1 | `management/src/views/notifications/NotificationCreateDialog.vue` | all | 改造为创建/编辑双模对话框 |
| P2 | `backend-spring/.../notification/controller/NotificationController.java` | all | 确认后端参数 |
| P2 | `backend-spring/.../admin/controller/AdminNotificationController.java` | all | 添加 PUT 端点 |
| P2 | `backend-spring/.../admin/service/AdminNotificationService.java` | all | 添加 update 方法签名 |
| P2 | `console/src/lib/socket.ts` | all | 确认 WebSocket payload 字段 |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| N/A | N/A | 无外部依赖，全部使用项目内部模式 |

---

## Patterns to Mirror

### API_REQUEST_PATTERN (Console)
// SOURCE: console/src/api/notification.ts
```typescript
import { apiGet, apiPatch, apiPost, apiDelete } from "@/utils/request";

export async function fetchNotifications(
  params?: NotificationQuery,
): Promise<NotificationListResult> {
  return apiGet<NotificationListResult>(`/notifications${buildQuery(params)}`);
}
```

### API_REQUEST_PATTERN (Management)
// SOURCE: management/src/api/admin/notifications.ts
```typescript
import { apiGet, apiPost, apiDelete } from '@/utils/request'

export const adminNotifications = {
  create: (data: CreateNotificationDto) => apiPost<SystemAnnouncement>('/admin/notifications', data),
  getAll: () => apiGet<SystemAnnouncement[]>('/admin/notifications'),
  delete: (id: string) => apiDelete<{ message: string }>(`/admin/notifications/${id}`),
}
```

### ENUM_PATTERN (Management — 正确示范)
// SOURCE: management/src/api/admin/notifications.ts
```typescript
export enum NotificationType {
  COMMENT = 'COMMENT',
  REPLY = 'REPLY',
  MENTION = 'MENTION',
  UPVOTE = 'UPVOTE',
  FOLLOW = 'FOLLOW',
  SYSTEM = 'SYSTEM',
  SUBMISSION = 'SUBMISSION',
  CONTEST = 'CONTEST',
}

export enum NotificationCategory {
  COMMUNICATION = 'COMMUNICATION',
  MARKETING = 'MARKETING',
  SECURITY = 'SECURITY',
  SYSTEM = 'SYSTEM',
  // 需要补充: CONTEST = 'CONTEST'
}
```

### STORE_PATTERN (Console)
// SOURCE: console/src/stores/notification.ts
```typescript
import { defineStore } from "pinia";
import { ref } from "vue";

export const useNotificationStore = defineStore("notification", () => {
  const loading = ref(false);
  // ... actions
  return { loading, /* ... */ };
});
```

### STORE_PATTERN (Management)
// SOURCE: management/src/stores/admin/notifications.ts
```typescript
export const useNotificationsStore = defineStore('admin-notifications', () => {
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function createNotification(data: CreateNotificationDto) {
    isLoading.value = true;
    try {
      await adminNotifications.create(data);
      await fetchAnnouncements();
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      error.value = err.response?.data?.message || 'Failed to create notification';
      throw e;
    } finally {
      isLoading.value = false;
    }
  }
  // ...
});
```

### DIALOG_EDIT_PATTERN (Management)
// SOURCE: management/src/views/tags/TagEditDialog.vue (参照)
```typescript
const props = defineProps<{
  open: boolean
  tagToEdit: Tag | null  // null = create, Tag = edit
}>()

// In submit:
if (props.tagToEdit) {
  await tagsStore.updateTag(props.tagToEdit.id, payload)
  toast.success(t('tags.toast.updatedSuccessfully'))
} else {
  await tagsStore.createTag(payload)
  toast.success(t('tags.toast.createdSuccessfully'))
}
```

### BACKEND_CONTROLLER_PATTERN
// SOURCE: backend-spring/.../admin/controller/AdminNotificationController.java
```java
@RateLimit(key = "admin:notification-create", limit = 30, period = 60)
@PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<AdminNotificationVO> createNotification(@Valid @RequestBody CreateSystemNotificationRequest request) {
    return Result.success(adminNotificationService.createSystemNotification(request));
}
```

### BACKEND_SERVICE_PATTERN
// SOURCE: backend-spring/.../admin/service/impl/AdminNotificationServiceImpl.java
```java
@Override
@Transactional
@Audited(action = AuditActionUtil.CREATE_NOTIFICATION, entityType = AuditActionUtil.ENTITY_NOTIFICATION)
public AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request) {
    // ... implementation
}
```

### BACKEND_DTO_PATTERN
// SOURCE: backend-spring/.../admin/dto/CreateSystemNotificationRequest.java
```java
@Data
public class CreateSystemNotificationRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String content;
    // ...
}
```

### WEBSOCKET_PAYLOAD_PATTERN
// SOURCE: backend-spring/.../websocket/notification/dto/NotificationPayload.java
```java
public record NotificationPayload(
    String event,      // "notification"
    String id,
    String type,       // "mention", "reply", "system" (小写!)
    String title,
    String content,    // ← 注意：这里用 content，不是 body
    Map<String, Object> data,
    Instant createdAt,
    boolean read) {}
```

### CONSOLE_SOCKET_HANDLER
// SOURCE: console/src/stores/notification.ts
```typescript
function handleNewNotification(payload: NotificationPayload) {
  const newItem: NotificationItem = {
    id: payload.id,
    title: payload.title,
    body: payload.body,   // ← 前端用 body，后端推送用 content
    type: payload.type as NotificationItem["type"],
    category: "system",
    // ...
  };
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `console/src/types/notification.ts` | UPDATE | 修复枚举为大写，补充 CONTEST |
| `console/src/api/notification.ts` | UPDATE | 修复 `unreadOnly` → `isRead`，添加 `apiPut` 导入 |
| `console/src/stores/notification.ts` | UPDATE | 修复枚举引用，集成偏好设置，修复 WebSocket payload 映射 |
| `console/src/views/personal/NotificationsView.vue` | UPDATE | 添加偏好设置面板，修复枚举引用 |
| `console/src/components/notification/NotificationBadge.vue` | UPDATE | 修复枚举引用（如有） |
| `console/src/lib/socket.ts` | UPDATE | 修复 `NotificationPayload.body` → `content` |
| `management/src/api/admin/notifications.ts` | UPDATE | 补充 CONTEST 分类，添加 update API |
| `management/src/stores/admin/notifications.ts` | UPDATE | 添加 updateNotification action |
| `management/src/views/notifications/NotificationsListView.vue` | UPDATE | 添加编辑入口 |
| `management/src/views/notifications/NotificationCreateDialog.vue` | UPDATE | 改造为创建/编辑双模对话框，补充 CONTEST 分类 |
| `management/src/lib/entities/notification.ts` | UPDATE | 同步类型定义 |
| `backend-spring/.../admin/controller/AdminNotificationController.java` | UPDATE | 添加 PUT 端点 |
| `backend-spring/.../admin/service/AdminNotificationService.java` | UPDATE | 添加 update 方法签名 |
| `backend-spring/.../admin/service/impl/AdminNotificationServiceImpl.java` | UPDATE | 实现 update 方法 |
| `backend-spring/.../admin/dto/UpdateSystemNotificationRequest.java` | CREATE | 新增编辑请求 DTO |
| `console/src/components/notification/NotificationPreferencesPanel.vue` | CREATE | 新增偏好设置面板组件 |

## NOT Building

- 后端 PUT 端点的审计日志（复用 @Audited 注解即可，不单独设计）
- 管理端 WebSocket 实时推送（当前不需要）
- 通知偏好设置的 granular per-type 控制（后端只支持 4 大分类，不扩展）
- 前端国际化新 key 的全语言翻译（仅添加中文和英文）
- 数据库 migration（无 schema 变更）

---

## Step-by-Step Tasks

### Task 1: 修复 Console 枚举类型为大写
- **ACTION**: 将 `console/src/types/notification.ts` 中的枚举从小写改为大写
- **IMPLEMENT**:
  ```typescript
  // Before:
  export type NotificationCategory = "communication" | "marketing" | "security" | "system";
  export type NotificationType = "comment" | "reply" | "mention" | "upvote" | "follow" | "system" | "submission" | "contest";

  // After:
  export type NotificationCategory = "COMMUNICATION" | "MARKETING" | "SECURITY" | "SYSTEM" | "CONTEST";
  export type NotificationType = "COMMENT" | "REPLY" | "MENTION" | "UPVOTE" | "FOLLOW" | "SYSTEM" | "SUBMISSION" | "CONTEST" | "CONTEST_REMINDER";
  ```
- **MIRROR**: Management 端枚举模式 (`management/src/api/admin/notifications.ts`)
- **IMPORTS**: 无新增
- **GOTCHA**: 后端 `NotificationType` 有 `CONTEST_REMINDER`，需同步添加
- **VALIDATE**: `pnpm type-check` 通过

### Task 2: 修复 Console 查询参数 `unreadOnly` → `isRead`
- **ACTION**: 修改 `console/src/api/notification.ts` 中的 `buildQuery` 函数和 `NotificationQuery` 类型
- **IMPLEMENT**:
  ```typescript
  // NotificationQuery 修改:
  export interface NotificationQuery {
    page?: number;
    limit?: number;
    isRead?: boolean;      // 替换 unreadOnly
    category?: string;
    type?: string;
  }

  // buildQuery 修改:
  function buildQuery(params?: NotificationQuery): string {
    if (!params) return "";
    const query = new URLSearchParams();
    if (params.page) query.set("page", String(params.page));
    if (params.limit) query.set("limit", String(params.limit));
    if (params.isRead !== undefined) {
      query.set("isRead", String(params.isRead));
    }
    if (params.category) query.set("category", params.category);
    if (params.type) query.set("type", params.type);
    const value = query.toString();
    return value ? `?${value}` : "";
  }
  ```
- **MIRROR**: 后端 `NotificationQueryDTO.java` 字段名
- **IMPORTS**: 无新增
- **GOTCHA**: 前端调用方可能传 `unreadOnly: true` 表示"只要未读"，对应后端应为 `isRead: false`。需检查所有调用点并翻转逻辑。
- **VALIDATE**: `pnpm type-check` 通过；搜索 `unreadOnly` 确认零残留

### Task 3: 修复 Console WebSocket payload 字段名
- **ACTION**: 修改 `console/src/lib/socket.ts` 和 `console/src/stores/notification.ts` 中 WebSocket payload 映射
- **IMPLEMENT**:
  ```typescript
  // socket.ts — NotificationPayload 修改:
  export interface NotificationPayload {
    id: string;
    type: string;
    title: string;
    content: string;    // body → content，匹配后端 NotificationPayload record
    link?: string;
    createdAt: string;
  }

  // stores/notification.ts — handleNewNotification 修改:
  function handleNewNotification(payload: NotificationPayload) {
    const newItem: NotificationItem = {
      id: payload.id,
      title: payload.title,
      body: payload.content,    // 从 content 映射到 body
      type: payload.type as NotificationItem["type"],
      category: "SYSTEM",       // 修正为大写
      link: payload.link || null,
      isRead: false,
      readAt: null,
      createdAt: payload.createdAt,
    };
    // ...
  }
  ```
- **MIRROR**: 后端 `NotificationPayload.java` record 字段名
- **IMPORTS**: 无新增
- **GOTCHA**: 后端 WebSocket payload 的 `type` 字段使用小写（如 `"mention"`, `"system"`），而 REST API 返回大写。需在 `handleNewNotification` 中做 `.toUpperCase()` 转换
- **VALIDATE**: `pnpm type-check` 通过

### Task 4: 修复 Console 通知页面枚举引用
- **ACTION**: 修改 `NotificationsView.vue` 中的类型图标映射和筛选逻辑
- **IMPLEMENT**:
  ```typescript
  // 修改 typeIconMap 键名:
  const typeIconMap: Record<NotificationType, typeof Bell> = {
    COMMENT: MessageSquare,     // comment → COMMENT
    REPLY: CornerUpLeft,        // reply → REPLY
    MENTION: AtSign,            // mention → MENTION
    UPVOTE: ThumbsUp,           // upvote → UPVOTE
    FOLLOW: UserPlus,           // follow → FOLLOW
    SYSTEM: ShieldAlert,        // system → SYSTEM
    SUBMISSION: CheckCircle2,   // submission → SUBMISSION
    CONTEST: Trophy,            // contest → CONTEST
  };

  // 筛选选项中的值也改为大写
  ```
- **MIRROR**: Task 1 修改后的类型定义
- **IMPORTS**: 无新增
- **GOTCHA**: 确保所有使用 `NotificationType` 和 `NotificationCategory` 的地方都更新
- **VALIDATE**: `pnpm type-check` 通过

### Task 5: 修复 Console NotificationBadge 枚举引用
- **ACTION**: 检查并修复 `NotificationBadge.vue` 中可能存在的枚举引用
- **IMPLEMENT**: 搜索组件中小写枚举值，替换为大写
- **MIRROR**: Task 1 修改后的类型定义
- **IMPORTS**: 无新增
- **GOTCHA**: NotificationBadge 使用 `NotificationItem` 类型，类型已随 Task 1 更新，此处只需确认运行时值正确
- **VALIDATE**: `pnpm type-check` 通过

### Task 6: 创建 Console 通知偏好设置面板
- **ACTION**: 新建 `console/src/components/notification/NotificationPreferencesPanel.vue`
- **IMPLEMENT**: 偏好设置面板组件，包含 4 个开关（communication, marketing, security, system）
  ```vue
  <script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { toast } from 'vue-sonner'
  import { Switch } from '@/components/ui/switch'
  import { Label } from '@/components/ui/label'
  import {
    fetchNotificationPreferences,
    updateNotificationPreferences,
  } from '@/api/notification'
  import type { NotificationPreferences } from '@/types/notification'

  const { t } = useI18n()
  const loading = ref(false)
  const preferences = ref<NotificationPreferences>({
    communication: true,
    marketing: true,
    security: true,
    system: true,
  })

  onMounted(async () => {
    loading.value = true
    try {
      preferences.value = await fetchNotificationPreferences()
    } finally {
      loading.value = false
    }
  })

  async function togglePreference(key: keyof NotificationPreferences, value: boolean) {
    preferences.value[key] = value
    try {
      await updateNotificationPreferences({ [key]: value })
      toast.success(t('notifications.preferences.saved'))
    } catch {
      preferences.value[key] = !value  // 回滚
      toast.error(t('notifications.preferences.error'))
    }
  }
  </script>
  ```
- **MIRROR**: Console 现有 UI 组件模式 (Switch, Label, toast)
- **IMPORTS**: `@/api/notification`, `@/types/notification`, `vue-sonner`, UI 组件
- **GOTCHA**: 偏好设置 API 已在 `api/notification.ts` 中定义但未使用，直接调用即可
- **VALIDATE**: `pnpm type-check` 通过；在 NotificationsView 中集成后手动验证

### Task 7: 集成偏好设置到 Console 通知页面
- **ACTION**: 在 `NotificationsView.vue` 中添加偏好设置入口和面板
- **IMPLEMENT**: 在页面顶部操作栏添加"偏好设置"按钮，点击弹出偏好设置面板（使用 Popover 或 Sheet 组件）
  ```vue
  <!-- 在操作栏中添加 -->
  <Button variant="outline" size="sm" @click="prefPanelOpen = true">
    <Settings class="mr-2 h-4 w-4" />
    {{ t('notifications.preferences.title') }}
  </Button>

  <!-- 面板 -->
  <Sheet v-model:open="prefPanelOpen">
    <SheetContent>
      <SheetHeader>
        <SheetTitle>{{ t('notifications.preferences.title') }}</SheetTitle>
      </SheetHeader>
      <NotificationPreferencesPanel />
    </SheetContent>
  </Sheet>
  ```
- **MIRROR**: Console 现有 Sheet/Popover 使用模式
- **IMPORTS**: `Sheet`, `SheetContent`, `SheetHeader`, `SheetTitle`, `Settings` icon, `NotificationPreferencesPanel`
- **GOTCHA**: 确认 Console 的 UI 组件库中有 Sheet 组件
- **VALIDATE**: 页面渲染正确，偏好设置开关可切换并持久化

### Task 8: 补充 Management 端 CONTEST 分类
- **ACTION**: 在 `management/src/api/admin/notifications.ts` 的 `NotificationCategory` 枚举中添加 `CONTEST`
- **IMPLEMENT**:
  ```typescript
  export enum NotificationCategory {
    COMMUNICATION = 'COMMUNICATION',
    MARKETING = 'MARKETING',
    SECURITY = 'SECURITY',
    SYSTEM = 'SYSTEM',
    CONTEST = 'CONTEST',       // 新增
  }
  ```
- **MIRROR**: 后端 `NotificationCategory.java` 枚举
- **IMPORTS**: 无新增
- **GOTCHA**: 同步更新 `management/src/lib/entities/notification.ts` 中的相关类型（如有）
- **VALIDATE**: `pnpm type-check` 通过；创建对话框中分类下拉出现 CONTEST 选项

### Task 9: 后端添加管理端编辑 API
- **ACTION**: 添加 `PUT /admin/notifications/{id}` 端点
- **IMPLEMENT**:

  1. 创建 `UpdateSystemNotificationRequest.java`:
  ```java
  package com.ulticode.modules.admin.dto;

  import jakarta.validation.constraints.NotBlank;
  import lombok.Data;

  @Data
  public class UpdateSystemNotificationRequest {
      @NotBlank
      private String title;
      @NotBlank
      private String content;
      private String type;
      private String category;
  }
  ```

  2. 在 `AdminNotificationService.java` 接口添加:
  ```java
  AdminNotificationVO updateSystemNotification(String id, UpdateSystemNotificationRequest request);
  ```

  3. 在 `AdminNotificationServiceImpl.java` 实现:
  ```java
  @Override
  @Transactional
  @Audited(action = AuditActionUtil.UPDATE_NOTIFICATION, entityType = AuditActionUtil.ENTITY_NOTIFICATION)
  public AdminNotificationVO updateSystemNotification(String id, UpdateSystemNotificationRequest request) {
      Notification notification = notificationMapper.selectById(id);
      if (notification == null) {
          throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found");
      }

      // 更新所有同批次通知（同 title+type+category+createdAt）
      LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(Notification::getTitle, notification.getTitle());
      wrapper.eq(Notification::getType, notification.getType());
      wrapper.eq(Notification::getCategory, "SYSTEM");
      if (notification.getCreatedAt() != null) {
          wrapper.eq(Notification::getCreatedAt, notification.getCreatedAt());
      }

      List<Notification> batch = notificationMapper.selectList(wrapper);

      for (Notification n : batch) {
          n.setTitle(request.getTitle());
          n.setBody(request.getContent());
          if (request.getType() != null) n.setType(request.getType());
          if (request.getCategory() != null) n.setCategory(request.getCategory());
          notificationMapper.updateById(n);
      }

      notification.setTitle(request.getTitle());
      notification.setBody(request.getContent());
      if (request.getType() != null) notification.setType(request.getType());
      if (request.getCategory() != null) notification.setCategory(request.getCategory());

      return toAdminVO(notification);
  }
  ```

  4. 在 `AdminNotificationController.java` 添加:
  ```java
  @Operation(summary = "Update system notification", description = "Update a system announcement")
  @RateLimit(key = "admin:notification-update", limit = 30, period = 60)
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  public Result<AdminNotificationVO> updateNotification(
          @PathVariable String id,
          @Valid @RequestBody UpdateSystemNotificationRequest request) {
      return Result.success(adminNotificationService.updateSystemNotification(id, request));
  }
  ```

  5. 检查 `AuditActionUtil.java` 是否有 `UPDATE_NOTIFICATION` 常量，若无则添加

- **MIRROR**: 后端 `createSystemNotification` 和 `deleteNotification` 的模式
- **IMPORTS**: `jakarta.validation.constraints.NotBlank`, `@PutMapping`, `@Valid`
- **GOTCHA**: 编辑系统公告时需更新所有用户副本（同批次），不是只更新一条记录；`AuditActionUtil` 可能缺少 `UPDATE_NOTIFICATION` 常量
- **VALIDATE**: `./mvnw compile` 通过；Swagger UI 中出现 PUT 端点

### Task 10: Management 前端添加编辑 API 和 Store action
- **ACTION**: 在 `management/src/api/admin/notifications.ts` 添加 `update` 方法，在 store 中添加 `updateNotification` action
- **IMPLEMENT**:
  ```typescript
  // api/admin/notifications.ts — 添加:
  export interface UpdateNotificationDto {
    title: string
    content: string
    type?: NotificationType
    category?: NotificationCategory
  }

  export const adminNotifications = {
    // ...existing methods...
    update: (id: string, data: UpdateNotificationDto) =>
      apiPut<SystemAnnouncement>(`/admin/notifications/${id}`, data),
  }

  // 需要导入 apiPut:
  import { apiGet, apiPost, apiPut, apiDelete } from '@/utils/request'

  // stores/admin/notifications.ts — 添加:
  async function updateNotification(id: string, data: UpdateNotificationDto) {
    isLoading.value = true
    error.value = null
    try {
      await adminNotifications.update(id, data)
      await fetchAnnouncements()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      error.value = err.response?.data?.message || 'Failed to update notification'
      throw e
    } finally {
      isLoading.value = false
    }
  }
  ```
- **MIRROR**: 现有 `createNotification` 和 `deleteAnnouncement` action 模式
- **IMPORTS**: `apiPut`, `UpdateNotificationDto`
- **GOTCHA**: `apiPut` 需确认 `management/src/utils/request.ts` 中是否已导出
- **VALIDATE**: `pnpm type-check` 通过

### Task 11: 改造 Management 通知对话框为创建/编辑双模
- **ACTION**: 修改 `NotificationCreateDialog.vue`，增加编辑模式
- **IMPLEMENT**:
  ```typescript
  // 添加 props:
  const props = defineProps<{
    open: boolean
    notificationToEdit?: SystemAnnouncement | null  // null = 创建, 有值 = 编辑
  }>()

  // 修改 emit:
  const emit = defineEmits<{
    (e: 'update:open', value: boolean): void
    (e: 'success'): void
  }>()

  // 修改 watch — 编辑模式时填充表单:
  watch(() => props.open, (isOpen) => {
    if (isOpen) {
      if (props.notificationToEdit) {
        form.value = {
          title: props.notificationToEdit.title,
          content: props.notificationToEdit.content,
          type: props.notificationToEdit.type,
          category: NotificationCategory.SYSTEM,
          target: NotificationTarget.ALL,
          userIds: '',
        }
      } else {
        form.value = { ...defaultForm }
      }
      error.value = ''
    }
  })

  // 修改 submit — 区分创建和编辑:
  async function handleSubmit() {
    // ... validation ...
    if (props.notificationToEdit) {
      await store.updateNotification(props.notificationToEdit.id, {
        title: form.value.title,
        content: form.value.content,
        type: form.value.type,
        category: form.value.category,
      })
      toast.success(t('notifications.toast.updatedSuccessfully'))
    } else {
      await store.createNotification(payload)
      toast.success(t('notifications.toast.sentSuccessfully'))
    }
    emit('success')
    emit('update:open', false)
  }

  // 修改对话框标题:
  // {{ notificationToEdit ? t('notifications.edit.title') : t('notifications.create.title') }}
  ```
- **MIRROR**: `TagEditDialog.vue` 的创建/编辑双模模式
- **IMPORTS**: 无新增
- **GOTCHA**: 编辑模式下隐藏"目标用户"字段（已有公告不能修改接收者）；编辑时 category 使用默认值因为 `SystemAnnouncement` 接口不含 category
- **VALIDATE**: `pnpm type-check` 通过；创建和编辑流程均可用

### Task 12: Management 列表页添加编辑入口
- **ACTION**: 在 `NotificationsListView.vue` 中添加编辑操作
- **IMPLEMENT**:
  ```typescript
  // 添加状态:
  const editDialogOpen = ref(false)
  const notificationToEdit = ref<SystemAnnouncement | null>(null)

  // 添加操作函数:
  function startEdit(notification: SystemAnnouncement) {
    notificationToEdit.value = notification
    editDialogOpen.value = true
  }

  // 修改对话框引用:
  // 将 createDialogOpen 改为复用 editDialogOpen
  // NotificationCreateDialog 改为:
  // <NotificationCreateDialog
  //   v-model:open="editDialogOpen"
  //   :notification-to-edit="notificationToEdit"
  //   @success="store.fetchAnnouncements()"
  // />

  // 在行操作中添加编辑:
  // 下拉菜单中增加 Edit 选项，调用 startEdit(notification)
  ```
- **MIRROR**: Management 现有行操作模式（dropdown menu）
- **IMPORTS**: 无新增
- **GOTCHA**: 创建按钮点击时需将 `notificationToEdit` 设为 null
- **VALIDATE**: 列表中每行出现编辑操作；编辑对话框正确预填充数据

### Task 13: 同步 Management entities/notification.ts 类型
- **ACTION**: 更新 `management/src/lib/entities/notification.ts` 中的类型定义
- **IMPLEMENT**: 确保导出的 `NotificationType` 和 `NotificationCategory` 与 API 层一致，补充 `CONTEST`
- **MIRROR**: API 层枚举定义
- **IMPORTS**: 无新增
- **GOTCHA**: 该文件定义了独立的 `NotificationType`（含 `PROBLEM`, `FORUM`, `ACCOUNT`），与 API 层不同。需确认是否统一或保持独立
- **VALIDATE**: `pnpm type-check` 通过

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `buildQuery({isRead: false})` | `isRead: false` | `?isRead=false` | 修复前返回 `?unreadOnly=true` |
| `buildQuery({isRead: true})` | `isRead: true` | `?isRead=true` | 修复前不可表达 |
| `handleNewNotification(payload)` | payload with `content` | NotificationItem with `body` = payload.content | WebSocket 字段映射 |
| `NotificationType` enum | — | 所有值大写 | 与后端对齐 |
| Preferences API toggle | `{ communication: false }` | API called with correct payload | 偏好设置 |
| Admin update notification | id + dto | PUT request sent | 编辑功能 |

### Edge Cases Checklist
- [ ] `buildQuery` 无参数时返回空字符串
- [ ] `buildQuery` 只传 `isRead` 不传分页
- [ ] WebSocket payload `type` 为小写时的转换
- [ ] 偏好设置 API 失败时回滚 UI 状态
- [ ] 管理端编辑时 `notificationToEdit` 为 null 的创建模式
- [ ] 管理端编辑时隐藏目标用户选择
- [ ] 后端更新公告时批量更新所有用户副本

---

## Validation Commands

### Static Analysis
```bash
cd /home/david/project/UltiCode-Public-Next/console && pnpm type-check
cd /home/david/project/UltiCode-Public-Next/management && pnpm type-check
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw compile -q
```
EXPECT: Zero type errors, zero compilation errors

### Unit Tests
```bash
cd /home/david/project/UltiCode-Public-Next/console && pnpm test
cd /home/david/project/UltiCode-Public-Next/management && pnpm test
```
EXPECT: All tests pass

### Full Test Suite
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw test -q
```
EXPECT: No regressions

### Lint
```bash
cd /home/david/project/UltiCode-Public-Next/console && pnpm lint
cd /home/david/project/UltiCode-Public-Next/management && pnpm lint
```
EXPECT: Zero lint errors

### Browser Validation
```bash
# Console
cd /home/david/project/UltiCode-Public-Next/console && pnpm dev
# 访问 http://localhost:9002/personal/notifications
# 验证：偏好设置按钮可用，筛选 isRead 工作正常

# Management
cd /home/david/project/UltiCode-Public-Next/management && pnpm dev
# 访问 http://localhost:9003/notifications
# 验证：编辑按钮可用，CONTEST 分类可选
```
EXPECT: 功能正常

### Manual Validation
- [ ] Console 通知列表筛选"未读"只显示未读通知
- [ ] Console 偏好设置开关切换后刷新页面仍保持
- [ ] Console WebSocket 收到通知后正确显示
- [ ] Management 创建公告时 CONTEST 分类可选
- [ ] Management 编辑公告后列表更新
- [ ] 后端 Swagger UI 显示 PUT /admin/notifications/{id}

---

## Acceptance Criteria
- [ ] 所有 13 个 Task 完成
- [ ] 前后端查询参数名一致（`isRead`）
- [ ] Console 枚举统一为大写
- [ ] WebSocket payload `content` → `body` 映射正确
- [ ] 偏好设置面板在 Console 中可用
- [ ] Management CONTEST 分类可选
- [ ] Management 编辑功能完整
- [ ] 后端 PUT 端点可用
- [ ] 所有验证命令通过
- [ ] 无类型错误、无 lint 错误

## Completion Checklist
- [ ] 代码遵循已发现的模式
- [ ] 错误处理与代码库风格一致
- [ ] 日志遵循代码库约定
- [ ] 无硬编码值
- [ ] 无不必要的范围扩展
- [ ] 自包含 — 实现过程中无需额外搜索

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `apiPut` 在 Management request.ts 中未导出 | Low | Medium | 检查并添加导出 |
| Console Sheet 组件不存在 | Medium | Low | 降级为 Popover 或 Dialog |
| `AuditActionUtil` 缺少 `UPDATE_NOTIFICATION` | Medium | Low | 添加常量 |
| 管理端 entities/notification.ts 类型与 API 层冲突 | Medium | Medium | 统一或保持独立并在文档中说明 |
| 编辑公告时批量更新大量用户副本性能 | Low | High | 限制批量大小或异步处理 |

## Notes
- WebSocket payload 中 `type` 字段使用小写（如 `"mention"`, `"system"`），这与 REST API 返回的大写枚举不同。这是后端设计，前端在 `handleNewNotification` 中需做 `.toUpperCase()` 转换。
- `management/src/lib/entities/notification.ts` 定义了独立的 `NotificationType`（含 `PROBLEM`, `FORUM`, `ACCOUNT`），与 API 层的枚举不同。需确认这些是 UI 展示用的辅助类型还是需要统一。
- 后端 `AdminNotificationServiceImpl.deleteNotification` 通过 `title+type+category+createdAt` 来删除同批次所有通知副本，编辑时也需用相同逻辑定位所有副本。
- Console 前端没有现有的 settings/preferences 页面可参考，偏好设置面板需从头设计 UI。
