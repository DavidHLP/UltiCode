import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconAlertTriangle,
  IconCheck,
  IconClock,
  IconDotsVertical,
  IconEye,
  IconX,
} from '@tabler/icons-vue'

import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Difficulty, type Problem } from '@/api/admin/problems'
import { formatDate } from '@/lib/format/date'

export type FlagStatus = 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'

export interface ModerationActions {
  viewProblem: (id: string) => void
  openDrawer: (problem: Problem) => void
  quickResolve: (id: string) => void
  quickDismiss: (id: string) => void
}

// Status badge styles (terminal-style)
const statusStyles: Record<
  FlagStatus,
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
    icon: IconCheck,
  },
  DISMISSED: {
    bg: 'bg-[oklch(0.6_0.2_25/0.15)]',
    border: 'border-[oklch(0.6_0.2_25/0.4)]',
    text: 'text-[var(--terminal-red)]',
    icon: IconX,
  },
}

// Difficulty badge styles (matching existing ProblemsListView)
const difficultyStyles: Record<Difficulty, { bg: string; border: string; text: string }> = {
  EASY: {
    bg: 'bg-[oklch(0.7_0.15_145/0.15)]',
    border: 'border-[oklch(0.7_0.15_145/0.4)]',
    text: 'text-[var(--terminal-green)]',
  },
  MEDIUM: {
    bg: 'bg-[oklch(0.75_0.15_85/0.15)]',
    border: 'border-[oklch(0.75_0.15_85/0.4)]',
    text: 'text-[var(--terminal-amber)]',
  },
  HARD: {
    bg: 'bg-[oklch(0.6_0.2_25/0.15)]',
    border: 'border-[oklch(0.6_0.2_25/0.4)]',
    text: 'text-[var(--terminal-red)]',
  },
}

// Status i18n key mapping
const statusI18nKeys: Record<FlagStatus, string> = {
  PENDING: 'moderation.statusPending',
  REVIEWED: 'moderation.statusReviewed',
  RESOLVED: 'moderation.statusResolved',
  DISMISSED: 'moderation.statusDismissed',
}

// Terminal-style status badge renderer
function renderStatusBadge(status: FlagStatus, t: (key: string) => string) {
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
      t(statusI18nKeys[status]),
    ),
  ])
}

// Terminal-style difficulty badge renderer (matches role badge style)
function renderDifficultyBadge(difficulty: Difficulty, t: (key: string) => string) {
  const style = difficultyStyles[difficulty]

  return h('div', { class: 'flex items-center gap-2' }, [
    h('span', {
      class: ['w-1.5 h-1.5 rounded-full', style.text.replace('text-', 'bg-')].join(' '),
    }),
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
      t(`common.difficulty.${difficulty.toLowerCase()}`),
    ),
  ])
}

// Truncate text helper
function truncateText(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

/**
 * Creates column definitions for the moderation queue DataTable.
 * @param t - Translation function from useI18n
 * @param actions - Action handlers for moderation operations
 * @returns Array of column definitions for @tanstack/vue-table
 */
export function createColumns(
  t: (key: string) => string,
  actions: ModerationActions,
): ColumnDef<Problem>[] {
  return [
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
    {
      accessorKey: 'title',
      minSize: 180,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('moderation.columns.problem'),
        ),
      cell: ({ row }) => {
        const problem = row.original
        return h('div', { class: 'flex flex-col gap-1 py-1' }, [
          h('span', { class: 'font-medium text-sm truncate' }, problem.title),
          h('span', { class: 'text-muted-foreground text-xs truncate' }, problem.slug),
          renderDifficultyBadge(problem.difficulty, t),
        ])
      },
    },
    {
      accessorKey: 'flag_status',
      size: 120,
      minSize: 100,
      maxSize: 140,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.status'),
        ),
      cell: ({ row }) => {
        const status = (row.getValue('flag_status') as FlagStatus) || 'PENDING'
        return renderStatusBadge(status, t)
      },
    },
    {
      accessorKey: 'flag_reason',
      minSize: 150,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('moderation.flagReason'),
        ),
      cell: ({ row }) => {
        const reason = row.original.flag_reason
        if (!reason) {
          return h('span', { class: 'font-data text-xs text-[var(--silver-400)] italic' }, '-')
        }
        const truncated = truncateText(reason, 30)
        return h(
          'span',
          {
            class: 'text-sm text-muted-foreground block truncate',
            title: reason,
          },
          truncated,
        )
      },
    },
    {
      accessorKey: 'flag_reported_by',
      size: 100,
      minSize: 80,
      maxSize: 120,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.reportedBy'),
        ),
      cell: ({ row }) => {
        const reportedBy = row.original.flag_reported_by
        if (!reportedBy) {
          return h(
            'span',
            { class: 'font-data text-xs text-[var(--silver-400)] italic' },
            t('moderation.unknownReporter'),
          )
        }
        return h('span', { class: 'text-sm text-muted-foreground truncate block' }, reportedBy)
      },
    },
    {
      accessorKey: 'flag_reported_at',
      size: 100,
      minSize: 80,
      maxSize: 120,
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.reportedAt'),
        ),
      cell: ({ row }) => {
        const date = row.original.flag_reported_at
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
          date ? formatDate(date) : '—',
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.actions'),
        ),
      cell: ({ row }) => {
        const problem = row.original
        return createActionsDropdown(t, problem, actions)
      },
    },
  ]
}

function createActionsDropdown(
  t: (key: string) => string,
  problem: Problem,
  actions: ModerationActions,
) {
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
                {
                  variant: 'ghost',
                  size: 'icon',
                  class:
                    'h-8 w-8 p-0 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]',
                },
                {
                  default: () => [
                    h('span', { class: 'sr-only' }, 'Open menu'),
                    h(IconDotsVertical, { class: 'h-4 w-4 text-[var(--silver-400)]' }),
                  ],
                },
              ),
          },
        ),
        h(
          DropdownMenuContent,
          {
            align: 'end',
            class: 'border-[var(--silver-200)] dark:border-[var(--silver-700)]',
          },
          {
            default: () => [
              h(
                DropdownMenuItem,
                {
                  onClick: () => actions.openDrawer(problem),
                  class: 'font-data text-xs cursor-pointer',
                },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconEye, { class: 'h-4 w-4 text-[var(--terminal-cyan)]' }),
                      h('span', t('common.view')),
                    ]),
                },
              ),
              h(DropdownMenuSeparator, {
                class: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
              }),
              h(
                DropdownMenuItem,
                {
                  onClick: () => actions.quickResolve(problem.id),
                  class: 'font-data text-xs cursor-pointer',
                },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconCheck, { class: 'h-4 w-4 text-[var(--terminal-green)]' }),
                      h(
                        'span',
                        { class: 'text-[var(--terminal-green)]' },
                        t('moderation.quickResolve'),
                      ),
                    ]),
                },
              ),
              h(
                DropdownMenuItem,
                {
                  onClick: () => actions.quickDismiss(problem.id),
                  class: 'font-data text-xs cursor-pointer',
                },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconX, { class: 'h-4 w-4 text-[var(--terminal-red)]' }),
                      h(
                        'span',
                        { class: 'text-[var(--terminal-red)]' },
                        t('moderation.quickDismiss'),
                      ),
                    ]),
                },
              ),
            ],
          },
        ),
      ],
    },
  )
}
