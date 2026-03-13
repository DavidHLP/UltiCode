# Moderation Queue View Redesign

**Date:** 2026-03-14
**Status:** Approved
**Author:** Claude Code

## Overview

Refactor the ModerationQueueView from a card-based layout to a DataTable layout with enhanced filtering, search, and workflow improvements. This aligns the moderation UI with other management views like ProblemsListView.

## Goals

1. **Consistency**: Match the design patterns used in ProblemsListView and SubmissionsView
2. **Efficiency**: Enable faster moderation with quick actions and batch operations
3. **Information Density**: Display more items per screen with compact table layout
4. **Enhanced Filtering**: Add search and multiple filter dimensions

## Design

### Page Structure

```
┌─────────────────────────────────────────────────────────────┐
│ Terminal Header                                             │
│ moderation > 内容审核                      [状态筛选下拉]    │
├─────────────────────────────────────────────────────────────┤
│ Stats Ticker                                                │
│ total: 11  pending: 5  reviewed: 2  resolved: 2            │
├─────────────────────────────────────────────────────────────┤
│ DataTableToolbar                                            │
│ [🔍 搜索题目...] [状态 ▼] [难度 ▼] [刷新]                  │
├─────────────────────────────────────────────────────────────┤
│ DataTable                                                   │
│ ☐ | 题目          | 状态     | 举报原因      | 操作        │
│ ☐ | Longest Sub.. | PENDING  | 题目描述存... | [查看][审核]│
│ ☐ | Median of T.. | REVIEWED | 时间限制过... | [查看][审核]│
├─────────────────────────────────────────────────────────────┤
│ Pagination                                                  │
│ 行/页: [20▼]  第 1/1 页  [<<] [<] [>] [>>]                 │
└─────────────────────────────────────────────────────────────┘
```

### Column Definitions

| Column | Width | Content | Sortable |
|--------|-------|---------|----------|
| select | 40px | Checkbox | No |
| problem | flex | Title (bold) + slug (gray small) + difficulty badge | Yes |
| status | 100px | Terminal-style status badge | Yes |
| flag_reason | flex-1 | Truncated text (expand on click/hover) | No |
| reporter | 100px | Username | No |
| reported_at | 120px | Relative time (e.g., "2 hours ago") | Yes |
| actions | 140px | View details + Quick action buttons | No |

### Status Badge Design

Terminal-style badges matching the difficulty badge style in ProblemsListView:

```
PENDING   → Amber border + background  "等待审核"
REVIEWED  → Cyan border + background    "审核中"
RESOLVED  → Green border + background   "已解决"
DISMISSED → Red border + background     "已驳回"
```

CSS Classes:
```css
/* PENDING */
bg-[oklch(0.75_0.15_85/0.15)] border-[oklch(0.75_0.15_85/0.4)] text-[var(--terminal-amber)]

/* REVIEWED */
bg-[oklch(0.7_0.12_200/0.15)] border-[oklch(0.7_0.12_200/0.4)] text-[var(--terminal-cyan)]

/* RESOLVED */
bg-[oklch(0.7_0.15_145/0.15)] border-[oklch(0.7_0.15_145/0.4)] text-[var(--terminal-green)]

/* DISMISSED */
bg-[oklch(0.6_0.2_25/0.15)] border-[oklch(0.6_0.2_25/0.4)] text-[var(--terminal-red)]
```

### Detail Drawer

Right-side drawer for viewing full flag details and moderation:

```
┌─────────────────────────────────────────┐
│ ← 关闭                                  │
├─────────────────────────────────────────┤
│ > 题目详情                              │
├─────────────────────────────────────────┤
│ 标题: Longest Substring...              │
│ Slug: longest-substring...              │
│ 难度: 🟡 MEDIUM                         │
│ 状态: PENDING                           │
├─────────────────────────────────────────┤
│ > 举报信息                              │
├─────────────────────────────────────────┤
│ 举报原因:                               │
│ [Full reason text in bordered box]      │
│                                         │
│ 举报人: yuki                            │
│ 举报时间: 2025-03-10 08:30:00           │
│                                         │
│ 审核备注: [暂无或显示现有备注]          │
├─────────────────────────────────────────┤
│ > 审核操作                              │
├─────────────────────────────────────────┤
│ 新状态: [PENDING ▼]                     │
│                                         │
│ 审核备注:                               │
│ [Textarea for moderation notes]         │
│                                         │
│ [取消] [保存]                           │
└─────────────────────────────────────────┘
```

### Batch Operations

Floating action bar when rows are selected:

```
┌─────────────────────────────────────────────────────────────┐
│ > SELECTED:3  │  [✓ 批量解决]  [✗ 批量驳回]  │  [ESC 清除] │
└─────────────────────────────────────────────────────────────┘
```

### Quick Actions

In the actions column:
- **Quick Resolve** → Set status to RESOLVED without opening drawer
- **Quick Dismiss** → Set status to DISMISSED without opening drawer
- **View Details** → Open drawer for full information and moderation

## Implementation

### Files to Modify

1. **`management/src/views/moderation/ModerationQueueView.vue`**
   - Replace card layout with DataTable
   - Add DataTableToolbar for filters
   - Implement detail drawer
   - Add batch operations

2. **`management/src/views/moderation/columns.ts`** (NEW)
   - Column definitions for the DataTable
   - Status badge renderer
   - Actions dropdown

3. **`management/src/views/moderation/components/ModerationDrawer.vue`** (NEW)
   - Detail drawer component
   - Moderation form

### Dependencies

- `@tanstack/vue-table` - Already in use
- `DataTable.vue` - Existing component
- `DataTableToolbar.vue` - Existing component
- `BaseDetailDrawer.vue` - Existing component for drawer pattern

### API Integration

Existing API endpoints (no changes needed):
- `GET /admin/problems/flagged` - List flagged problems
- `POST /admin/problems/:id/moderate` - Moderate single problem
- `POST /admin/problems/batch-moderate` - Batch moderation

## Success Criteria

1. DataTable displays all flagged problems with correct columns
2. Status badges render with correct colors
3. Search filters by problem title
4. Status and difficulty filters work correctly
5. Batch operations update multiple problems
6. Detail drawer shows full information
7. Quick actions work without opening drawer
8. Pagination works correctly

## Out of Scope

- Real-time updates (polling/websockets)
- Advanced filtering by date range
- Export functionality
- Audit log viewing in drawer
