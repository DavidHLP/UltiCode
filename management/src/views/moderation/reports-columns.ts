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

import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
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

// ========== Status Styles ==========
const statusStyles: Record<
  ReportStatus,
  { bg: string; border: string; text: string; icon: typeof IconAlertTriangle }
> = {
  PENDING: {
    bg: 'bg-[oklch(0.75_0.15_85/0.15)]',
    border: 'border-[oklch(0.75_0.15_85/0.4)]',
    text: 'text-[var(--terminal-amber)]',
    icon: IconAlertTriangle,
  },
  REVIEWED: {
    bg: 'bg-[oklch(0.7_0.12_200/0.15)]',
    border: 'border-[oklch(0.7_0.12_200/0.4)]',
    text: 'text-[var(--terminal-cyan)]',
    icon: IconClock,
  },
  RESOLVED: {
    bg: 'bg-[oklch(0.7_0.15_145/0.15)]',
    border: 'border-[oklch(0.7_0.15_145/0.4)]',
    text: 'text-[var(--terminal-green)]',
    icon: IconEye,
  },
  DISMISSED: {
    bg: 'bg-[oklch(0.6_0.2_25/0.15)]',
    border: 'border-[oklch(0.6_0.2_25/0.4)]',
    text: 'text-[var(--terminal-red)]',
    icon: IconAlertCircle,
  },
}

// ========== Category Styles ==========
const categoryStyles: Record<
  ReportCategory,
  { color: string; icon: typeof IconAlertTriangle }
> = {
  SPAM: { color: 'text-[var(--terminal-amber)]', icon: IconAlertCircle },
  HARASSMENT: { color: 'text-[var(--terminal-red)]', icon: IconAlertTriangle },
  HATE_SPEECH: { color: 'text-[var(--terminal-red)]', icon: IconAlertTriangle },
  VIOLENCE: { color: 'text-[var(--terminal-red)]', icon: IconAlertTriangle },
  SEXUAL_CONTENT: { color: 'text-[var(--terminal-red)]', icon: IconAlertTriangle },
  MISINFORMATION: { color: 'text-[var(--terminal-amber)]', icon: IconAlertCircle },
  WRONG_ANSWER: { color: 'text-[var(--terminal-amber)]', icon: IconAlertCircle },
  COPYRIGHT: { color: 'text-[var(--terminal-purple)]', icon: IconAlertCircle },
  OTHER: { color: 'text-[var(--silver-500)]', icon: IconAlertCircle },
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

function renderStatusBadge(status: ReportStatus, t: (key: string) => string) {
  const style = statusStyles[status]
  const Icon = style.icon

  return h('div', { class: 'flex items-center gap-2' }, [
    h(Icon, { class: ['h-3.5 w-3.5', style.text] }),
    h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border',
          style.bg,
          style.border,
          style.text,
        ].join(' '),
      },
      t(`moderation.reportStatus.${status}`),
    ),
  ])
}

function renderCategoryBadge(category: ReportCategory, t: (key: string) => string) {
  const style = categoryStyles[category]
  const Icon = style.icon

  return h('div', { class: 'flex items-center gap-1.5' }, [
    h(Icon, { class: ['h-3 w-3', style.color] }),
    h(
      'span',
      {
        class: [
          'font-data text-[10px] uppercase tracking-[0.05em]',
          style.color,
        ].join(' '),
      },
      t(`moderation.categories.${category}`),
    ),
  ])
}

function truncateText(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

/**
 * Creates column definitions for the reports DataTable.
 */
export function createReportsColumns(
  t: (key: string) => string,
  actions: ReportActions,
): ColumnDef<Report>[] {
  return [
    // Selection column
    {
      id: 'select',
      size: 40,
      minSize: 40,
      maxSize: 40,
      header: ({ table }) =>
        h(Checkbox, {
          modelValue:
            table.getIsAllPageRowsSelected() ||
            (table.getIsSomePageRowsSelected() && 'indeterminate'),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
            table.toggleAllPageRowsSelected(!!value),
          'aria-label': 'Select all',
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': 'Select row',
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      enableSorting: false,
      enableHiding: false,
    },
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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.columns.reporter'),
        ),
      cell: ({ row }) => {
        const reporter = row.original.reporter
        return h('span', { class: 'text-sm truncate block' },
          reporter?.display_name || reporter?.username || t('moderation.unknownReporter'),
        )
      },
    },
    // Entity Type
    {
      accessorKey: 'entity_type',
      size: 100,
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.columns.entityType'),
        ),
      cell: ({ row }) => {
        const entityType = row.original.entity_type as ModeratableEntityType
        return h(
          'span',
          { class: ['font-data text-xs', entityTypeColors[entityType]] },
          t(`moderation.entityTypes.${entityType}`),
        )
      },
    },
    // Entity ID
    {
      accessorKey: 'entity_id',
      minSize: 150,
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.columns.entity'),
        ),
      cell: ({ row }) => {
        const entityId = row.original.entity_id
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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.columns.status'),
        ),
      cell: ({ row }) => {
        const status = row.original.status as ReportStatus
        return renderStatusBadge(status, t)
      },
    },
    // Created at
    {
      accessorKey: 'created_at',
      size: 100,
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.columns.createdAt'),
        ),
      cell: ({ row }) => {
        const date = row.original.created_at
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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
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
                      { default: () => [h(IconDotsVertical, { class: 'h-4 w-4 text-[var(--silver-400)]' })] },
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
