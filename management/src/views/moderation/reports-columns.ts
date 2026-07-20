import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconAlertTriangle,
  IconAlertCircle,
  IconDotsVertical,
  IconEye,
  IconLink,
  IconClock,
} from '@tabler/icons-vue'

import { createSelectionColumn } from '@/components/table/selectionColumn'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { badge } from '@/components/ui/terminal'
import type { SemanticColor } from '@/components/ui/terminal'
import {
  type Report,
  ReportStatus,
  ReportCategory,
  type ModeratableEntityType,
} from '@/api/admin/moderation'
import { formatDate } from '@/lib/format/date'

export interface ReportActions {
  viewEntity: (report: Report) => void
  viewInQueue: (report: Report) => void
}

// ========== Status Maps ==========
const REPORT_STATUS_ICON_MAP: Record<ReportStatus, typeof IconAlertTriangle> = {
  PENDING: IconAlertTriangle,
  REVIEWED: IconClock,
  RESOLVED: IconEye,
  DISMISSED: IconAlertCircle,
}

const REPORT_STATUS_COLOR_MAP: Record<string, SemanticColor> = {
  PENDING: 'warning',
  REVIEWED: 'info',
  RESOLVED: 'success',
  DISMISSED: 'error',
}

// ========== Category Maps ==========
const CATEGORY_COLOR_MAP: Record<string, SemanticColor> = {
  SPAM: 'warning',
  HARASSMENT: 'error',
  HATE_SPEECH: 'error',
  VIOLENCE: 'error',
  SEXUAL_CONTENT: 'error',
  MISINFORMATION: 'warning',
  WRONG_ANSWER: 'warning',
  COPYRIGHT: 'purple',
  OTHER: 'neutral',
}

const CATEGORY_ICON_MAP: Record<ReportCategory, typeof IconAlertTriangle> = {
  SPAM: IconAlertCircle,
  HARASSMENT: IconAlertTriangle,
  HATE_SPEECH: IconAlertTriangle,
  VIOLENCE: IconAlertTriangle,
  SEXUAL_CONTENT: IconAlertTriangle,
  MISINFORMATION: IconAlertCircle,
  WRONG_ANSWER: IconAlertCircle,
  COPYRIGHT: IconAlertCircle,
  OTHER: IconAlertCircle,
}

// ========== Entity Type Styles ==========
const entityTypeColors: Record<ModeratableEntityType, string> = {
  forum_post: 'text-[var(--terminal-cyan)]',
  forum_comment: 'text-[var(--terminal-cyan)]',
  solution: 'text-[var(--terminal-green)]',
  solution_comment: 'text-[var(--terminal-green)]',
  problem: 'text-[var(--terminal-amber)]',
}

// ========== Renderers ==========

function renderStatusBadge(status: ReportStatus, t: (key: string, fallback?: string) => string) {
  return badge({
    color: REPORT_STATUS_COLOR_MAP[status] ?? 'neutral',
    label: t(`moderation.reportStatus.${status}`),
    icon: REPORT_STATUS_ICON_MAP[status],
  })
}

function renderCategoryBadge(
  category: ReportCategory,
  t: (key: string, fallback?: string) => string,
) {
  return badge({
    color: CATEGORY_COLOR_MAP[category] ?? 'neutral',
    label: t(`moderation.categories.${category}`),
    icon: CATEGORY_ICON_MAP[category],
    size: 'sm',
  })
}

function truncateText(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

/**
 * Creates column definitions for the reports DataTable.
 */
export function createReportsColumns(
  t: (key: string, fallback?: string) => string,
  actions: ReportActions,
): ColumnDef<Report>[] {
  return [
    // Selection column
    ...createSelectionColumn<Report>(t, {
      checkboxClass:
        'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
    }),
    // Row number
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
    // Reporter
    {
      accessorKey: 'reporter',
      size: 120,
      minSize: 100,
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('moderation.columns.reporter'),
        ),
      cell: ({ row }) => {
        const reporterName = row.original.reporterName
        const reporterUsername = row.original.reporterUsername
        return h(
          'span',
          { class: 'text-sm truncate block' },
          reporterName || reporterUsername || t('moderation.unknownReporter'),
        )
      },
    },
    // Entity Type
    {
      accessorKey: 'entityType',
      size: 100,
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('moderation.columns.entityType'),
        ),
      cell: ({ row }) => {
        const entityType = row.original.entityType as ModeratableEntityType
        return h(
          'span',
          { class: ['font-data text-xs', entityTypeColors[entityType]] },
          t(`moderation.entityTypes.${entityType}`),
        )
      },
    },
    // Entity ID
    {
      accessorKey: 'entityId',
      minSize: 150,
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('moderation.columns.entity'),
        ),
      cell: ({ row }) => {
        const entityId = row.original.entityId
        return h(
          'span',
          {
            class: 'text-sm text-muted-foreground block truncate',
            title: entityId,
          },
          truncateText(entityId, 24),
        )
      },
    },
    // Category
    {
      accessorKey: 'category',
      size: 120,
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('moderation.columns.category'),
        ),
      cell: ({ row }) => {
        const category = row.original.category as ReportCategory
        return renderCategoryBadge(category, t)
      },
    },
    // Reason
    {
      accessorKey: 'reason',
      minSize: 150,
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('moderation.columns.reason'),
        ),
      cell: ({ row }) => {
        const reason = row.original.reason
        if (!reason) {
          return h('span', { class: 'font-data text-xs text-[var(--silver-400)] italic' }, '-')
        }
        return h(
          'span',
          { class: 'text-sm text-muted-foreground block truncate', title: reason },
          truncateText(reason, 30),
        )
      },
    },
    // Status
    {
      accessorKey: 'status',
      size: 120,
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('moderation.columns.status'),
        ),
      cell: ({ row }) => {
        const status = row.original.status as ReportStatus
        return renderStatusBadge(status, t)
      },
    },
    // Created at
    {
      accessorKey: 'createdAt',
      size: 100,
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('moderation.columns.createdAt'),
        ),
      cell: ({ row }) => {
        const date = row.original.createdAt
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
          date ? formatDate(date) : '—',
        )
      },
    },
    // Actions
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('moderation.columns.actions'),
        ),
      cell: ({ row }) => {
        const report = row.original
        return h(
          DropdownMenu,
          {},
          {
            default: () => [
              h(
                DropdownMenuTrigger,
                { asChild: true },
                {
                  default: () =>
                    h(
                      Button,
                      { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                      {
                        default: () => [
                          h(IconDotsVertical, { class: 'h-4 w-4 text-[var(--silver-400)]' }),
                        ],
                      },
                    ),
                },
              ),
              h(
                DropdownMenuContent,
                { align: 'end', class: 'border-[var(--silver-200)]' },
                {
                  default: () => [
                    h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.viewEntity(report),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconEye, { class: 'h-4 w-4 text-[var(--terminal-cyan)]' }),
                            h('span', t('moderation.reports.viewEntity')),
                          ]),
                      },
                    ),
                    h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.viewInQueue(report),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconLink, { class: 'h-4 w-4 text-[var(--terminal-amber)]' }),
                            h('span', t('moderation.reports.viewQueue')),
                          ]),
                      },
                    ),
                  ],
                },
              ),
            ],
          },
        )
      },
    },
  ]
}