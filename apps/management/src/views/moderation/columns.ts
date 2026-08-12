import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconAlertTriangle,
  IconAlertCircle,
  IconCheck,
  IconClock,
  IconCode,
  IconEye,
  IconFileText,
  IconMessage,
  IconMessages,
  IconScale,
  IconX,
  IconTournament,
  IconFlag,
  IconUser,
} from '@tabler/icons-vue'

import { createSelectionColumn } from '@/components/table/selectionColumn'
import { createEntityActionsMenu } from '@/components/table/entityActions'
import {
  type ModerationQueueItem,
  type ModerationStatus,
  type ReportCategory,
  type ModeratableEntityType,
  ModerationActionType,
} from '@/api/admin/moderation'
import { formatDate } from '@/lib/format/date'
import { badge, MODERATION_STATUS_COLOR_MAP } from '@/components/ui/terminal'
import type { SemanticColor } from '@/components/ui/terminal'

export interface ModerationActions {
  viewEntity: (item: ModerationQueueItem) => void
  openDrawer: (item: ModerationQueueItem) => void
  quickAction: (id: string, action: ModerationActionType) => void
  claimItem: (id: string) => void
}

// ========== Status Icon Map ==========
const STATUS_ICON_MAP: Record<ModerationStatus, typeof IconAlertTriangle> = {
  PENDING: IconAlertTriangle,
  UNDER_REVIEW: IconClock,
  RESOLVED: IconCheck,
  DISMISSED: IconX,
  APPEAL_PENDING: IconScale,
}

// ========== Entity Type Styles ==========
const ENTITY_TYPE_CONFIG: Record<
  ModeratableEntityType,
  { icon: typeof IconFileText; color: SemanticColor; label: string }
> = {
  forum_post: { icon: IconMessages, color: 'info', label: 'moderation.entityTypes.forum_post' },
  forum_comment: {
    icon: IconMessage,
    color: 'info',
    label: 'moderation.entityTypes.forum_comment',
  },
  solution: { icon: IconCode, color: 'success', label: 'moderation.entityTypes.solution' },
  solution_comment: {
    icon: IconMessage,
    color: 'success',
    label: 'moderation.entityTypes.solution_comment',
  },
  problem: { icon: IconFileText, color: 'warning', label: 'moderation.entityTypes.problem' },
}

// ========== Category Styles ==========
const CATEGORY_CONFIG: Record<
  ReportCategory,
  { color: SemanticColor; icon: typeof IconAlertTriangle }
> = {
  SPAM: { color: 'warning', icon: IconAlertCircle },
  HARASSMENT: { color: 'error', icon: IconAlertTriangle },
  HATE_SPEECH: { color: 'error', icon: IconAlertTriangle },
  VIOLENCE: { color: 'error', icon: IconAlertTriangle },
  SEXUAL_CONTENT: { color: 'error', icon: IconAlertTriangle },
  MISINFORMATION: { color: 'warning', icon: IconAlertCircle },
  WRONG_ANSWER: { color: 'warning', icon: IconAlertCircle },
  COPYRIGHT: { color: 'purple', icon: IconAlertCircle },
  OTHER: { color: 'neutral', icon: IconAlertCircle },
}

// ========== Priority Styles ==========
function getPriorityStyle(priority: number): SemanticColor {
  if (priority >= 8) return 'error'
  if (priority >= 5) return 'warning'
  if (priority >= 3) return 'info'
  return 'neutral'
}

// ========== Renderers ==========

function renderStatusBadge(
  status: ModerationStatus,
  t: (key: string, fallback?: string) => string,
) {
  return badge({
    color: MODERATION_STATUS_COLOR_MAP[status] ?? 'neutral',
    label: t(`moderation.status.${status}`),
    icon: STATUS_ICON_MAP[status],
  })
}

function renderEntityTypeBadge(entityType: ModeratableEntityType, t: (key: string) => string) {
  const config = ENTITY_TYPE_CONFIG[entityType]
  return badge({ color: config.color, label: t(config.label), icon: config.icon })
}

function renderCategoryBadge(
  category: ReportCategory,
  t: (key: string, fallback?: string) => string,
) {
  const config = CATEGORY_CONFIG[category]
  return badge({
    color: config.color,
    label: t(`moderation.categories.${category}`),
    icon: config.icon,
    size: 'sm',
  })
}

