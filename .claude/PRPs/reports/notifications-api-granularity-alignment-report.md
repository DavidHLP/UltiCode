# Implementation Report: Notifications API Granularity Alignment

## Summary
Aligned Management frontend and Spring Boot backend notification APIs by adding server-side pagination, announcement_id-based dedup/cascade, batch creator queries, type/category enum alignment, i18n fixes, and ghost type cleanup.

## Tasks Completed

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | V110 Flyway migration (announcement_id) | Done | Includes backfill for existing SYSTEM notifications |
| 2 | Add announcementId to Notification entity | Done | @TableField("announcement_id") |
| 3 | Create AdminNotificationQueryDTO | Done | page, limit, keyword, type, category, sortBy, sortOrder |
| 4 | Update AdminNotificationVO | Done | Added announcementId field |
| 5 | Refactor AdminNotificationServiceImpl | Done | Pagination, announcementId dedup, batch creator query, cascade by announcementId |
| 6 | Update AdminNotificationController | Done | GET returns PageResult<AdminNotificationVO> |
| 7 | Update AdminNotificationService interface | Done | Signature updated for paginated query |
| 8 | Refactor frontend notifications API | Done | String literal unions, PageResult, void delete return |
| 9 | Refactor notifications store | Done | Pagination state, delete refreshes list |
| 10 | Refactor NotificationsListView | Done | Server-side pagination, Category column, useDataTable |
| 11 | Update NotificationCreateDialog | Done | No changes needed — already had category selection |
| 12 | Align i18n keys | Done | zh-CN + en-US categories now match backend enums |
| 13 | Delete ghost types | Done | Removed lib/entities/notification.ts (no imports) |
| 14 | End-to-end verification | Done | Backend compiles, frontend type-checks, lint passes |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Backend Compile | Done | `./mvnw compile -DskipTests` passes |
| Frontend Type Check | Done | `vue-tsc --noEmit` — only TS5101 baseUrl deprecation warning |
| Frontend Lint | Done | ESLint passes on all notification files |
| Integration | Done | No import errors, store correctly wires to API |

## Files Changed

| File | Action | Description |
|------|--------|-------------|
| `db-manager/migrations/V110__add_announcement_id_to_notifications.sql` | CREATED | Flyway migration with backfill |
| `backend-spring/.../notification/entity/Notification.java` | UPDATED | Added announcementId field |
| `backend-spring/.../admin/dto/AdminNotificationQueryDTO.java` | CREATED | Pagination query DTO |
| `backend-spring/.../admin/dto/AdminNotificationVO.java` | UPDATED | Added announcementId |
| `backend-spring/.../admin/service/AdminNotificationService.java` | UPDATED | Interface signature changes |
| `backend-spring/.../admin/service/impl/AdminNotificationServiceImpl.java` | REWRITTEN | Core refactoring |
| `backend-spring/.../admin/controller/AdminNotificationController.java` | REWRITTEN | Paginated GET endpoint |
| `management/src/api/admin/notifications.ts` | REWRITTEN | New types, PageResult, void delete |
| `management/src/stores/admin/notifications.ts` | REWRITTEN | Pagination, delete refresh |
| `management/src/views/notifications/NotificationsListView.vue` | REWRITTEN | Server-side pagination, Category column |
| `management/src/i18n/locales/zh-CN/modules/notifications.ts` | REWRITTEN | Aligned with backend enums |
| `management/src/i18n/locales/en-US/modules/notifications.ts` | REWRITTEN | Aligned with backend enums |
| `management/src/lib/entities/notification.ts` | DELETED | Ghost types removed |

## Deviations from Plan
- NotificationCreateDialog required no changes (already had category selection)
- Used `@/utils/request` import path instead of `@/lib/api`

## Issues Encountered
- None — all changes compiled and type-checked successfully
