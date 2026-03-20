import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCheck,
  IconX,
  IconClock,
  IconDotsVertical,
  IconEye,
  IconScale,
  IconUser,
} from '@tabler/icons-vue'

import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { type Appeal, AppealStatus } from '@/api/admin/moderation'
import { formatDate } from '@/lib/format/date'

export interface AppealActions {
  viewAppeal: (appeal: Appeal) => void
  approveAppeal: (appeal: Appeal) => void
  rejectAppeal: (appeal: Appeal) => void
}

// ========== Status Styles ==========
const statusStyles: Record<
  AppealStatus,
  { bg: string; border: string; text: string; icon: typeof IconScale }
> = {
  PENDING: {
    bg: 'bg-[oklch(0.75_0.15_85/0.15)]',
    border: 'border-[oklch(0.75_0.15_85/0.4)]',
    text: 'text-[var(--terminal-amber)]',
    icon: IconClock,
  },
  UNDER_REVIEW: {
    bg: 'bg-[oklch(0.7_0.12_200/0.15)]',
    border: 'border-[oklch(0.7_0.12_200/0.4)]',
    text: 'text-[var(--terminal-cyan)]',
    icon: IconEye,
  },
  APPROVED: {
    bg: 'bg-[oklch(0.7_0.15_145/0.15)]',
    border: 'border-[oklch(0.7_0.15_145/0.4)]',
    text: 'text-[var(--terminal-green)]',
    icon: IconCheck,
  },
  REJECTED: {
    bg: 'bg-[oklch(0.6_0.2_25/0.15)]',
    border: 'border-[oklch(0.6_0.2_25/0.4)]',
    text: 'text-[var(--terminal-red)]',
    icon: IconX,
  },
}

function renderStatusBadge(status: AppealStatus, t: (key: string) => string) {
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
      t(`moderation.appealStatus.${status}`),
    ),
  ])
}

function truncateText(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

/**
 * Creates column definitions for the appeals DataTable.
 */
export function createAppealsColumns(
  t: (key: string) => string,
  actions: AppealActions,
): ColumnDef<Appeal>[] {
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
    // Appellant
    {
      accessorKey: 'appellant',
      size: 120,
      minSize: 100,
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.appeals.appellant'),
        ),
      cell: ({ row }) => {
        const appellant = row.original.appellant
        return h('div', { class: 'flex items-center gap-2' }, [
          h(IconUser, { class: 'h-3.5 w-3.5 text-[var(--silver-500)]' }),
          h(
            'span',
            { class: 'text-sm truncate' },
            appellant?.display_name || appellant?.username || t('moderation.unknownReporter'),
          ),
        ])
      },
    },
    // Queue ID
    {
      accessorKey: 'queue_id',
      minSize: 150,
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          'Queue ID',
        ),
      cell: ({ row }) => {
        const queueId = row.original.queue_id
        return h(
          'span',
          {
            class: 'font-data text-xs text-muted-foreground block truncate',
            title: queueId,
          },
          truncateText(queueId, 24),
        )
      },
    },
    // Reason
    {
      accessorKey: 'reason',
      minSize: 200,
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.appeals.reason'),
        ),
      cell: ({ row }) => {
        const reason = row.original.reason
        return h(
          'span',
          { class: 'text-sm text-muted-foreground block truncate', title: reason },
          truncateText(reason, 40),
        )
      },
    },
    // Status
    {
      accessorKey: 'status',
      size: 140,
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.columns.status'),
        ),
      cell: ({ row }) => {
        const status = row.original.status as AppealStatus
        return renderStatusBadge(status, t)
      },
    },
    // Reviewed By
    {
      accessorKey: 'reviewer',
      size: 120,
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('moderation.appeals.reviewedBy'),
        ),
      cell: ({ row }) => {
        const reviewer = row.original.reviewer
        if (!reviewer) {
          return h('span', { class: 'font-data text-xs text-[var(--silver-400)] italic' }, '-')
        }
        return h(
          'span',
          { class: 'text-sm truncate block' },
          reviewer.display_name || reviewer.username,
        )
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
        const appeal = row.original
        const isPending =
          appeal.status === AppealStatus.PENDING || appeal.status === AppealStatus.UNDER_REVIEW

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
                  default: () =>
                    [
                      h(
                        DropdownMenuItem,
                        {
                          onClick: () => actions.viewAppeal(appeal),
                          class: 'font-data text-xs cursor-pointer',
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconEye, { class: 'h-4 w-4 text-[var(--terminal-cyan)]' }),
                              h('span', t('moderation.appeals.reviewAppeal')),
                            ]),
                        },
                      ),
                      isPending
                        ? h(
                            DropdownMenuItem,
                            {
                              onClick: () => actions.approveAppeal(appeal),
                              class: 'font-data text-xs cursor-pointer',
                            },
                            {
                              default: () =>
                                h('div', { class: 'flex items-center gap-2' }, [
                                  h(IconCheck, { class: 'h-4 w-4 text-[var(--terminal-green)]' }),
                                  h(
                                    'span',
                                    { class: 'text-[var(--terminal-green)]' },
                                    t('moderation.appeals.approveAppeal'),
                                  ),
                                ]),
                            },
                          )
                        : null,
                      isPending
                        ? h(
                            DropdownMenuItem,
                            {
                              onClick: () => actions.rejectAppeal(appeal),
                              class: 'font-data text-xs cursor-pointer',
                            },
                            {
                              default: () =>
                                h('div', { class: 'flex items-center gap-2' }, [
                                  h(IconX, { class: 'h-4 w-4 text-[var(--terminal-red)]' }),
                                  h(
                                    'span',
                                    { class: 'text-[var(--terminal-red)]' },
                                    t('moderation.appeals.rejectAppeal'),
                                  ),
                                ]),
                            },
                          )
                        : null,
                    ].filter(Boolean),
                },
              ),
            ],
          },
        )
      },
    },
  ]
}
