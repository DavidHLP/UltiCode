# Plan: Fix CR Issues

## Summary
修复 Code Review 中发现的 5 个中低优先级问题，涵盖 console 通知 store 的空 handler、NotificationCreateDialog 的语法噪声，以及 NotificationsView 中的 console.error 调用。

## User Story
As a developer, I want clean, warning-free code with proper error handling, so that the codebase remains maintainable and production logs are structured.

## Problem → Solution

| # | 文件 | 问题 | 修复方式 |
|---|---|---|---|
| 1 | `console/src/stores/notification.ts` | 两个空 handler 被 `eslint-disable` 抑制 | 删除空的 stub，移除 eslint-disable 注释 |
| 2 | `management/src/views/notifications/NotificationCreateDialog.vue` | 模板中有孤立 `>` 字符 | 删除 `>` |
| 3 | `console/src/views/personal/NotificationsView.vue:143` | `console.error` 在生产代码中 | 替换为 `toast.error` |
| 4 | `console/src/views/personal/NotificationsView.vue:153` | `console.error` 在生产代码中 | 替换为 `toast.error` |
| 5 | `console/src/views/personal/NotificationsView.vue:183` | `console.error` 在生产代码中 | 替换为 `toast.error` |

## Metadata
- **Complexity**: Small
- **Source PRD**: N/A
- **PRD Phase**: N/A (standalone CR fix)
- **Estimated Files**: 3

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `console/src/stores/notification.ts` | 173-178 | 当前空 handler 的确切写法 |
| P0 | `console/src/views/personal/NotificationsView.vue` | 138-186 | `console.error` 的使用上下文 |
| P1 | `management/src/views/notifications/NotificationCreateDialog.vue` | 277-278 | 孤立 `>` 的确切位置 |

---

## Patterns to Mirror

### HANDLER_REGISTRATION
// SOURCE: `console/src/stores/notification.ts:199-207`
WebSocket 事件 handler 注册（已有模板，只是需要删除未使用的 stub）：
```typescript
socketManager.on(
  NotificationEvent.SYSTEM_ANNOUNCEMENT,
  handleNewNotification,
);
socketManager.on(
  NotificationEvent.SUBMISSION_RESULT,
  handleSubmissionResult,
);
socketManager.on(NotificationEvent.BADGE_EARNED, handleBadgeEarned);
```
当 handler 实现为空 stub 时，应该直接移除注册行，不留哑代码。

### ERROR_HANDLING_VUE
// SOURCE: `console/src/views/personal/NotificationsView.vue:139-146`
Vue 组件中 UI 错误处理的正确模式：
```typescript
async function handleMarkAllRead() {
  try {
    await notificationStore.markAllRead();
    toast.success(t("personal.messages.notificationsMarkedRead"));
  } catch (error) {
    toast.error(t("common.status.error"));
  }
}
```
错误不打印到 console，而是通过 toast 反馈给用户。

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `console/src/stores/notification.ts` | UPDATE | 删除空 handler 及其 eslint-disable，移除对应的 socketManager.on 注册 |
| `console/src/views/personal/NotificationsView.vue` | UPDATE | 替换 3 处 console.error 为 toast.error |
| `management/src/views/notifications/NotificationCreateDialog.vue` | UPDATE | 删除模板中孤立的 `>` 字符 |

---

## NOT Building
- 不实现 `handleSubmissionResult` 和 `handleBadgeEarned` 的功能逻辑
- 不修改 WebSocket 事件订阅的整体结构
- 不添加新的错误处理分支（仅替换已有的）

---

## Step-by-Step Tasks

### Task 1: Remove empty handlers and eslint-disable comments

**ACTION**: 删除 `console/src/stores/notification.ts` 中第 173-178 行的空 handler stub 及对应的 `eslint-disable` 注释。

**IMPLEMENT**:
```typescript
// 删除这两行（包含注释）：
// handleSubmissionResult - WebSocket callback placeholder
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const handleSubmissionResult = (_: SubmissionResultPayload) => { };

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const handleBadgeEarned = (_: BadgeEarnedPayload) => { };
```
同时在 `setupRealtimeListeners()` 中删除对应的注册行（第 203-207 行附近）：
```typescript
// 删除这两行：
socketManager.on(
  NotificationEvent.SUBMISSION_RESULT,
  handleSubmissionResult,
);
socketManager.on(NotificationEvent.BADGE_EARNED, handleBadgeEarned);
```