function renderPriorityBadge(priority: number) {
  return badge({
    color: getPriorityStyle(priority),
    label: String(priority),
    icon: IconFlag,
    size: 'sm',
  })
}

function renderAssignedUser(
  assignedToName: string | undefined,
  assignedToUsername: string | undefined,
  t: (key: string) => string,
) {
  const displayName = assignedToName || assignedToUsername
  if (!displayName) {
    return h(
      'span',
      { class: 'font-data text-xs text-[var(--foreground-muted)] italic' },
      t('moderation.queue.unassigned'),
    )
  }
  return h('div', { class: 'flex items-center gap-2' }, [
    h(IconUser, { class: 'h-3.5 w-3.5 text-[var(--foreground-muted)]' }),
    h('span', { class: 'text-sm text-[var(--foreground)] truncate' }, displayName),
  ])
}

// ========== Helper Functions ==========

function truncateText(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

/**
 * Creates column definitions for the moderation queue DataTable.
 * @param t - Translation function from useI18n
 * @param actions - Action handlers for moderation operations
 * @returns Array of column definitions for @tanstack/vue-table
 */
export function createColumns(
  t: (key: string, fallback?: string) => string,
  actions: ModerationActions,
): ColumnDef<ModerationQueueItem>[] {
  return [
    // Selection column
    ...createSelectionColumn<ModerationQueueItem>(t, {
      checkboxClass:
        'border-[var(--border-subtle)] data-[state=checked]:bg-[var(--primary)] data-[state=checked]:border-[var(--primary)]',
    }),
    // Row number column
    {
      id: 'row_num',
      header: () => '#',
      cell: ({ row, table }) => {
        const pageIndex = table.getState().pagination.pageIndex
        const pageSize = table.getState().pagination.pageSize
        const rowNum = pageIndex * pageSize + row.index + 1
        return h('span', { class: 'terminal-row-num' }, String(rowNum).padStart(2, '0'))
      },
      enableSorting: false,
      enableHiding: false,
    },
    // Entity type column
    {
      accessorKey: 'entityType',
      size: 120,
      minSize: 100,
      maxSize: 140,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.entityType'),
        ),
      cell: ({ row }) => {
        const entityType = row.original.entityType as ModeratableEntityType
        return renderEntityTypeBadge(entityType, t)
      },
    },
    // Entity ID / Title column
    {
      id: 'entity',
      minSize: 180,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.entity'),
        ),
      cell: ({ row }) => {
        const item = row.original
        return h('div', { class: 'flex flex-col gap-1 py-1' }, [
          h(
            'span',
            {
              class: 'font-medium text-sm truncate',
              title: item.entityId,
            },
            truncateText(item.entityId, 24),
          ),
          h(
            'span',
            { class: 'text-muted-foreground text-xs truncate' },
            truncateText(item.entityId, 32),
          ),
        ])
      },
    },
    // Category column
    {
      accessorKey: 'primaryCategory',
      size: 120,
      minSize: 100,
      maxSize: 140,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.category'),
        ),
      cell: ({ row }) => {
        const category = row.original.primaryCategory as ReportCategory
        return renderCategoryBadge(category, t)
      },
    },
    // Status column
    {
      accessorKey: 'status',
      size: 130,
      minSize: 110,
      maxSize: 150,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.status'),
        ),
      cell: ({ row }) => {
        const status = row.original.status as ModerationStatus
        return renderStatusBadge(status, t)
      },
    },
    // Resolution column (操作内容)
    {
      accessorKey: 'resolution',
      size: 120,
      minSize: 100,
      maxSize: 140,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.resolution'),
        ),
      cell: ({ row }) => {
        const resolution = row.original.resolution
        if (!resolution) {
          return h('span', { class: 'text-[var(--foreground-muted)]' }, '—')
        }
        // Map action to color
        const colorMap: Record<string, string> = {
          DELETED: 'text-foreground-strong',
          HIDDEN: 'text-foreground-strong',
          RESTORED: 'text-foreground-strong',
          WARNED: 'text-foreground-strong',
          TEMP_BANNED: 'text-foreground-strong',
          PERM_BANNED: 'text-foreground-strong',
          DISMISSED: 'text-[var(--foreground-muted)]',
          RESOLVED: 'text-foreground-strong',
          APPEAL_PENDING: 'text-foreground-strong',
          APPEAL_APPROVED: 'text-foreground-strong',
          APPEAL_REJECTED: 'text-foreground-strong',
        }
        const colorClass = colorMap[resolution] || 'text-[var(--foreground-muted)]'
        return h(
          'span',
          { class: `font-data text-xs ${colorClass}` },
          t(`moderation.actions.${resolution}`),
        )
      },
    },
    // Priority column
    {
      accessorKey: 'priority',
      size: 70,
      minSize: 60,
      maxSize: 80,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.priority'),
        ),
      cell: ({ row }) => {
        const priority = row.original.priority
        return renderPriorityBadge(priority)
      },
    },
    // Report count column
    {
      accessorKey: 'reportCount',
      size: 70,
      minSize: 60,
      maxSize: 80,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.reports'),
        ),
      cell: ({ row }) => {
        const count = row.original.reportCount
        return h(
          'span',
          {
            class: [
              'font-data text-xs tabular-nums',
              count >= 3 ? 'text-foreground-strong' : 'text-[var(--foreground-muted)]',
            ].join(' '),
          },
          count,
        )
      },
    },
    // Assigned to column
    {
      accessorKey: 'assignedTo',
      size: 120,
      minSize: 100,
      maxSize: 140,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.assignedTo'),
        ),
      cell: ({ row }) => {
        return renderAssignedUser(row.original.assignedToName, row.original.assignedToUsername, t)
      },
    },
    // Created at column
    {
      accessorKey: 'createdAt',
      size: 100,
      minSize: 80,
      maxSize: 120,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.createdAt'),
        ),
      cell: ({ row }) => {
        const date = row.original.createdAt
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--foreground-muted)] tabular-nums' },
          date ? formatDate(date) : '—',
        )
      },
    },
    // Actions column
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]',
          },
          t('moderation.columns.actions'),
        ),
      cell: ({ row }) => {
        const item = row.original
        const canClaim = !item.assignedToId
        const canAction = item.status === 'PENDING' || item.status === 'UNDER_REVIEW'
        return createEntityActionsMenu(
          [
            {
              label: t('moderation.queue.viewDetails'),
              onSelect: () => actions.openDrawer(item),
              icon: IconEye,
              iconClass: 'h-4 w-4 text-foreground-strong',
            },
            {
              label: t('moderation.queue.viewEntity'),
              onSelect: () => actions.viewEntity(item),
              icon: IconTournament,
              iconClass: 'h-4 w-4 text-foreground-strong',
            },
            {
              label: t('moderation.queue.claimItem'),
              onSelect: () => actions.claimItem(item.id),
              icon: IconUser,
              iconClass: 'h-4 w-4 text-foreground-strong',
              hidden: !canClaim,
            },
            { kind: 'separator', hidden: !canAction },
            {
              label: t('moderation.quickResolve'),
              onSelect: () => actions.quickAction(item.id, ModerationActionType.RESOLVED),
              icon: IconCheck,
              iconClass: 'h-4 w-4 text-foreground-strong',
              labelClass: 'text-[var(--foreground-strong)]',
              hidden: !canAction,
            },
            {
              label: t('moderation.quickDismiss'),
              onSelect: () => actions.quickAction(item.id, ModerationActionType.DISMISSED),
              icon: IconX,
              iconClass: 'h-4 w-4 text-foreground-strong',
              labelClass: 'text-[var(--foreground-strong)]',
              hidden: !canAction,
            },
          ],
          {
            triggerClass:
              'h-8 w-8 p-0 hover:bg-[var(--surface-highlight)] dark:hover:bg-[var(--foreground-strong)]',
            triggerIconClass: 'h-4 w-4 text-[var(--foreground-muted)]',
            contentClass: 'border-[var(--border-subtle)] dark:border-[var(--foreground-strong)]',
            itemClass: 'font-data text-xs cursor-pointer',
            separatorClass: 'bg-[var(--border-subtle)] dark:bg-[var(--foreground-strong)]',
          },
        )
      },
    },
  ]
}