# Implementation Plan: Unified AdminCommentController & Frontend Integration

This plan details the steps to implement a unified comment moderation system for both Forum Comments and Solution Comments.

## 1. Database Schema Update

We need to align the `SolutionComment` model with `ForumComment` to support moderation features.

### `backend/prisma/schema.prisma`

- Update `SolutionComment` model to include:
  - `is_flagged` (Boolean, default: false)
  - `flagged_reason` (String?, Text)
  - `flagged_at` (DateTime?)
  - `is_deleted` (Boolean, default: false)
  - `deleted_at` (DateTime?)
  - `deleted_by` (String?)

## 2. Backend Implementation

### A. DTOs and Interfaces
Create a unified structure to handle both comment types.

**`backend/src/admin/dto/comment.dto.ts`** (New File)
- `CommentType` enum: `'forum' | 'solution'`
- `CommentQueryDto`:
  - `type`: `CommentType` (Required)
  - `page`, `limit`, `search`, `is_flagged`
- `CommentResponseDto`: Unified shape for returning comments to frontend.
  - `id`, `content`, `author`, `source_id` (post/solution id), `source_title`, `created_at`, `is_flagged`, `flagged_reason`, etc.

### B. Service Layer
**`backend/src/admin/services/admin-comment.service.ts`** (New File)
- Create `AdminCommentService` class.
- Methods:
  - `findAll(query: CommentQueryDto)`:
    - If `type === 'forum'`, query `prisma.forumComment`.
    - If `type === 'solution'`, query `prisma.solutionComment`.
    - Map results to `CommentResponseDto`.
  - `delete(id: string, type: CommentType, adminId: string)`:
    - Soft delete based on type.
  - `flag(id: string, type: CommentType, reason: string, adminId: string)`
  - `unflag(id: string, type: CommentType, adminId: string)`

### C. Controller Layer
**`backend/src/admin/controllers/admin-comment.controller.ts`** (New File)
- Endpoints:
  - `GET /admin/comments`: wrapper for `service.findAll`.
  - `DELETE /admin/comments/:id`: Requires `type` query param.
  - `POST /admin/comments/:id/flag`: Requires `type` query param.
  - `POST /admin/comments/:id/unflag`: Requires `type` query param.
- **Authorization**:
  - Use `@RequirePermissions` with a mapped resource based on type (or a generic COMMENT resource if added to PermissionResource enum, otherwise map 'forum' -> FORUM_POST, 'solution' -> SOLUTION).

### D. Module Update
**`backend/src/admin/admin.module.ts`**
- Register `AdminCommentService` and `AdminCommentController`.

## 3. Frontend Implementation

### A. API Integration
**`admin-frontend/src/api/admin/comments.ts`** (New File)
- `fetchComments(params)`
- `deleteComment(id, type)`
- `flagComment(id, type, reason)`
- `unflagComment(id, type)`

### B. View Components
**`admin-frontend/src/views/comments/CommentsListView.vue`** (New File)
- **Layout**:
  - Header: "Comments Management"
  - Tabs: "All Comments", "Flagged"
  - Filter Bar: Search, Type Selector (All/Forum/Solution)
- **Data Table**:
  - Columns: Content (truncated), Author, Type (Badge), Source (Link to post/solution), Status (Flagged/Deleted), Actions.
  - Actions: Delete, Flag/Unflag, View Context.

### C. Navigation & Routing
**`admin-frontend/src/router/index.ts`**
- Add route:
  ```typescript
  {
    path: 'comments',
    name: 'comments',
    component: () => import('@/views/comments/CommentsListView.vue'),
    meta: { permission: { action: 'READ', resource: 'SYSTEM' } } // Or generic permission
  }
  ```

**`admin-frontend/src/components/layout/AppSidebar.vue`**
- Add "Comments" item to `navMain` array.
- Icon: `IconMessageCircle` (or similar from tabler-icons).

## 4. Migration & Cleanup

- Run `npm run prisma:migrate --prefix backend` to apply schema changes.
- Verify existing `ForumComment` data is unaffected.
- Verify `SolutionComment` defaults work correctly.

## 5. Testing Plan
- **Backend Tests**: Unit tests for `AdminCommentService` handling both types.
- **E2E Tests**: Verify admin can list, flag, and delete comments of both types.
- **Frontend Verification**: Check UI renders correctly, filters work, and actions persist.