**MIRROR**: HANDLER_REGISTRATION pattern — 只注册实际有逻辑的 handler。

**IMPORTS**: 无需新增 import。

**GOTCHA**: 只删除 handler 变量和对应的 `.on()` 调用，不要删除 `SubmissionResultPayload` 和 `BadgeEarnedPayload` 的 import——它们仍被其他地方引用。

**VALIDATE**: `pnpm lint` 在 console 目录下无 warning。

---

### Task 2: Replace console.error with toast.error

**ACTION**: 将 `console/src/views/personal/NotificationsView.vue` 中 3 处 `console.error` 替换为 `toast.error`。

**IMPLEMENT**:

第 143 行：
```typescript
// Before:
console.error("Failed to mark notifications as read", error);
// After:
toast.error(t("common.status.error"));
```

第 153 行：
```typescript
// Before:
console.error("Failed to clear notifications", error);
// After:
toast.error(t("common.status.error"));
```

第 183 行：
```typescript
// Before:
console.error("Failed to delete notification", error);
// After:
toast.error(t("common.status.error"));
```

注意：catch 的 `error` 变量不再使用，改为静默捕获（保持 catch 块不变，只改 console.error）。

**MIRROR**: ERROR_HANDLING_VUE pattern — UI 错误通过 toast 反馈而非 console。

**IMPORTS**: 已有 `toast` 从 `vue-sonner` 导入。

**GOTCHA**: `error` 变量仍在 catch 作用域中但不再引用，TypeScript 不会报错（因为 `catch` 块的参数默认不可达检查被禁用）。无需 `void error`。

**VALIDATE**: `pnpm lint` 在 console 目录下无 warning；页面重新加载后功能行为不变。

---

### Task 3: Remove stray `>` in template

**ACTION**: 删除 `management/src/views/notifications/NotificationCreateDialog.vue` 第 278 行的孤立 `>` 字符。

**IMPLEMENT**:
```vue
<!-- Before (line 277-278): -->
<FieldSet v-if="!isEditMode">
              >
              <FieldDescription class="text-[var(--silver-500)]">

<!-- After: -->
<FieldSet v-if="!isEditMode">
              <FieldDescription class="text-[var(--silver-500)]">
```

**MIRROR**: Vue template syntax — 闭合标签和属性之间不应有孤立字符。

**IMPORTS**: 无。

**GOTCHA**: 确保 `FieldDescription` 仍正确嵌套在 `FieldSet` 内。

**VALIDATE**: `pnpm lint` 在 management 目录下无新增 warning。

---

## Testing Strategy

### Manual Validation
- [ ] `console/src/stores/notification.ts` lint 无 warning
- [ ] `console/src/views/personal/NotificationsView.vue` lint 无 warning
- [ ] `management/src/views/notifications/NotificationCreateDialog.vue` lint 无 warning
- [ ] console 页面打开通知列表，标记已读、清空、删除操作正常
- [ ] management 页面打开通知创建对话框，切换编辑/创建模式正常

---

## Validation Commands

```bash
# lint check
cd console && pnpm lint
cd management && pnpm lint
```
EXPECT: 无 warning

```bash
# type check
cd console && pnpm type-check
cd management && pnpm type-check
```
EXPECT: 仅 pre-existing DonutChart.vue 错误，无新增

---

## Acceptance Criteria
- [ ] Task 1: 两个空 handler 及其 eslint-disable 注释已删除，对应的 socketManager.on 注册行已删除
- [ ] Task 2: 3 处 console.error 已替换为 toast.error
- [ ] Task 3: 孤立 `>` 已删除
- [ ] `pnpm lint` 在两个目录下均无 warning
- [ ] 功能行为不变

## Completion Checklist
- [ ] 代码遵循 HANDLER_REGISTRATION 和 ERROR_HANDLING_VUE pattern
- [ ] 无新增 lint warning
- [ ] 无新增 type error
- [ ] 无不必要的 scope 添加

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 删除 socketManager.on 调用导致事件丢失 | Low | High | 这些 handler 本身就是空的，不影响功能 |
| toast.error 替换后错误信息不够详细 | Low | Low | 这是现有代码库的一致处理方式 |

## Notes
3 个文件都是小改动，每个任务独立，可在一次实现中顺序完成。
