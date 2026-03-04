import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCheck,
  IconDotsVertical,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconTrash,
  IconUser,
  IconCode,
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
import type { Solution } from '@/api/admin/solutions'
import { formatDate } from '@/lib/format/date'

export interface SolutionActions {
  viewSolution: (id: string) => void
  openFlagDialog: (solution: Solution) => void
  unflagSolution: (id: string) => void
  confirmDelete: (solution: Solution) => void
}

// Terminal-style status badge renderer
function renderStatusBadge(solution: Solution, t: (key: string) => string) {
  if (solution.is_deleted) {
    return h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border rounded-sm',
          'bg-[oklch(0.6_0.2_25/0.15)]',
          'border-[oklch(0.6_0.2_25/0.4)]',
          'text-[var(--terminal-red)]',
        ].join(' '),
      },
      [h(IconTrash, { class: 'mr-1 h-3 w-3 inline' }), t('solutions.status.deleted')],
    )
  }

  if (solution.is_flagged) {
    return h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border rounded-sm',
          'bg-[oklch(0.75_0.15_85/0.15)]',
          'border-[oklch(0.75_0.15_85/0.4)]',
          'text-[var(--terminal-amber)]',
          'animate-pulse-subtle',
        ].join(' '),
      },
      [h(IconFlag, { class: 'mr-1 h-3 w-3 inline' }), t('solutions.status.flagged')],
    )
  }

  if (solution.is_published) {
    return h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border rounded-sm',
          'bg-[oklch(0.7_0.15_145/0.15)]',
          'border-[oklch(0.7_0.15_145/0.4)]',
          'text-[var(--terminal-green)]',
        ].join(' '),
      },
      [h(IconCheck, { class: 'mr-1 h-3 w-3 inline' }), t('solutions.status.published')],
    )
  }

  return h(
    'span',
    {
      class: [
        'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
        'px-2 py-0.5 border rounded-sm',
        'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
        'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
        'text-[var(--silver-500)]',
      ].join(' '),
    },
    [h(IconEyeOff, { class: 'mr-1 h-3 w-3 inline' }), t('solutions.status.unpublished')],
  )
}

export function createColumns(
  t: (key: string) => string,
  actions: SolutionActions,
  canUpdateSolution: () => boolean,
  canDeleteSolution: () => boolean,
): ColumnDef<Solution>[] {
  return [
    {
      id: 'select',
      header: ({ table }) =>
        h(Checkbox, {
          modelValue:
            table.getIsAllPageRowsSelected() ||
            (table.getIsSomePageRowsSelected() && 'indeterminate'),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
            table.toggleAllPageRowsSelected(!!value),
          'aria-label': t('table.selectAll'),
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': t('common.select'),
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
      accessorKey: 'id',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('solutions.columns.id'),
        ),
      cell: ({ row }) => {
        const id = row.getValue('id') as string
        return h('span', { class: 'font-data text-xs text-[var(--silver-400)]' }, id.slice(0, 8))
      },
    },
    {
      accessorKey: 'title',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('solutions.columns.solution'),
        ),
      cell: ({ row }) => {
        const solution = row.original
        return h('div', { class: 'flex flex-col gap-0.5' }, [
          h('span', { class: 'font-medium text-sm text-[var(--foreground)]' }, solution.title),
          h('div', { class: 'flex items-center gap-1 text-xs text-[var(--silver-400)]' }, [
            h(IconCode, { class: 'h-3 w-3' }),
            h('span', {}, solution.problem?.title || t('common.noData')),
          ]),
        ])
      },
    },
    {
      accessorKey: 'author',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('solutions.columns.author'),
        ),
      cell: ({ row }) => {
        const author = row.original.author
        return h('div', { class: 'flex items-center gap-2' }, [
          h(IconUser, { class: 'h-3.5 w-3.5 text-[var(--terminal-cyan)]' }),
          h(
            'span',
            { class: 'text-sm text-[var(--foreground)]' },
            author?.username || t('common.noData'),
          ),
        ])
      },
    },
    {
      accessorKey: 'is_flagged',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('solutions.columns.status'),
        ),
      cell: ({ row }) => {
        const solution = row.original
        return renderStatusBadge(solution, t)
      },
    },
    {
      accessorKey: 'views',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('solutions.columns.views'),
        ),
      cell: ({ row }) => {
        const views = row.getValue('views') as number
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--terminal-cyan)] tabular-nums' },
          views.toLocaleString(),
        )
      },
    },
    {
      accessorKey: 'created_at',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('solutions.columns.created'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('created_at') as string
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
          formatDate(date),
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('solutions.columns.actions'),
        ),
      cell: ({ row }) => {
        const solution = row.original
        return createActionsDropdown(t, solution, actions, canUpdateSolution, canDeleteSolution)
      },
    },
  ]
}

function createActionsDropdown(
  t: (key: string) => string,
  solution: Solution,
  actions: SolutionActions,
  canUpdateSolution: () => boolean,
  canDeleteSolution: () => boolean,
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
                    h('span', { class: 'sr-only' }, t('common.open')),
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
                  onClick: () => actions.viewSolution(solution.id),
                  class: 'font-data text-xs cursor-pointer',
                },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconFile, { class: 'h-4 w-4 text-[var(--terminal-cyan)]' }),
                      t('solutions.actions.viewDetails'),
                    ]),
                },
              ),
              canUpdateSolution()
                ? solution.is_flagged
                  ? h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.unflagSolution(solution.id),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconCheck, { class: 'h-4 w-4 text-[var(--terminal-green)]' }),
                            h(
                              'span',
                              { class: 'text-[var(--terminal-green)]' },
                              t('solutions.actions.unflag'),
                            ),
                          ]),
                      },
                    )
                  : h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.openFlagDialog(solution),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconFlag, { class: 'h-4 w-4 text-[var(--terminal-amber)]' }),
                            h(
                              'span',
                              { class: 'text-[var(--terminal-amber)]' },
                              t('solutions.actions.flag'),
                            ),
                          ]),
                      },
                    )
                : null,
              h(DropdownMenuSeparator, {
                class: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
              }),
              canDeleteSolution()
                ? h(
                    DropdownMenuItem,
                    {
                      onClick: () => actions.confirmDelete(solution),
                      class: 'font-data text-xs cursor-pointer',
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconTrash, { class: 'h-4 w-4 text-[var(--terminal-red)]' }),
                          h(
                            'span',
                            { class: 'text-[var(--terminal-red)]' },
                            t('solutions.actions.delete'),
                          ),
                        ]),
                    },
                  )
                : null,
            ],
          },
        ),
      ],
    },
  )
}
